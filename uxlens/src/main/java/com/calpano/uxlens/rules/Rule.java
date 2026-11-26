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

    List<Finding> check(Analysis analysis);

    /** Convenience for building findings that carry this rule's id. */
    default Finding finding(com.calpano.uxlens.model.Severity severity, String message) {
        return new Finding(id(), severity, message);
    }
}
