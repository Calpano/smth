package com.calpano.uxlens;

import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.junit.UxAssert;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detection guard, run against a fixture whose every block is a known defect.
 *
 * <p>Each rule id below corresponds to a commented block in {@code broken.html}. If a rule
 * stops firing here, it has stopped working -- the page has not changed.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("A deliberately broken page is caught rule by rule")
class BrokenPageAuditTest {

    private static Report report;

    @BeforeAll
    static void auditOnce(Auditor auditor) {
        report = auditor.viewports(Viewport.DESKTOP, Viewport.MOBILE).audit(Fixtures.broken());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // layout
            "horizontal-overflow", "clipped-text", "off-viewport", "text-overlap", "touch-target",
            // alignment
            "edge-alignment", "baseline-drift", "row-center-drift", "page-margin",
            // rhythm
            "gap-consistency", "spacing-scale",
            // typography
            "flat-type-hierarchy", "noisy-type-scale", "font-family-sprawl",
            "measure", "leading", "orphan-line", "all-caps-run",
            // colour
            "text-contrast", "control-border-contrast", "palette-sprawl", "near-duplicate-colors",
            // hierarchy
            "competing-focal-points", "inverted-hierarchy", "primary-action-not-salient",
            // proximity
            "label-proximity", "orphaned-heading",
            // images
            "upscaled-image", "distorted-image", "broken-image", "missing-alt",
    })
    void rule_fires(String ruleId) {
        UxAssert.that(report).flags(ruleId);
    }

    @Test
    void every_actionable_finding_names_an_element_or_a_region() {
        // Page-level findings ("this page uses five typefaces") have no single element to
        // point at, and that is correct. Anything at MEDIUM or above must be locatable.
        for (Finding f : report.findings(Severity.MEDIUM)) {
            boolean located = !f.selectors.isEmpty() || f.focus != null;
            assertTrue(located, "finding carries no location: " + f.ruleId + " -- " + f.message);
        }
    }

    @Test
    void page_level_findings_still_carry_their_numbers() {
        for (Finding f : report.allFindings()) {
            if (!f.selectors.isEmpty() || f.focus != null) continue;
            assertFalse(f.data.isEmpty(),
                    "a finding with no element must at least state its measurement: " + f.ruleId);
        }
    }

    @Test
    void every_finding_states_a_measurement_and_a_fix() {
        for (Finding f : report.allFindings()) {
            assertNotNull(f.message, f.ruleId + " has no message");
            assertTrue(f.message.length() > 20, f.ruleId + " message is too terse: " + f.message);
            assertNotNull(f.suggestion, f.ruleId + " proposes no fix: " + f.message);
        }
    }

    @Test
    void clipped_text_reports_how_much_is_hidden() {
        Finding f = first("clipped-text");
        assertTrue(((Number) f.data.get("hiddenPx")).intValue() > 50,
                "expected the fixture's 34px box to hide most of its text, data=" + f.data);
        assertEquals(Severity.HIGH, f.severity);
    }

    @Test
    void contrast_finding_suggests_a_colour_that_would_pass() {
        Finding f = report.allFindings().stream()
                .filter(x -> x.ruleId.equals("text-contrast")
                        && "#bbbbbb".equals(x.data.get("foreground")))
                .findFirst().orElseThrow(() -> new AssertionError("the #bbb paragraph was not flagged"));
        assertEquals(1.92, ((Number) f.data.get("ratio")).doubleValue(), 0.02);
        assertTrue(f.suggestion.matches(".*#[0-9a-f]{6}.*"),
                "expected a concrete replacement colour, got: " + f.suggestion);
    }

    @Test
    void edge_alignment_reports_the_three_pixel_nudge() {
        Finding f = first("edge-alignment");
        assertEquals(3.0, ((Number) f.data.get("deltaPx")).doubleValue(), 0.6);
    }

    @Test
    void gap_consistency_names_the_odd_gap_out() {
        Finding f = first("gap-consistency");
        assertEquals(24.0, ((Number) f.data.get("median")).doubleValue(), 0.5);
        assertEquals(7.0, ((Number) f.data.get("worstDeviationPx")).doubleValue(), 1.0);
    }

    @Test
    void findings_are_ordered_most_severe_first() {
        for (Report.ViewportResult v : report.viewports) {
            List<Finding> fs = v.findings;
            for (int i = 1; i < fs.size(); i++) {
                assertTrue(fs.get(i - 1).severity.compareTo(fs.get(i).severity) >= 0,
                        "findings out of order at " + i);
            }
        }
    }

    @Test
    void assertions_fail_with_the_measurement_in_the_message() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> UxAssert.that(report).hasNothingAtOrAbove(Severity.HIGH));
        assertTrue(error.getMessage().contains("clipped-text"), error.getMessage());
        assertTrue(error.getMessage().contains("fix:"),
                "a failure must tell you what to change:\n" + error.getMessage());
    }

    private static Finding first(String ruleId) {
        Optional<Finding> f = report.allFindings().stream()
                .filter(x -> ruleId.equals(x.ruleId)).findFirst();
        return f.orElseThrow(() -> new AssertionError("rule did not fire: " + ruleId));
    }
}
