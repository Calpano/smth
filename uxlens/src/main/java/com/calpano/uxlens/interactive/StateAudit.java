package com.calpano.uxlens.interactive;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.perception.VisualDiff;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Clip;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Does the control react when you reach for it?
 *
 * <p>No amount of stylesheet analysis answers this. A focus ring can be declared and then
 * painted outside a clipping ancestor; a hover rule can be overridden three stylesheets
 * later, or defeated by specificity that nobody noticed. So each control is actually
 * focused and actually hovered, and the pixels around it are compared before and after.
 *
 * <p>Unlike the static rules this drives the page, so it runs on its own rather than as
 * part of {@code RuleSet}.
 */
public final class StateAudit {

    /** Controls to check. Driving hundreds of them is slow and tells you nothing new. */
    private static final int MAX_CONTROLS = 40;
    /** Margin around the control to capture; rings and shadows are painted outside the box. */
    private static final double HALO_PX = 8;
    /** Pixel-change share below which nothing visible happened. */
    private static final double NO_CHANGE = 0.002;
    /** Pixel-change share below which the change is too subtle to notice. */
    private static final double FAINT = 0.012;

    private StateAudit() { }

    /** The states a control can be reached in. */
    public enum State {
        /** Keyboard focus. Its absence locks out everyone not using a mouse. */
        FOCUS,
        /** Pointer hover. Its absence makes a control feel inert on a desktop. */
        HOVER
    }

    /** Opens the URL and checks every focusable control in the given states. */
    public static List<Finding> run(PageProbe probe, String url, Viewport viewport, Set<State> states) {
        try (BrowserContext ctx = probe.newContext(viewport)) {
            Page page = ctx.newPage();
            page.navigate(url);
            PageProbe.Capture capture = probe.measureCurrentState(page, viewport);
            return check(page, capture.snapshot(), viewport, states);
        }
    }

    /** Both states, on a freshly opened URL. */
    public static List<Finding> run(PageProbe probe, String url, Viewport viewport) {
        return run(probe, url, viewport, EnumSet.allOf(State.class));
    }

    /** Checks a page you have already driven into the state you care about. */
    public static List<Finding> check(Page page, PageSnapshot snapshot, Viewport viewport,
                                      Set<State> states) {
        List<Finding> out = new ArrayList<>();
        for (Elem e : controls(snapshot)) {
            if (states.contains(State.FOCUS)) {
                checkState(page, e, State.FOCUS).ifPresent(out::add);
            }
            // Hover is a pointer affordance; a touch viewport has no pointer to give it.
            if (states.contains(State.HOVER) && !viewport.mobile()) {
                checkState(page, e, State.HOVER).ifPresent(out::add);
            }
        }
        return out;
    }

    private static List<Elem> controls(PageSnapshot snapshot) {
        return snapshot.interactiveElements().stream()
                .filter(e -> e.actionable && !e.disabled && e.w >= 4 && e.h >= 4)
                .filter(e -> e.rect().right() <= snapshot.scrollWidth
                        && e.rect().bottom() <= snapshot.scrollHeight)
                .limit(MAX_CONTROLS).toList();
    }

    private static java.util.Optional<Finding> checkState(Page page, Elem e, State state) {
        try {
            rest(page);
            BufferedImage before = shoot(page, e.rect());
            if (before == null) return java.util.Optional.empty();

            switch (state) {
                case FOCUS -> page.locator(e.selector).first().focus();
                case HOVER -> page.locator(e.selector).first().hover();
            }
            page.waitForTimeout(80);

            BufferedImage after = shoot(page, e.rect());
            if (after == null || after.getWidth() != before.getWidth()
                    || after.getHeight() != before.getHeight()) return java.util.Optional.empty();

            VisualDiff.Result diff = VisualDiff.compare(before, after, 0.04);
            if (diff.changedPixelRatio() >= FAINT) return java.util.Optional.empty();

            boolean none = diff.changedPixelRatio() < NO_CHANGE;
            return java.util.Optional.of(finding(e, state, diff, none));
        } catch (RuntimeException ex) {
            // A control that moved, detached or could not be reached is not a finding.
            return java.util.Optional.empty();
        }
    }

    private static Finding finding(Elem e, State state, VisualDiff.Result diff, boolean none) {
        boolean focus = state == State.FOCUS;
        String id = focus ? "focus-not-visible" : "hover-not-visible";
        Severity severity = none
                ? (focus ? Severity.HIGH : Severity.MEDIUM)
                : (focus ? Severity.MEDIUM : Severity.LOW);

        return new Finding(id, severity, String.format(
                "%s shows %s when %s (%.3f%% of the pixels around it change).",
                e.label(),
                none ? "no visible change at all" : "only a barely visible change",
                focus ? "focused by keyboard" : "hovered",
                diff.changedPixelRatio() * 100))
                .about(e)
                .with("state", state.name().toLowerCase(java.util.Locale.ROOT))
                .with("changedPixelRatio", Math.round(diff.changedPixelRatio() * 100000) / 100000.0)
                .with("ssim", Math.round(diff.ssim() * 10000) / 10000.0)
                .suggest(focus
                        ? "Add a :focus-visible style with a ring of at least 2px at 3:1 contrast "
                          + "against both the control and the page. If a CSS reset removed the "
                          + "default outline, restore it rather than hiding it."
                        : "Give the control a :hover style -- a background, border or underline "
                          + "change. Hover is how a pointer user confirms a target before "
                          + "committing to it.");
    }

    /** Returns the page to its resting state: nothing focused, pointer out of the way. */
    private static void rest(Page page) {
        page.evaluate("() => { if (document.activeElement && document.activeElement.blur) "
                + "document.activeElement.blur(); }");
        page.mouse().move(0, 0);
        page.waitForTimeout(40);
    }

    /** Screenshots the control plus its halo, in document coordinates. */
    private static BufferedImage shoot(Page page, Rect box) {
        Rect clip = box.inflate(HALO_PX);
        double x = Math.max(0, clip.x()), y = Math.max(0, clip.y());
        if (clip.w() < 1 || clip.h() < 1) return null;
        byte[] png = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true)
                .setClip(new Clip(x, y, clip.w(), clip.h())));
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            return null;
        }
    }
}
