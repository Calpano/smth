package com.calpano.uxlens;

import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleContract;
import com.calpano.uxlens.rules.RuleContracts;
import com.calpano.uxlens.rules.RuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The gate every rule has to pass: can it tell a page that violates it from one that does not?
 *
 * <p>Without this, a plausible-sounding principle turns into a check that fires on
 * everything or on nothing, and both are worse than no check at all. It matters most for
 * rules mined from outside sources -- a line from a style guide arrives as prose, and prose
 * always sounds checkable. Writing the conforming page is what settles it.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Every rule discriminates between a violating and a conforming page")
class RuleContractTest {

    /**
     * Rules allowed to have no contract, each with the reason.
     *
     * <p>Deliberately empty. If a rule genuinely cannot be demonstrated on a self-contained
     * page, add it here with a reason rather than quietly leaving it unproven -- the point
     * is that skipping the gate has to be a decision somebody wrote down.
     */
    private static final Map<String, String> EXEMPT = Map.of();

    @Test
    void every_rule_declares_a_contract_or_a_reason_for_not_having_one() {
        List<String> undeclared = RuleSet.standard().rules().stream()
                .filter(r -> r.contract().isEmpty())
                .map(Rule::id)
                .filter(id -> !EXEMPT.containsKey(id))
                .toList();
        if (!undeclared.isEmpty()) {
            fail("These rules cannot be shown to discriminate: " + undeclared
                    + "\nAdd a pair of pages to RuleContracts -- one the rule fires on, one it "
                    + "stays silent on -- or list it in EXEMPT with a reason.");
        }
    }

    @Test
    void no_contract_is_registered_for_a_rule_that_no_longer_exists() {
        Set<String> live = RuleSet.standard().rules().stream()
                .map(Rule::id).collect(java.util.stream.Collectors.toSet());
        List<String> orphaned = RuleContracts.covered().stream()
                .filter(id -> !live.contains(id)).toList();
        assertTrue(orphaned.isEmpty(), "contracts for rules that no longer exist: " + orphaned);
    }

    /**
     * Rules allowed to fire on somebody else's conforming page, with the reason.
     *
     * <p>The sixty conforming pages are between them the cleanest pages this project has:
     * each is a minimal page with its one deliberate defect removed. Anything firing there
     * is a false-positive candidate, so the set is pinned and any growth fails the build.
     *
     * <p>Two kinds of entry are listed. Most are fixture artefacts: a minimal page is a
     * strange page, a form fixture has no heading, a text fixture has one wide paragraph,
     * and the second complaint is correct about the page even though the page was not
     * written to make it. Driving these to zero would mean growing every fixture into a
     * realistic page, which is what the real corpus is for. The rest are rules already
     * known to be weak -- {@code spacing-scale} and {@code contrast-model-disagreement}
     * both appear here and both are unvalidated or scoring zero on the real corpus, so
     * this is one more piece of the same evidence.
     *
     * <p>Before the content floor went into the hierarchy rules this map would have needed
     * fifty-nine entries, almost all of them {@code squint-cohesion}: it fired on
     * fifty-eight of the sixty clean pages, because two paragraphs on white really do blur
     * into one shape. That was invisible on the real corpus, where every page has enough
     * content to have a hierarchy, and it is the clearest thing the synthetic corpus found.
     */
    private static final Map<String, Set<String>> CROSS_TALK_ALLOWED = Map.ofEntries(
            // a form fixture has no heading, and a dialog fixture has two buttons side by side
            Map.entry("dialog-without-escape", Set.of("destructive-adjacency", "flat-type-hierarchy")),
            Map.entry("invalid-field-without-message", Set.of("flat-type-hierarchy")),
            Map.entry("required-field-unmarked", Set.of("flat-type-hierarchy")),
            Map.entry("placeholder-as-label", Set.of("flat-type-hierarchy")),
            // the conforming image is correctly sized for its box and still upscaled from source
            Map.entry("distorted-image", Set.of("upscaled-image")),
            // a text fixture is one wide paragraph, which is a long measure whatever else it shows
            Map.entry("leading", Set.of("measure")),
            Map.entry("leading-for-measure", Set.of("measure")),
            Map.entry("text-over-texture", Set.of("text-contrast")),
            // a two-element fixture has two comparable elements; see the floor note above
            Map.entry("flat-grouping-hierarchy", Set.of("competing-focal-points", "squint-cohesion")),
            Map.entry("mixed-label-placement", Set.of("competing-focal-points", "squint-cohesion")),
            Map.entry("palette-sprawl", Set.of("competing-focal-points", "squint-cohesion")),
            Map.entry("no-tonal-anchor", Set.of("contrast-model-disagreement", "squint-cohesion")),
            // A page of body copy on white has no tonal structure for the squint test to
            // find, because blurring text washes it towards the page colour. That is worth
            // knowing about the rule rather than about the fixture: squint-cohesion measures
            // whether a page has blocks, and a text page legitimately has none.
            Map.entry("competing-focal-points", Set.of("spacing-scale", "squint-cohesion")),
            // rules already known to be weak on the real corpus
            Map.entry("noisy-type-scale", Set.of("spacing-scale", "flat-type-hierarchy")),
            Map.entry("squint-cohesion", Set.of("spacing-scale")),
            Map.entry("unlabelled-icon-control", Set.of("spacing-scale")),
            Map.entry("optical-centering", Set.of("unlabelled-icon-control")),
            Map.entry("font-family-sprawl", Set.of("consistency-typography")),
            Map.entry("touch-target", Set.of("horizontal-overflow")),
            Map.entry("slow-state-feedback", Set.of("spacing-scale")));

    @TestFactory
    @DisplayName("contract")
    Stream<DynamicTest> each_contract_holds(Auditor auditor) {
        return RuleSet.standard().rules().stream()
                .filter(r -> r.contract().isPresent())
                .map(rule -> DynamicTest.dynamicTest(rule.id(), () -> {
                    RuleContract contract = rule.contract().orElseThrow();

                    Report onViolating = audit(auditor, rule, contract, contract.violates(), true);
                    assertTrue(fired(onViolating, rule),
                            rule.id() + " did not fire on the page that violates it. Either the "
                                    + "rule has stopped working, or the page no longer violates it.\n"
                                    + "page:\n" + contract.violates());

                    // Every rule on the conforming page, not just this one: it costs no extra
                    // render and turns the contract into a false-positive check as well.
                    Report onConforming = audit(auditor, rule, contract, contract.conforms(), false);
                    assertTrue(!fired(onConforming, rule),
                            rule.id() + " fired on the page that conforms to it, so it is not "
                                    + "measuring what it claims to.\nfindings: "
                                    + onConforming.allFindings() + "\npage:\n" + contract.conforms());

                    Set<String> others = onConforming.allFindings().stream()
                            .map(f -> f.ruleId)
                            .filter(id -> !id.equals(rule.id()))
                            .collect(java.util.stream.Collectors.toCollection(
                                    java.util.TreeSet::new));
                    Set<String> allowed = CROSS_TALK_ALLOWED.getOrDefault(rule.id(), Set.of());
                    Set<String> unexpected = new java.util.TreeSet<>(others);
                    unexpected.removeAll(allowed);
                    assertTrue(unexpected.isEmpty(),
                            unexpected + " fired on the conforming page for " + rule.id()
                                    + ", which is a minimal page with its one defect removed.\n"
                                    + "Either that is a false positive worth fixing, or it is a "
                                    + "fixture artefact -- in which case add it to "
                                    + "CROSS_TALK_ALLOWED with the reason.\nfindings: "
                                    + onConforming.allFindings() + "\npage:\n"
                                    + contract.conforms());
                }));
    }

    private static Report audit(Auditor auditor, Rule rule, RuleContract contract, String html,
                                boolean onlyThisRule) {
        Config config = Config.defaults();
        if (onlyThisRule) config.only(rule.id());
        return auditor.config(config).viewports(contract.viewport()).auditHtml(html);
    }

    private static boolean fired(Report report, Rule rule) {
        return report.allFindings().stream().anyMatch(f -> f.ruleId.equals(rule.id()));
    }
}
