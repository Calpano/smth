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

    @TestFactory
    @DisplayName("contract")
    Stream<DynamicTest> each_contract_holds(Auditor auditor) {
        return RuleSet.standard().rules().stream()
                .filter(r -> r.contract().isPresent())
                .map(rule -> DynamicTest.dynamicTest(rule.id(), () -> {
                    RuleContract contract = rule.contract().orElseThrow();

                    Report onViolating = audit(auditor, rule, contract, contract.violates());
                    assertTrue(fired(onViolating, rule),
                            rule.id() + " did not fire on the page that violates it. Either the "
                                    + "rule has stopped working, or the page no longer violates it.\n"
                                    + "page:\n" + contract.violates());

                    Report onConforming = audit(auditor, rule, contract, contract.conforms());
                    assertTrue(!fired(onConforming, rule),
                            rule.id() + " fired on the page that conforms to it, so it is not "
                                    + "measuring what it claims to.\nfindings: "
                                    + onConforming.allFindings() + "\npage:\n" + contract.conforms());
                }));
    }

    private static Report audit(Auditor auditor, Rule rule, RuleContract contract, String html) {
        Config config = Config.defaults();
        config.only(rule.id());
        return auditor.config(config).viewports(contract.viewport()).auditHtml(html);
    }

    private static boolean fired(Report report, Rule rule) {
        return report.allFindings().stream().anyMatch(f -> f.ruleId.equals(rule.id()));
    }
}
