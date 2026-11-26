package com.calpano.uxlens;

import com.calpano.uxlens.rules.Reference;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleReferences;
import com.calpano.uxlens.rules.RuleSet;
import com.calpano.uxlens.rules.StandardsCoverage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every rule has to say what it is grounded in.
 *
 * <p>The point is not bookkeeping. A rule mined out of a book or a standard arrives sounding
 * authoritative, and the question worth forcing is whether the authority actually says the
 * thing the rule checks, or whether the predicate and the threshold are ours wearing
 * somebody else's name. Requiring a reference makes that an explicit decision, and {@link
 * Reference.Source#HOUSE} is an honourable answer.
 */
@DisplayName("Every rule declares what it is grounded in")
class RuleReferenceTest {

    @Test
    void no_rule_is_without_a_source() {
        List<String> undeclared = RuleSet.standard().rules().stream()
                .filter(r -> r.references().isEmpty())
                .map(Rule::id)
                .toList();
        if (!undeclared.isEmpty()) {
            fail("These rules claim no authority at all: " + undeclared
                    + "\nAdd them to RuleReferences. If the rule rests on nothing but this "
                    + "project's judgement, say so with Source.HOUSE -- that is a valid answer "
                    + "and the one a team most needs to see.");
        }
    }

    @Test
    void no_reference_is_registered_for_a_rule_that_no_longer_exists() {
        Set<String> live = RuleSet.standard().rules().stream()
                .map(Rule::id).collect(Collectors.toSet());
        List<String> orphaned = RuleReferences.covered().stream()
                .filter(id -> !live.contains(id)).toList();
        assertTrue(orphaned.isEmpty(), "references for rules that no longer exist: " + orphaned);
    }

    @Test
    void every_reference_says_what_the_source_says() {
        for (Rule rule : RuleSet.standard().rules()) {
            for (Reference reference : rule.references()) {
                assertTrue(reference.says() != null && reference.says().length() > 20,
                        rule.id() + " cites " + reference.source() + " without saying what it "
                                + "says. A bare citation is decoration.");
            }
        }
    }

    @Test
    void wcag_is_cited_by_exact_criterion_and_other_standards_are_not_faked() {
        for (Rule rule : RuleSet.standard().rules()) {
            for (Reference reference : rule.references()) {
                if (reference.source() == Reference.Source.WCAG) {
                    assertTrue(reference.clause() != null && reference.clause().matches("^(\\d+\\.\\d+\\.\\d+|F\\d+)\\b.*"),
                            rule.id() + " cites WCAG as '" + reference.clause() + "'. WCAG "
                                    + "criteria are stably numbered, so cite the number.");
                }
            }
        }
    }

    @Test
    void the_rules_resting_only_on_our_own_judgement_are_named_honestly() {
        List<String> house = StandardsCoverage.houseOnly();
        // Not a number to optimise -- but if it were zero, somebody would be overclaiming.
        assertFalse(house.isEmpty(),
                "No rule admits to being this project's own judgement, which cannot be true.");
        assertTrue(house.contains("no-tonal-anchor"),
                "no-tonal-anchor rests entirely on our own judgement and must say so: " + house);
    }

    @Test
    void the_out_of_scope_list_explains_itself() {
        List<StandardsCoverage.OutOfScope> out = StandardsCoverage.outOfScope();
        assertTrue(out.size() >= 8, "the honest half of the coverage map should not be thin");
        for (StandardsCoverage.OutOfScope entry : out) {
            assertTrue(entry.why().length() > 40, entry.area() + " needs a real reason");
            assertTrue(entry.insteadUse().length() > 15,
                    entry.area() + " should say what would actually answer it");
        }
    }

    @Test
    void coverage_accounts_for_every_rule() {
        int rules = RuleSet.standard().rules().size();
        Set<String> covered = StandardsCoverage.coverage().stream()
                .flatMap(c -> c.ruleIds().stream()).collect(Collectors.toSet());
        assertTrue(covered.size() == rules,
                "coverage lists " + covered.size() + " of " + rules + " rules");
    }
}
