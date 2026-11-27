package com.calpano.uxlens.corpus;

import com.calpano.uxlens.Fixtures;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calibration loop: pin pages, run the rules over them, score against human verdicts.
 *
 * <p>The thresholds in {@code Config} are all somebody's taste until something measures
 * them. This is that something, and it only works if a pinned capture really does stop the
 * page moving between runs.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Calibration over a pinned corpus")
class CalibrationTest {

    private Path seedCorpus(PageProbe probe, Path dir, boolean withImages) {
        CorpusStore.writeSites(dir, List.of(
                new CorpusStore.Site("clean", Fixtures.clean(), List.of("desktop"), null, null, null, null, null, "the quiet one"),
                new CorpusStore.Site("broken", Fixtures.broken(), List.of("desktop"), null, null, null, null, null, "the loud one")));
        CorpusStore.capture(probe, dir, withImages);
        return dir;
    }

    @Test
    void a_capture_can_be_pinned_and_read_back(PageProbe probe, @TempDir Path dir) {
        assertEquals(2, CorpusStore.capture(probe, seedCorpus(probe, dir, false), false).written());

        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        assertEquals(2, corpus.size());
        for (CorpusStore.Entry entry : corpus) {
            assertFalse(entry.snapshot().elements.isEmpty(), "snapshot survived the round trip");
            assertEquals("desktop", entry.viewport().name());
            assertFalse(entry.hasPixels(), "images were not requested");
        }
    }

    @Test
    void pinning_keeps_the_page_still(PageProbe probe, @TempDir Path dir) {
        seedCorpus(probe, dir, false);
        Calibration.Result first = Calibration.run(CorpusStore.load(dir), Config.defaults(), Labels.empty());
        Calibration.Result second = Calibration.run(CorpusStore.load(dir), Config.defaults(), Labels.empty());
        assertEquals(first.findings().stream().map(Calibration.Scored::key).toList(),
                second.findings().stream().map(Calibration.Scored::key).toList(),
                "a pinned corpus has to give the same answer twice, or precision means nothing");
    }

    @Test
    void screenshots_are_optional_and_unlock_the_pixel_rules(PageProbe probe, @TempDir Path dir) {
        seedCorpus(probe, dir, true);
        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        assertTrue(corpus.stream().allMatch(CorpusStore.Entry::hasPixels));
        assertTrue(Files.exists(dir.resolve("broken").resolve("desktop").resolve("fold.png")));
    }

    @Test
    void the_broken_fixture_produces_findings_and_the_clean_one_does_not(PageProbe probe,
                                                                        @TempDir Path dir) {
        seedCorpus(probe, dir, false);
        Calibration.Result result = Calibration.run(
                CorpusStore.load(dir), Config.defaults(), Labels.empty());

        assertTrue(result.findings().stream().anyMatch(s -> s.entry().siteId().equals("broken")));
        assertTrue(result.findings().stream().noneMatch(s -> s.entry().siteId().equals("clean")),
                "the clean fixture must stay clean through the corpus path too");
    }

    @Test
    void labels_attach_to_findings_and_produce_precision(PageProbe probe, @TempDir Path dir) {
        seedCorpus(probe, dir, false);
        Calibration.Result unlabelled = Calibration.run(
                CorpusStore.load(dir), Config.defaults(), Labels.empty());
        assertEquals(unlabelled.findings().size(), unlabelled.unlabelled());
        assertTrue(unlabelled.scores().stream().noneMatch(Calibration.RuleScore::isJudged));

        // Judge one rule's findings: half right, half wrong.
        Labels labels = Labels.empty();
        List<Calibration.Scored> contrast = unlabelled.findings().stream()
                .filter(s -> s.finding().ruleId.equals("text-contrast")).toList();
        assertTrue(contrast.size() >= 2, "fixture should give us something to judge");
        labels.put(contrast.get(0).key(), Labels.Verdict.TRUE_POSITIVE, "real");
        labels.put(contrast.get(1).key(), Labels.Verdict.FALSE_POSITIVE, "not worth fixing");

        Calibration.Result scored = Calibration.run(CorpusStore.load(dir), Config.defaults(), labels);
        Calibration.RuleScore score = scored.scores().stream()
                .filter(s -> s.ruleId().equals("text-contrast")).findFirst().orElseThrow();
        assertEquals(1, score.truePositives());
        assertEquals(1, score.falsePositives());
        assertEquals(0.5, score.precision(), 1e-9);
        assertEquals(List.of("text-contrast"),
                scored.below(0.8).stream().map(Calibration.RuleScore::ruleId).toList());
    }

    @Test
    void an_unjudged_rule_never_fails_the_precision_floor(PageProbe probe, @TempDir Path dir) {
        seedCorpus(probe, dir, false);
        Calibration.Result result = Calibration.run(
                CorpusStore.load(dir), Config.defaults(), Labels.empty());
        assertTrue(result.below(0.99).isEmpty(),
                "nothing judged means nothing proven, which is not the same as something failing");
    }

    @Test
    void labels_survive_a_round_trip_and_ignore_the_wording_of_a_finding(@TempDir Path dir) {
        Labels labels = Labels.empty();
        labels.put("site|desktop|text-contrast|#footer", Labels.Verdict.TRUE_POSITIVE, "checked it");
        labels.save(dir);

        Labels reloaded = Labels.load(dir);
        assertEquals(1, reloaded.size());
        assertEquals(Labels.Verdict.TRUE_POSITIVE,
                reloaded.get("site|desktop|text-contrast|#footer").orElseThrow().verdict());
        assertEquals("checked it",
                reloaded.get("site|desktop|text-contrast|#footer").orElseThrow().note());
    }

    @Test
    void a_missing_corpus_says_so_rather_than_returning_nothing(@TempDir Path dir) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> CorpusStore.sites(dir));
        assertTrue(e.getMessage().contains("sites.json"), e.getMessage());
    }

    @Test
    void severity_can_be_raised_to_judge_only_what_matters(PageProbe probe, @TempDir Path dir) {
        seedCorpus(probe, dir, false);
        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        int all = Calibration.run(corpus, Config.defaults(), Labels.empty(), Severity.INFO)
                .findings().size();
        int serious = Calibration.run(corpus, Config.defaults(), Labels.empty(), Severity.HIGH)
                .findings().size();
        assertTrue(serious < all, "HIGH should be a subset: " + serious + " of " + all);
        assertTrue(serious > 0);
    }
}
