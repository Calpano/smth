package com.calpano.uxlens;

import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.junit.UxAssert;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The false-positive guard.
 *
 * <p>A visual QA tool that cries wolf is worse than none, because a team learns to ignore
 * it. So the deliberately well-built fixture must come back completely silent -- at every
 * severity, at every viewport. Any new rule that breaks this test is too eager.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("A well-built page produces no findings")
class CleanPageAuditTest {

    private static Report report;

    @BeforeAll
    static void auditOnce(Auditor auditor) {
        report = auditor.viewports(Viewport.DESKTOP, Viewport.MOBILE).audit(Fixtures.clean());
    }

    @Test
    void produces_nothing_at_any_severity() {
        UxAssert.that(report).hasNothingAtOrAbove(Severity.INFO);
    }

    @Test
    void reports_both_viewports() {
        assertEquals(2, report.viewports.size());
        assertEquals("desktop", report.viewports.get(0).viewportName);
        assertEquals("mobile", report.viewports.get(1).viewportName);
    }

    @Test
    void measures_a_plausible_number_of_elements() {
        report.viewports.forEach(v -> {
            assertTrue(v.elementCount > 20, "expected a measurable page, got " + v.elementCount);
            assertFalse(v.truncated, "fixture should be well under the probe's element cap");
        });
    }

    @Test
    void infers_the_page_s_own_eight_pixel_spacing_scale() {
        @SuppressWarnings("unchecked")
        var spacing = (java.util.Map<String, Object>) report.viewports.get(0).inferredTokens.get("spacing");
        assertEquals(8.0, ((Number) spacing.get("baseUnit")).doubleValue(), 0.001,
                "the fixture is built on an 8px scale and should be recognised as such");
        assertTrue(((Number) spacing.get("adherence")).doubleValue() > 0.95,
                "adherence was " + spacing.get("adherence"));
    }

    @Test
    void ranks_the_headline_as_the_loudest_thing_on_the_page() {
        var top = report.viewports.get(0).prominence.get(0);
        assertEquals("body > div > h1", top.selector,
                "expected the h1 to win the first glance, got " + top.selector);
        assertEquals(1, top.rank);
    }

    @Test
    void the_fluent_assertions_all_pass_on_a_clean_page() {
        UxAssert.that(report)
                .hasNoBrokenLayout()
                .hasClearVisualHierarchy()
                .hasConsistentSpacing()
                .isAligned()
                .allTextMeetsContrast()
                .hasAtMost(0, Severity.LOW);
    }
}
