package com.calpano.uxlens.corpus;

import com.calpano.uxlens.Fixtures;
import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether the rules and measurements are alive, as opposed to correct.
 *
 * <p>These are the tests for the thing that catches dead code, so they have to be alive
 * themselves. Each one would have caught a fault this project actually shipped: pixel
 * rules that were skipped on every capture and read as restraint, and two captures that
 * were rate-limit error pages counted for months as the right-to-left and CJK coverage.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Census: did it run, and did anything come out")
class CensusTest {

    private Path seed(PageProbe probe, Path dir, boolean withImages) {
        CorpusStore.writeSites(dir, List.of(
                new CorpusStore.Site("clean", Fixtures.clean(), List.of("desktop"), null, false,
                        withImages, withImages, true, "content", "the quiet one"),
                new CorpusStore.Site("broken", Fixtures.broken(), List.of("desktop"), null, false,
                        withImages, withImages, true, "content", "the loud one")));
        CorpusStore.capture(probe, dir, withImages);
        return dir;
    }

    // ------------------------------------------------- run() and explain() agree

    @Test
    void every_finding_run_reports_came_from_a_rule_that_explain_says_fired(PageProbe probe) {
        Analysis analysis = Analysis.of(probe.capture(Fixtures.broken(), Viewport.DESKTOP),
                Config.defaults());
        RuleSet rules = RuleSet.standard();

        Set<String> fromRun = rules.run(analysis).stream().map(f -> f.ruleId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> fromExplain = rules.explain(analysis).stream()
                .filter(e -> e.outcome() == RuleSet.Outcome.FIRED)
                .map(e -> e.rule().id())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(fromExplain, fromRun,
                "the census must measure the same loop the audit runs, not a second copy");
    }

    @Test
    void explain_accounts_for_every_rule_exactly_once(PageProbe probe) {
        Analysis analysis = Analysis.of(probe.capture(Fixtures.clean(), Viewport.DESKTOP),
                Config.defaults());
        RuleSet rules = RuleSet.standard();
        List<RuleSet.Execution> executions = rules.explain(analysis);

        assertEquals(rules.rules().size(), executions.size());
        assertEquals(rules.rules().stream().map(Rule::id).toList(),
                executions.stream().map(e -> e.rule().id()).toList(),
                "a rule missing from the census is invisible, which is the fault being hunted");
    }

    @Test
    void a_pixel_rule_without_pixels_is_skipped_and_says_so(PageProbe probe) {
        PageSnapshot snapshot = probe.capture(Fixtures.broken(), Viewport.DESKTOP).snapshot();
        Analysis blind = Analysis.ofSnapshotOnly(snapshot, Viewport.DESKTOP, Config.defaults());

        List<RuleSet.Execution> executions = RuleSet.standard().explain(blind);
        List<RuleSet.Execution> pixelRules = executions.stream()
                .filter(e -> e.rule().needsPixels()).toList();

        assertFalse(pixelRules.isEmpty(), "there are pixel-reading rules to skip");
        assertTrue(pixelRules.stream()
                        .allMatch(e -> e.outcome() == RuleSet.Outcome.SKIPPED_NO_PIXELS),
                "silence and never running must be distinguishable; that is the whole point");
        assertTrue(pixelRules.stream().noneMatch(RuleSet.Execution::ran));
    }

    @Test
    void a_rule_that_throws_is_recorded_rather_than_breaking_the_run(PageProbe probe) {
        Analysis analysis = Analysis.of(probe.capture(Fixtures.clean(), Viewport.DESKTOP),
                Config.defaults());
        Rule exploding = new Rule() {
            @Override public String id() { return "always-throws"; }
            @Override public String title() { return "A rule that cannot run"; }
            @Override public String rationale() { return "For the test below."; }
            @Override public List<com.calpano.uxlens.model.Finding> check(Analysis a) {
                throw new IllegalStateException("boom");
            }
        };

        List<RuleSet.Execution> executions = RuleSet.of(exploding).explain(analysis);
        assertEquals(1, executions.size());
        assertEquals(RuleSet.Outcome.THREW, executions.get(0).outcome());
        assertTrue(executions.get(0).error().contains("boom"));
        assertEquals(1, executions.get(0).findings().size(),
                "the failure is reported as a finding so one broken check cannot hide the rest");
    }

    // ------------------------------------------------------- the capture check

    @Test
    void an_error_page_is_not_mistaken_for_the_page_it_was_pinned_under() {
        // The shape the Wikimedia rate-limit page had: small, flat, almost linkless, and
        // ending at the fold. Pinned under the name of a Wikipedia article, it passed for
        // right-to-left coverage until something looked.
        PageSnapshot errorPage = new PageSnapshot();
        errorPage.url = "https://ar.wikipedia.org/wiki/x";
        errorPage.requestedWidth = 1440;
        errorPage.requestedHeight = 900;
        errorPage.scrollHeight = 900;
        errorPage.elements = List.of(box("body", 1440, 900), box("div", 640, 741),
                box("h1", 576, 28), box("p", 576, 66), box("code", 1322, 59));

        Census.CaptureRow row = Census.inspect("wikipedia-ar|desktop", errorPage.url, errorPage,
                false, false);
        assertTrue(row.isSuspect(), "flags were " + row.flags());
        assertTrue(row.flags().contains("thin"));
        assertTrue(row.flags().contains("does not scroll"));
    }

    @Test
    void a_real_page_is_not_called_suspect(PageProbe probe) {
        PageSnapshot real = probe.capture(Fixtures.broken(), Viewport.DESKTOP).snapshot();
        Census.CaptureRow row = Census.inspect("fixture|desktop", "fixture", real, true, false);
        assertFalse(row.isSuspect(), "flags were " + row.flags());
    }

    @Test
    void a_site_may_declare_itself_genuinely_small() {
        PageSnapshot tiny = new PageSnapshot();
        tiny.requestedWidth = 390;
        tiny.requestedHeight = 844;
        tiny.scrollHeight = 185;
        tiny.elements = List.of(box("body", 390, 185), box("h1", 390, 20));

        assertTrue(Census.inspect("todo|mobile", "u", tiny, false, false).isSuspect());
        assertFalse(Census.inspect("todo|mobile", "u", tiny, false, true).isSuspect(),
                "an empty to-do app and an error page have the same shape; only the site knows");
    }

    @Test
    void capture_refuses_to_pin_a_page_that_does_not_look_like_a_page(PageProbe probe,
                                                                     @TempDir Path dir)
            throws java.io.IOException {
        Path page = java.nio.file.Files.writeString(dir.resolve("error.html"),
                "<!doctype html><title>Error</title><body>"
                        + "<h1>Error</h1><p>Our servers are under maintenance.</p>"
                        + "<code>Request from 0.0.0.0 via cache</code></body>");
        CorpusStore.writeSites(dir, List.of(new CorpusStore.Site("stub", page.toUri().toString(),
                List.of("desktop"), null, false, false, false, null, "content", "an error page")));

        CorpusStore.CaptureReport report = CorpusStore.capture(probe, dir, false);
        assertEquals(0, report.written());
        assertEquals(1, report.failures().size());
        assertTrue(report.failures().get(0).reason().contains("does not look like a page"),
                report.failures().get(0).reason());
        assertTrue(CorpusStore.load(dir).isEmpty(), "nothing rotten was pinned");
    }

    // ------------------------------------------------------------ the measurements

    @Test
    void nothing_is_dead_or_broken_over_a_corpus_with_pixels(PageProbe probe, @TempDir Path dir) {
        Census.Result result = Census.run(CorpusStore.load(seed(probe, dir, true)),
                Config.defaults(), dir.resolve("out"), Set.of("clean", "broken"));

        assertEquals(2, result.captures());
        assertEquals(2, result.capturesWithPixels());
        assertTrue(result.rulesWith(Census.Verdict.NEVER_RAN).isEmpty(),
                () -> "rules that never got a turn: " + ids(result, Census.Verdict.NEVER_RAN));
        assertTrue(result.rulesWith(Census.Verdict.THREW).isEmpty(),
                () -> "rules that threw: " + ids(result, Census.Verdict.THREW));
        assertTrue(result.measuresWith(Census.Health.FAILED).isEmpty(),
                () -> "measurements that threw: " + names(result, Census.Health.FAILED));
        assertTrue(result.measuresWith(Census.Health.DEAD).isEmpty(),
                () -> "measurements that produced nothing: " + names(result, Census.Health.DEAD));
        assertTrue(result.measuresWith(Census.Health.WRONG).isEmpty(),
                () -> "measurements that failed their own check: " + names(result, Census.Health.WRONG));
    }

    @Test
    void without_pixels_the_pixel_measurements_are_not_applicable_rather_than_dead(
            PageProbe probe, @TempDir Path dir) {
        Census.Result result = Census.run(CorpusStore.load(seed(probe, dir, false)),
                Config.defaults(), dir.resolve("out"), Set.of("clean", "broken"));

        assertEquals(0, result.capturesWithPixels());
        List<Census.MeasureRow> pixelRows = result.measures().stream()
                .filter(m -> m.measure().needsPixels()).toList();
        assertFalse(pixelRows.isEmpty());
        for (Census.MeasureRow row : pixelRows) {
            assertEquals(result.captures(), row.notApplicable(), row.measure().name());
            assertEquals(0, row.noValue(), () -> row.measure().name()
                    + " was skipped by its gate, which is not the same as producing nothing");
        }
    }

    @Test
    void every_rule_gets_a_row_whether_it_ran_or_not(PageProbe probe, @TempDir Path dir) {
        Census.Result result = Census.run(CorpusStore.load(seed(probe, dir, false)),
                Config.defaults(), dir.resolve("out"), Set.of("clean", "broken"));
        assertEquals(RuleSet.standard().rules().size(), result.rules().size());
        for (Census.RuleRow row : result.rules()) {
            assertEquals(result.captures(), row.ran() + row.skipped(), row.ruleId());
        }
    }

    @Test
    void a_broken_fixture_exercises_rules_and_a_clean_one_does_not(PageProbe probe,
                                                                  @TempDir Path dir) {
        Census.Result result = Census.run(CorpusStore.load(seed(probe, dir, true)),
                Config.defaults(), dir.resolve("out"), Set.of("clean", "broken"));
        List<Census.RuleRow> exercised = result.rulesWith(Census.Verdict.EXERCISED);
        assertFalse(exercised.isEmpty(), "the broken fixture must trip something");
        assertTrue(exercised.stream().allMatch(r -> r.examples().stream()
                        .allMatch(key -> key.startsWith("broken"))),
                "every rule that fired did so on the broken fixture, not the clean one");
    }

    @Test
    void the_squint_threshold_has_one_home(PageProbe probe) {
        // Asked at half the luminance range this measurement read zero on every page and
        // looked dead. Nothing was wrong with it; the census was asking a different
        // question from the rule. One constant, one question.
        assertEquals(0.06, com.calpano.uxlens.perception.SquintTest.BLOB_THRESHOLD, 1e-12);
    }

    private static String ids(Census.Result result, Census.Verdict verdict) {
        return result.rulesWith(verdict).stream().map(Census.RuleRow::ruleId).toList().toString();
    }

    private static String names(Census.Result result, Census.Health health) {
        return result.measuresWith(health).stream()
                .map(m -> m.measure().name() + (m.error() == null ? "" : ": " + m.error()))
                .toList().toString();
    }

    private static Elem box(String tag, double w, double h) {
        Elem e = new Elem();
        e.tag = tag;
        e.selector = tag;
        e.w = w;
        e.h = h;
        e.opacity = 1;
        e.display = "block";
        return e;
    }
}
