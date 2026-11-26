package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Finding;

import java.util.List;

/**
 * One thing a designer checks.
 *
 * <p>A rule never says "this looks wrong". It states a measurement, names the elements,
 * and proposes a change -- so that a human can disagree with the threshold and an agent
 * can act on the result.
 */
public interface Rule {

    /** Stable id, used in reports, config and assertions. Kebab-case. */
    String id();

    /** One line, in the language of design review. */
    String title();

    /** Why this matters, for the report and for an agent's prompt. */
    String rationale();

    /** True when the rule reads the rendered pixels and must be skipped without them. */
    default boolean needsPixels() { return false; }

    /**
     * True when the rule matches the literal words on the page, as opposed to their length
     * or shape. Such a rule is skipped on a redacted capture instead of silently finding
     * nothing in text that has had its letters replaced.
     */
    default boolean needsLiteralText() { return false; }

    List<Finding> check(Analysis analysis);

    /**
     * A pair of pages proving this rule discriminates: one it must fire on, one it must
     * stay silent on.
     *
     * <p>Enforced by {@code RuleContractTest}. A rule without one has to be listed there as
     * an explicit exemption with a reason, so that adding a check nobody can falsify takes
     * a deliberate act rather than an oversight.
     */
    default java.util.Optional<RuleContract> contract() { return RuleContracts.forRule(id()); }

    /**
     * What this rule is grounded in: a standard, a principle, the typographic canon, or
     * nothing but this project's judgement.
     *
     * <p>Carried into the report so a team can see whether they are arguing with WCAG or
     * with us, and enforced by {@code RuleReferenceTest} so that adding a rule forces
     * somebody to answer the question.
     */
    default List<Reference> references() { return RuleReferences.forRule(id()); }

    /** Convenience for building findings that carry this rule's id. */
    default Finding finding(com.calpano.uxlens.model.Severity severity, String message) {
        return new Finding(id(), severity, message);
    }
}
