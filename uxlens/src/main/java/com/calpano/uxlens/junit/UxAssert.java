package com.calpano.uxlens.junit;

import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Assertions that fail with the measurement, not with "expected true but was false".
 *
 * <p>A failing visual test is only useful if the message says which element, by how much,
 * and what to change -- so every failure here prints the finding, its selectors and its
 * suggested fix.
 */
public final class UxAssert {

    private final Report report;

    private UxAssert(Report report) { this.report = report; }

    public static UxAssert that(Report report) { return new UxAssert(report); }

    /** Fails if anything at or above {@code min} was found. */
    public UxAssert hasNothingAtOrAbove(Severity min) {
        List<Finding> bad = report.findings(min);
        if (bad.isEmpty()) return this;
        throw fail(bad.size() + " finding(s) at or above " + min, bad);
    }

    /** Fails if the named rule produced any finding. */
    public UxAssert passes(String ruleId) {
        List<Finding> bad = report.allFindings().stream().filter(f -> ruleId.equals(f.ruleId)).toList();
        if (bad.isEmpty()) return this;
        throw fail(bad.size() + " finding(s) from rule '" + ruleId + "'", bad);
    }

    /** Fails if any of the named rules produced a finding. */
    public UxAssert passes(String... ruleIds) {
        for (String id : ruleIds) passes(id);
        return this;
    }

    /** Fails unless the named rule produced at least one finding. For testing the rules themselves. */
    public UxAssert flags(String ruleId) {
        boolean found = report.allFindings().stream().anyMatch(f -> ruleId.equals(f.ruleId));
        if (found) return this;
        throw new AssertionFailedError("Expected rule '" + ruleId + "' to flag something, but it did not.\n"
                + "Rules that did fire: " + report.allFindings().stream().map(f -> f.ruleId).distinct().toList());
    }

    /** Fails if more than {@code max} findings are at or above {@code min} -- for tightening a ratchet. */
    public UxAssert hasAtMost(int max, Severity min) {
        List<Finding> bad = report.findings(min);
        if (bad.size() <= max) return this;
        throw fail("expected at most " + max + " finding(s) at or above " + min
                + " but found " + bad.size(), bad);
    }

    /** Fails if any finding matches. The predicate's description appears in the message. */
    public UxAssert hasNoFindingWhere(String description, Predicate<Finding> predicate) {
        List<Finding> bad = report.allFindings().stream().filter(predicate).toList();
        if (bad.isEmpty()) return this;
        throw fail(bad.size() + " finding(s) where " + description, bad);
    }

    /** Fails if any text on the page is below the required contrast ratio. */
    public UxAssert allTextMeetsContrast() { return passes("text-contrast"); }

    /** Fails if the layout is broken in a way any visitor would see. */
    public UxAssert hasNoBrokenLayout() {
        return passes("horizontal-overflow", "clipped-text", "off-viewport", "text-overlap", "broken-image");
    }

    /** Fails unless one element clearly leads the first viewport. */
    public UxAssert hasClearVisualHierarchy() {
        return passes("competing-focal-points", "inverted-hierarchy", "squint-cohesion");
    }

    /** Fails if sibling spacing is uneven or off the page's own scale. */
    public UxAssert hasConsistentSpacing() { return passes("gap-consistency", "spacing-scale"); }

    /** Fails if edges or baselines are near-but-not-aligned. */
    public UxAssert isAligned() {
        return passes("edge-alignment", "baseline-drift", "row-center-drift", "page-margin");
    }

    public Report report() { return report; }

    private static AssertionFailedError fail(String headline, List<Finding> findings) {
        StringBuilder sb = new StringBuilder("Visual QA: ").append(headline).append(".\n");
        for (Finding f : findings) {
            sb.append("\n  [").append(f.severity).append("] ").append(f.ruleId).append('\n');
            sb.append("    ").append(f.message).append('\n');
            if (!f.selectors.isEmpty()) sb.append("    at: ").append(String.join(", ", f.selectors)).append('\n');
            if (f.suggestion != null) sb.append("    fix: ").append(f.suggestion).append('\n');
            if (!f.data.isEmpty()) {
                sb.append("    measured:");
                for (Map.Entry<String, Object> e : f.data.entrySet()) {
                    sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
                }
                sb.append('\n');
            }
        }
        return new AssertionFailedError(sb.toString());
    }
}
