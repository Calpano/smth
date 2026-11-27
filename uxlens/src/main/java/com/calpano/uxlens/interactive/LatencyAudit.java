package com.calpano.uxlens.interactive;

import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Severity;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * How long the page takes to answer a click.
 *
 * <p>The one thing in this project that needs a stopwatch rather than a screenshot. Doherty
 * and Thadani measured at IBM in 1982 that productivity rose sharply once the machine
 * answered inside about 400ms -- fast enough that neither the person nor the computer is
 * waiting on the other -- and about a second is where a delay stops being a delay and
 * becomes an interruption, because past it the train of thought has to be rebuilt.
 *
 * <p>The clock starts at the browser's own {@code pointerdown} event rather than when the
 * driver was told to click, because the driver's own overhead -- moving the mouse, waiting
 * for the element to be stable -- is not latency the page caused. It stops at the first DOM
 * mutation, which is the earliest moment the page could possibly have shown anything.
 *
 * <p>Three things make this harder than it looks, and all three are handled by refusing to
 * answer rather than by guessing:
 *
 * <ul>
 *   <li>A page that mutates constantly -- a clock, a carousel, a polling widget -- gives a
 *       spuriously fast reading, because the next unrelated mutation arrives within
 *       milliseconds of any click. Baseline mutation activity is measured first, and a
 *       restless page is reported as unmeasurable instead of as fast.</li>
 *   <li>Clicking things on a real page is destructive. Only controls that look safe are
 *       touched, and the page is reloaded whenever a click navigated away.</li>
 *   <li>A control that responds with a pure CSS {@code :active} style makes no DOM mutation
 *       at all. That is instant by construction, so nothing is reported; what is reported
 *       is silence, which is a different finding.</li>
 * </ul>
 */
public final class LatencyAudit {

    /** In-page controls to click. Each costs a round trip and a possible reload. */
    private static final int MAX_CONTROLS = 12;

    /** Links to follow. Each costs two navigations, so fewer. */
    private static final int MAX_LINKS = 4;

    /** How long to wait for the page to do anything at all. */
    private static final int WAIT_MS = 3000;

    /** How long to watch an untouched page to see whether it sits still. */
    private static final int BASELINE_MS = 600;

    /**
     * Mutations per second on an untouched page above which nothing can be measured.
     *
     * <p>Two is generous. A page with a ticking clock mutates once a second and is still
     * measurable; one repainting a chart at thirty frames a second is not.
     */
    private static final double RESTLESS_PER_SECOND = 4;

    /** Rule id for a page that takes longer than Doherty's threshold to answer. */
    public static final String SLOW_RULE_ID = "response-not-immediate";

    /** Rule id for a control that produces no response at all. */
    public static final String SILENT_RULE_ID = "click-does-nothing";

    /** Rule id for a page too restless to time. */
    public static final String UNMEASURABLE_RULE_ID = "latency-unmeasurable";

    public static java.util.Set<String> ruleIds() {
        return java.util.Set.of(SLOW_RULE_ID, SILENT_RULE_ID, UNMEASURABLE_RULE_ID);
    }

    private LatencyAudit() { }

    /**
     * What was actually measured, reported alongside the findings and never omitted.
     *
     * <p>A pass with no evidence behind it is the failure this project keeps finding in
     * itself: a rule that never ran and a rule with nothing to report look identical from
     * outside. "Every control answered inside 400ms" is worth nothing if no control was
     * ever clicked, so the count of clicks, measurements and refusals goes out with the
     * verdict.
     *
     * @param mutationsPerSecond how much the untouched page changed by itself
     */
    public record Summary(int actionableOnPage, int safeToClick, int clicked, int measured,
                          int silent, int navigated, int unreachable,
                          double mutationsPerSecond, List<Double> millis) {

        public boolean isConclusive() { return measured > 0; }

        public double slowest() {
            return millis.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        }

        public double median() {
            if (millis.isEmpty()) return Double.NaN;
            List<Double> sorted = millis.stream().sorted().toList();
            return sorted.get(sorted.size() / 2);
        }

        /** Why nothing could be measured, or empty when something was. */
        public Optional<String> whyInconclusive() {
            if (isConclusive()) return Optional.empty();
            if (mutationsPerSecond > RESTLESS_PER_SECOND) {
                return Optional.of(String.format(Locale.ROOT, "the page never stops changing by "
                        + "itself (%.1f mutations a second), so a click cannot be told from "
                        + "what it was doing anyway", mutationsPerSecond));
            }
            if (actionableOnPage == 0) {
                return Optional.of("the page has no actionable controls");
            }
            if (safeToClick == 0) {
                return Optional.of(String.format("all %d actionable control(s) were left alone: "
                        + "destructive wording, a form submit, or a link that navigates away",
                        actionableOnPage));
            }
            if (clicked == 0) {
                return Optional.of(String.format("none of the %d safe control(s) could be "
                        + "clicked -- they moved, detached or refused", safeToClick));
            }
            if (silent == clicked) {
                return Optional.of(String.format("all %d click(s) changed nothing in the "
                        + "document, so there was no response to time", clicked));
            }
            return Optional.of("no click produced a timeable response");
        }
    }

    /** The findings, and what was done to earn them. */
    public record Result(List<Finding> findings, Summary summary) { }

    /**
     * What one click produced.
     *
     * @param millis time from the click to the first thing the reader could see, or null
     * @param kind   how that was measured: the first DOM mutation for a control that stays
     *               on the page, or the new document's first contentful paint for a link
     *               that navigates. Both answer "when could they see anything", which is
     *               the question Doherty's figure is about, but they are not the same
     *               measurement and the report should not pretend otherwise.
     */
    public record Response(Elem control, Double millis, Kind kind, boolean silent) {

        public boolean measured() { return millis != null; }

        public boolean navigated() { return kind == Kind.NAVIGATION; }
    }

    /** Which clock was used. */
    public enum Kind {
        /** Click to first change in the document, on a page that stayed put. */
        MUTATION,
        /** Click to the next document's first contentful paint. */
        NAVIGATION
    }

    /** Opens the URL, clicks what is safe to click, and times each answer. */
    public static Result audit(PageProbe probe, String url, Viewport viewport, Config config) {
        return audit(probe, url, viewport, config, true);
    }

    /**
     * @param followLinks whether to follow the site's own links and time the next page. Two
     *                    navigations each, so it is the slow half; without it a content site
     *                    where every control is a link yields no measurement at all.
     */
    public static Result audit(PageProbe probe, String url, Viewport viewport, Config config,
                               boolean followLinks) {
        try (BrowserContext ctx = probe.newContext(viewport)) {
            Page page = ctx.newPage();
            page.navigate(url);
            PageSnapshot snapshot = probe.measureCurrentState(page, viewport).snapshot();

            int actionable = (int) snapshot.elements.stream()
                    .filter(e -> e.actionable && !e.disabled).count();
            double restlessness = baselineMutationsPerSecond(page);

            if (restlessness > RESTLESS_PER_SECOND) {
                Summary summary = new Summary(actionable, 0, 0, 0, 0, 0, 0, restlessness,
                        List.of());
                return new Result(List.of(new Finding(UNMEASURABLE_RULE_ID, Severity.INFO,
                        String.format(Locale.ROOT,
                                "This page mutates %.1f times a second while nobody is touching "
                                        + "it, so the time from a click to the page's answer "
                                        + "cannot be separated from what the page was doing "
                                        + "anyway.", restlessness))
                        .with("mutationsPerSecond", Math.round(restlessness * 10) / 10.0)
                        .suggest("Time this page with the browser's own performance tools, or "
                                + "pause whatever is animating and try again. A measurement "
                                + "that cannot tell your click from a carousel is worse than "
                                + "none.")), summary);
            }

            List<Elem> safe = safeToClick(snapshot);
            List<Elem> links = followLinks ? safeToFollow(snapshot) : List.of();
            List<Finding> out = new ArrayList<>();
            List<Double> timings = new ArrayList<>();
            int clicked = 0, silent = 0, navigated = 0, unreachable = 0;

            for (Elem control : safe) {
                Optional<Response> response = timeOne(page, control, url);
                if (response.isEmpty()) {
                    unreachable++;
                    continue;
                }
                clicked++;
                Response r = response.get();
                if (r.silent()) silent++;
                if (r.measured()) timings.add(r.millis());
                judge(r, config).ifPresent(out::add);
            }

            for (Elem link : links) {
                Optional<Response> response = timeNavigation(page, link, url);
                if (response.isEmpty()) {
                    unreachable++;
                    continue;
                }
                clicked++;
                navigated++;
                Response r = response.get();
                if (r.measured()) timings.add(r.millis());
                judge(r, config).ifPresent(out::add);
            }

            return new Result(out, new Summary(actionable, safe.size() + links.size(), clicked,
                    timings.size(), silent, navigated, unreachable, restlessness,
                    List.copyOf(timings)));
        }
    }

    /** The findings alone, for a caller that does not want the accounting. */
    public static List<Finding> run(PageProbe probe, String url, Viewport viewport, Config config) {
        return audit(probe, url, viewport, config).findings();
    }

    /**
     * Controls that can be clicked without doing something a person would regret.
     *
     * <p>Conservative on purpose. This runs against real sites, and the difference between
     * a tab and a "delete account" button is a word. Anything that submits, downloads,
     * leaves the origin, or carries destructive vocabulary is left alone -- which means the
     * audit covers less of the page than it could, and that is the right trade.
     */
    static List<Elem> safeToClick(PageSnapshot snapshot) {
        return candidates(snapshot)
                .filter(e -> !e.navigatesAway())
                .limit(MAX_CONTROLS)
                .toList();
    }

    /**
     * Links worth following: the site's own pages.
     *
     * <p>On a content site this is the whole interaction. xam.de has thirty-six actionable
     * controls and every one of them is a link, so an audit that refuses to navigate
     * reports a confident pass having clicked nothing -- which is how this was first
     * written. Following a link is a different measurement, not a worse one: the clock runs
     * from the click to the next document's first contentful paint, which is when the
     * reader can see that anything happened.
     */
    static List<Elem> safeToFollow(PageSnapshot snapshot) {
        return candidates(snapshot)
                .filter(e -> "same-site".equals(e.linkTarget) || "root".equals(e.linkTarget))
                .limit(MAX_LINKS)
                .toList();
    }

    private static java.util.stream.Stream<Elem> candidates(PageSnapshot snapshot) {
        return snapshot.elements.stream()
                .filter(e -> e.actionable && !e.disabled && e.w >= 8 && e.h >= 8)
                .filter(e -> e.rect().intersects(snapshot.viewportRect()))
                .filter(e -> !isDestructive(e))
                .filter(e -> !isSubmit(snapshot, e));
    }

    /** Words that mean the click cannot be taken back. */
    private static final List<String> DESTRUCTIVE = List.of("delete", "remove", "destroy",
            "deactivate", "cancel subscription", "close account", "unsubscribe", "revoke",
            "reset", "clear", "discard", "logout", "log out", "sign out", "abmelden",
            "löschen", "loschen", "entfernen", "buy", "pay", "order", "checkout", "confirm",
            "submit", "send", "kaufen", "bezahlen", "bestellen", "senden");

    private static boolean isDestructive(Elem e) {
        String words = ((e.visibleText == null ? "" : e.visibleText) + " "
                + (e.accessibleName == null ? "" : e.accessibleName) + " "
                + (e.ariaLabel == null ? "" : e.ariaLabel))
                .toLowerCase(Locale.ROOT);
        return DESTRUCTIVE.stream().anyMatch(words::contains);
    }

    /**
     * Whether clicking this would submit or reset a form.
     *
     * <p>Only inside a form. A bare {@code <button>} reports {@code type="submit"} because
     * that is the DOM default, and rejecting on the type alone left every plain button on
     * every page unclicked -- which silently reduced the audit to links and made its first
     * three tests fail for the right reason.
     */
    private static boolean isSubmit(PageSnapshot snapshot, Elem e) {
        boolean submits = "submit".equals(e.type) || "reset".equals(e.type)
                || "image".equals(e.type)
                // The probe records the attribute, not the resolved property, and a
                // <button> with no type attribute submits: that is the HTML default.
                || ("button".equals(e.tag) && e.type == null);
        if (!submits) return false;
        return snapshot.ancestorsOf(e).stream().anyMatch(p -> "form".equals(p.tag));
    }

    /** Clicks one control and reads the clock the page kept for itself. */
    private static Optional<Response> timeOne(Page page, Elem control, String url) {
        try {
            arm(page);
            page.locator(control.selector).first().click(
                    new com.microsoft.playwright.Locator.ClickOptions().setTimeout(2000));

            // Wait for the answer, not for a fixed moment. An earlier version waited 300ms
            // and then read the clock, so every response slower than that was recorded as
            // "changed nothing" -- the audit could not observe the delays it exists to find.
            try {
                page.waitForFunction(
                        "() => window.__uxlensLatency && window.__uxlensLatency.first !== null",
                        null, new Page.WaitForFunctionOptions().setTimeout(WAIT_MS));
            } catch (com.microsoft.playwright.TimeoutError timeout) {
                // Nothing happened within the window; the state below says so.
            }

            Object raw = page.evaluate("() => window.__uxlensLatency || null");
            boolean navigated = !page.url().equals(url);
            if (navigated) page.navigate(url);
            if (!(raw instanceof Map<?, ?> state)) {
                // The page navigated and took the clock with it; the navigation path below
                // is the one that can time that case.
                return Optional.empty();
            }

            Double started = asDouble(state.get("started"));
            Double first = asDouble(state.get("first"));
            if (started == null) return Optional.empty();       // the click never landed
            if (first == null) {
                return Optional.of(new Response(control, null, Kind.MUTATION, true));
            }
            return Optional.of(new Response(control, Math.max(0, first - started),
                    Kind.MUTATION, false));
        } catch (RuntimeException e) {
            // A control that moved, detached or refused to be clicked is not a finding.
            return Optional.empty();
        }
    }

    /**
     * Follows one link and reads the next document's first contentful paint.
     *
     * <p>No stopwatch of ours is needed: the Paint Timing API records, on the new document's
     * own clock, when the browser first painted content, measured from that navigation's
     * start. That is the number -- the moment the reader could see that their click had
     * done something.
     */
    private static Optional<Response> timeNavigation(Page page, Elem link, String url) {
        try {
            page.locator(link.selector).first().click(
                    new com.microsoft.playwright.Locator.ClickOptions().setTimeout(3000));
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);
            if (page.url().equals(url)) {
                page.navigate(url);
                return Optional.empty();            // it did not actually go anywhere
            }
            Object fcp = page.evaluate("""
                    () => {
                      const paint = performance.getEntriesByName('first-contentful-paint')[0];
                      if (paint) return paint.startTime;
                      const nav = performance.getEntriesByType('navigation')[0];
                      return nav ? nav.domContentLoadedEventEnd : null;
                    }
                    """);
            page.navigate(url);
            Double ms = asDouble(fcp);
            if (ms == null || ms <= 0) return Optional.empty();
            return Optional.of(new Response(link, ms, Kind.NAVIGATION, false));
        } catch (RuntimeException e) {
            try {
                page.navigate(url);
            } catch (RuntimeException ignored) {
                // Nothing more to do; the next control will fail the same way and be counted.
            }
            return Optional.empty();
        }
    }

    /**
     * Installs the page's own stopwatch: the pointerdown timestamp, and the first mutation
     * after it.
     *
     * <p>Both readings come from inside the page, on one clock, so nothing the driver does
     * is counted as latency the page caused.
     */
    private static void arm(Page page) {
        page.evaluate("""
                () => {
                  if (window.__uxlensObserver) window.__uxlensObserver.disconnect();
                  const state = { started: null, first: null };
                  window.__uxlensLatency = state;
                  window.addEventListener('pointerdown', e => {
                    if (state.started === null) state.started = e.timeStamp;
                  }, true);
                  const observer = new MutationObserver(() => {
                    if (state.started !== null && state.first === null) {
                      state.first = performance.now();
                    }
                  });
                  observer.observe(document.documentElement, {
                    subtree: true, childList: true, attributes: true, characterData: true
                  });
                  window.__uxlensObserver = observer;
                }
                """);
    }

    /** How much an untouched page changes by itself. */
    private static double baselineMutationsPerSecond(Page page) {
        page.evaluate("""
                () => {
                  window.__uxlensIdle = 0;
                  if (window.__uxlensIdleObserver) window.__uxlensIdleObserver.disconnect();
                  const observer = new MutationObserver(records => {
                    window.__uxlensIdle += records.length;
                  });
                  observer.observe(document.documentElement, {
                    subtree: true, childList: true, attributes: true, characterData: true
                  });
                  window.__uxlensIdleObserver = observer;
                }
                """);
        page.waitForTimeout(BASELINE_MS);
        Object count = page.evaluate(
                "() => { window.__uxlensIdleObserver.disconnect(); return window.__uxlensIdle; }");
        double mutations = count instanceof Number n ? n.doubleValue() : 0;
        return mutations / (BASELINE_MS / 1000.0);
    }

    private static Double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    static Optional<Finding> judge(Response response, Config config) {
        Elem e = response.control();
        if (response.silent() && !response.navigated()) {
            return Optional.of(new Finding(SILENT_RULE_ID, Severity.LOW, String.format(
                    "%s changed nothing in the page within %dms of being clicked.",
                    e.label(), WAIT_MS))
                    .about(e)
                    .suggest("Either the control does nothing, or its whole response is a CSS "
                            + "state with no change to the document -- which is instant, and "
                            + "fine. Check which: a control that silently does nothing is a "
                            + "dead end, and the reader has no way to tell the two apart "
                            + "either."));
        }
        if (!response.measured()) return Optional.empty();

        double ms = response.millis();
        if (ms <= config.dohertyThresholdMs) return Optional.empty();
        boolean breaksFlow = ms >= config.flowBreakMs;

        return Optional.of(new Finding(SLOW_RULE_ID,
                breaksFlow ? Severity.MEDIUM : Severity.LOW, String.format(Locale.ROOT,
                "%s took %.0fms to %s (%s).", e.label(), ms,
                response.navigated()
                        ? "paint anything on the page it led to"
                        : "produce any visible response",
                breaksFlow
                        ? "past the second that holds a train of thought"
                        : String.format(Locale.ROOT, "the Doherty threshold is %.0fms",
                                config.dohertyThresholdMs)))
                .about(e)
                .with("responseMs", Math.round(ms))
                .with("dohertyThresholdMs", config.dohertyThresholdMs)
                .with("measuredAs", response.kind().name().toLowerCase(Locale.ROOT))
                .suggest(response.navigated()
                        ? "This is the next page's first contentful paint, so it is the whole "
                          + "wait before anything appears. Cut it at the source -- fewer "
                          + "blocking requests, server-rendered first paint -- or make the "
                          + "transition itself show progress so the click is acknowledged "
                          + "while the page is still coming."
                        : "Show something inside 400ms even if the answer is not ready: a "
                          + "pressed state, a skeleton, a spinner. The measurement is to the "
                          + "first change in the document, so this is the floor rather than "
                          + "the whole wait."));
    }
}
