package com.calpano.uxlens.corpus;

import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A corpus built on purpose, two pages per rule.
 *
 * <p>Most of what this generates is checked by {@code RuleContractTest}, which renders the
 * same pages live and is where a rule that stops discriminating fails. What is checked
 * here is the generation itself -- that every rule gets a fixture, that the fixtures
 * survive being pinned and read back, and that a pinned fixture still trips its rule once
 * it has been through the capture, redaction and reload path rather than only in memory.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Synthetic corpus: a fixture per rule")
class SyntheticTest {

    @Test
    void every_rule_gets_a_fixture_and_a_conforming_twin(@TempDir Path dir) {
        int written = Synthetic.write(dir);
        List<Rule> rules = RuleSet.standard().rules();

        assertTrue(Synthetic.withoutContract().isEmpty(),
                () -> "rules with no contract, so no fixture: " + Synthetic.withoutContract());
        assertEquals(rules.size() * 2, written, "one violating and one conforming page per rule");

        List<CorpusStore.Site> sites = CorpusStore.sites(dir);
        assertEquals(written, sites.size());
        for (CorpusStore.Site site : sites) {
            assertTrue(site.isSparse(), site.id()
                    + " must be marked sparse: a minimal fixture has the same shape as an "
                    + "error page, and the capture check would reject it");
            assertTrue(site.keepsImages(false), site.id()
                    + " must keep its pixels, or the pixel-reading rules get no turn");
            assertTrue(Files.exists(Path.of(java.net.URI.create(site.url()))), site.url());
        }
    }

    @Test
    void the_site_id_says_which_rule_a_fixture_is_for() {
        assertEquals("text-contrast", Synthetic.intendedRule("syn-text-contrast").orElseThrow());
        assertEquals("text-contrast",
                Synthetic.intendedRule("syn-text-contrast-ok").orElseThrow());
        assertTrue(Synthetic.isConformingPage("syn-text-contrast-ok"));
        assertFalse(Synthetic.isConformingPage("syn-text-contrast"));
        assertTrue(Synthetic.intendedRule("xam-de").isEmpty(),
                "a real site must not be mistaken for a fixture");
    }

    @Test
    void a_pinned_fixture_still_trips_its_rule_after_a_round_trip(PageProbe probe,
                                                                 @TempDir Path dir) {
        // Narrow to a handful: capturing all hundred and twenty takes minutes, and the full
        // set is covered by RuleContractTest, which renders them live every build.
        Synthetic.write(dir);
        List<CorpusStore.Site> all = CorpusStore.sites(dir);
        List<String> wanted = List.of("text-contrast", "touch-target", "measure",
                "squint-cohesion", "competing-focal-points");
        CorpusStore.writeSites(dir, all.stream()
                .filter(s -> Synthetic.intendedRule(s.id())
                        .map(wanted::contains).orElse(false))
                .toList());

        CorpusStore.CaptureReport report = CorpusStore.capture(probe, dir, true);
        assertTrue(report.isComplete(), () -> "capture failures: " + report.failures());
        assertEquals(wanted.size() * 2, report.written());

        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        assertTrue(corpus.stream().allMatch(CorpusStore.Entry::hasPixels),
                "a fixture without pixels cannot exercise the pixel rules");

        Synthetic.Isolation isolation = Synthetic.isolation(corpus, Config.defaults());
        assertEquals(wanted.size() * 2, isolation.fixtures().size());
        assertTrue(isolation.broken().isEmpty(),
                () -> isolation.broken().stream()
                        .map(f -> f.ruleId() + (f.conformingPage()
                                ? " fired on its conforming page"
                                : " did not fire on its violating page"))
                        .toList().toString());
    }

    @Test
    void the_fixtures_label_themselves(PageProbe probe, @TempDir Path dir) {
        Synthetic.write(dir);
        List<CorpusStore.Site> all = CorpusStore.sites(dir);
        CorpusStore.writeSites(dir, all.stream()
                .filter(s -> Synthetic.intendedRule(s.id())
                        .map("text-contrast"::equals).orElse(false))
                .toList());
        CorpusStore.capture(probe, dir, true);

        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        Labels labels = Synthetic.labels(corpus, Config.defaults());
        assertTrue(labels.size() > 0,
                "a page written to contain a defect can label that defect itself");
        assertTrue(labels.all().keySet().stream()
                        .allMatch(key -> key.contains("text-contrast")),
                "only the rule the page was written for is judged; the cross-talk is left "
                        + "unlabelled rather than guessed at: " + labels.all().keySet());
        assertTrue(labels.all().keySet().stream().noneMatch(k -> k.contains("-ok|")),
                "nothing is labelled on a conforming page, where there is nothing to judge");
    }
}
