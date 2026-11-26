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
import java.util.List;

/**
 * Does the keyboard focus indicator actually show up?
 *
 * <p>This cannot be answered from the DOM: {@code :focus-visible} styles may be overridden,
 * painted outside the element's box, or removed by a reset that nobody noticed. So each
 * control is focused for real and the pixels around it are compared before and after. If
 * nothing changed, there is no focus indicator -- whatever the stylesheet claims.
 *
 * <p>Unlike the static rules this drives the page, so it runs on its own rather than as
 * part of {@code RuleSet}.
 */
public final class FocusAudit {

    /** Controls to check. Tabbing through hundreds of links is slow and tells you nothing new. */
    private static final int MAX_CONTROLS = 40;
    /** Margin around the control to capture; focus rings are painted outside the box. */
    private static final double HALO_PX = 8;
    /** Pixel-change share below which nothing visible happened. */
    private static final double NO_CHANGE = 0.002;
    /** Pixel-change share below which the change is too subtle to notice. */
    private static final double FAINT = 0.012;

    private FocusAudit() { }

    /** Opens the URL, tabs through its controls and reports the ones with no visible focus state. */
    public static List<Finding> run(PageProbe probe, String url, Viewport viewport) {
        try (BrowserContext ctx = probe.newContext(viewport)) {
            Page page = ctx.newPage();
            page.navigate(url);
            PageProbe.Capture capture = probe.measureCurrentState(page, viewport);
            return check(page, capture.snapshot(), viewport);
        }
    }

    /** Checks a page you have already driven into the state you care about. */
    public static List<Finding> check(Page page, PageSnapshot snapshot, Viewport viewport) {
        List<Finding> out = new ArrayList<>();
        List<Elem> controls = snapshot.interactiveElements().stream()
                .filter(e -> e.focusable && !e.disabled && e.w >= 4 && e.h >= 4)
                .filter(e -> e.rect().right() <= snapshot.scrollWidth && e.rect().bottom() <= snapshot.scrollHeight)
                .limit(MAX_CONTROLS).toList();

        for (Elem e : controls) {
            try {
                blurEverything(page);
                BufferedImage before = shoot(page, e.rect());
                if (before == null) continue;

                page.locator(e.selector).first().focus();
                page.waitForTimeout(60);
                BufferedImage after = shoot(page, e.rect());
                if (after == null || after.getWidth() != before.getWidth()
                        || after.getHeight() != before.getHeight()) continue;

                VisualDiff.Result diff = VisualDiff.compare(before, after, 0.04);
                if (diff.changedPixelRatio() >= FAINT) continue;

                boolean none = diff.changedPixelRatio() < NO_CHANGE;
                out.add(new Finding("focus-not-visible", none ? Severity.HIGH : Severity.MEDIUM,
                        String.format("%s shows %s when focused by keyboard (%.3f%% of the pixels "
                                        + "around it change).",
                                e.label(),
                                none ? "no visible change at all" : "only a barely visible change",
                                diff.changedPixelRatio() * 100))
                        .about(e)
                        .with("changedPixelRatio", Math.round(diff.changedPixelRatio() * 100000) / 100000.0)
                        .with("ssim", Math.round(diff.ssim() * 10000) / 10000.0)
                        .suggest("Add a :focus-visible style with a ring of at least 2px at 3:1 "
                                + "contrast against both the control and the page. If a CSS reset "
                                + "removed the default outline, restore it rather than hiding it."));
            } catch (RuntimeException ex) {
                // A control that moved, detached or could not be focused is not a finding.
            }
        }
        return out;
    }

    private static void blurEverything(Page page) {
        page.evaluate("() => { if (document.activeElement && document.activeElement.blur) "
                + "document.activeElement.blur(); }");
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
