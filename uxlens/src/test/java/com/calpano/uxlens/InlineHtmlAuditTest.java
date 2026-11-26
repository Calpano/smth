package com.calpano.uxlens;

import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.junit.UxAssert;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component-level audits: the fast path, where one fragment is checked in isolation.
 *
 * <p>This is how the tool is meant to be used in a unit test suite -- not a whole page,
 * but the card or the form row you just changed.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Auditing an HTML fragment in isolation")
class InlineHtmlAuditTest {

    @Test
    void a_flat_page_is_reported_as_having_no_hierarchy(Auditor auditor) {
        // Uniform grey, no contrast anywhere: blurred, nothing stands out at all.
        Report report = auditor.viewports(Viewport.DESKTOP).auditHtml("""
                <body style="margin:0;background:#9a9a9a;height:1200px">
                  <p style="color:#9d9d9d;font:16px system-ui;padding:40px">
                    Text that barely differs from the surface it sits on.</p>
                </body>""");
        UxAssert.that(report).flags("squint-cohesion");
        Finding f = report.allFindings().stream()
                .filter(x -> x.ruleId.equals("squint-cohesion")).findFirst().orElseThrow();
        assertTrue(((Number) f.data.get("blobs")).intValue() <= 1, "data was " + f.data);
    }

    @Test
    void transparency_is_composited_before_contrast_is_judged(Auditor auditor) {
        // The declared colour is pure white, which would pass against anything dark.
        // What is painted is white at 35% over a mid-blue, which does not.
        Report report = auditor.viewports(Viewport.DESKTOP).auditHtml("""
                <body style="margin:0;background:#4a7fd4">
                  <p style="color:rgba(255,255,255,0.35);font:16px system-ui;padding:40px">
                    Barely-there caption text.</p>
                </body>""");
        Finding f = report.allFindings().stream()
                .filter(x -> x.ruleId.equals("text-contrast")).findFirst()
                .orElseThrow(() -> new AssertionError("compositing was not applied"));
        double ratio = ((Number) f.data.get("ratio")).doubleValue();
        assertTrue(ratio < 2.0, "expected a low composited ratio, got " + ratio);
    }

    @Test
    void contrast_is_measured_against_the_nearest_painted_ancestor(Auditor auditor) {
        // The text's own background is transparent; the colour behind it comes from a
        // grandparent, which is the case hand-checking gets wrong.
        Report report = auditor.viewports(Viewport.DESKTOP).auditHtml("""
                <body style="margin:0;background:#ffffff">
                  <div style="background:#13293d;padding:40px">
                    <div style="padding:8px">
                      <p style="color:#2a3f55;font:16px system-ui;margin:0">Dark on dark.</p>
                    </div>
                  </div>
                </body>""");
        Finding f = report.allFindings().stream()
                .filter(x -> x.ruleId.equals("text-contrast")).findFirst()
                .orElseThrow(() -> new AssertionError("ancestor background was not resolved"));
        assertEquals("#13293d", f.data.get("background"));
    }

    @Test
    void large_text_is_held_to_the_lower_threshold(Auditor auditor) {
        // 3.5:1 passes for 32px text and fails for 16px text, per WCAG.
        String page = """
                <body style="margin:0;background:#fff;font-family:system-ui">
                  <p style="color:#949494;font-size:%dpx;padding:24px;margin:0">Heading-ish text</p>
                </body>""";
        Report large = auditor.viewports(Viewport.DESKTOP).auditHtml(page.formatted(32));
        Report small = auditor.viewports(Viewport.DESKTOP).auditHtml(page.formatted(16));
        UxAssert.that(large).passes("text-contrast");
        UxAssert.that(small).flags("text-contrast");
    }

    @Test
    void aaa_mode_is_stricter_than_aa(Auditor auditor) {
        // #767676 on white is 4.54:1 -- it passes AA and fails AAA.
        String page = """
                <body style="margin:0;background:#fff">
                  <p style="color:#767676;font:16px system-ui;padding:24px;margin:0">Body copy.</p>
                </body>""";
        UxAssert.that(auditor.viewports(Viewport.DESKTOP).auditHtml(page)).passes("text-contrast");

        auditor.config().aaa();
        UxAssert.that(auditor.viewports(Viewport.DESKTOP).auditHtml(page)).flags("text-contrast");
    }

    @Test
    void a_defect_that_only_appears_at_one_viewport_is_found_there(Auditor auditor) {
        // 420px of fixed-width content: fine on desktop, overflowing on a phone.
        Report report = auditor.viewports(Viewport.DESKTOP, Viewport.MOBILE).auditHtml("""
                <body style="margin:0"><div style="width:420px;height:80px;background:#345"></div></body>""");
        assertTrue(report.viewports.get(0).findings.stream()
                        .noneMatch(f -> f.ruleId.equals("horizontal-overflow")),
                "desktop has room for 420px");
        assertTrue(report.viewports.get(1).findings.stream()
                        .anyMatch(f -> f.ruleId.equals("horizontal-overflow")),
                "mobile does not");
    }

    @Test
    void rules_can_be_restricted_to_the_one_under_test(Auditor auditor) {
        auditor.config().only("text-contrast");
        Report report = auditor.viewports(Viewport.DESKTOP).auditHtml("""
                <body style="margin:0;background:#fff">
                  <div style="width:2000px">
                    <p style="color:#ccc;font:16px system-ui">Faint.</p></div></body>""");
        assertTrue(report.allFindings().stream().allMatch(f -> f.ruleId.equals("text-contrast")),
                "only the selected rule should run, got "
                        + report.allFindings().stream().map(f -> f.ruleId).distinct().toList());
        UxAssert.that(report).flags("text-contrast").passes("horizontal-overflow");
    }

    @Test
    void a_rule_can_be_waived_without_waiving_the_rest(Auditor auditor) {
        auditor.config().disable("text-contrast");
        Report report = auditor.viewports(Viewport.MOBILE).auditHtml("""
                <body style="margin:0;background:#fff">
                  <div style="width:2000px">
                    <p style="color:#ccc;font:16px system-ui">Faint.</p></div></body>""");
        UxAssert.that(report).passes("text-contrast").flags("horizontal-overflow");
    }

    @Test
    void an_unloadable_image_is_reported_rather_than_ignored(Auditor auditor) {
        Report report = auditor.viewports(Viewport.DESKTOP).auditHtml("""
                <body><img src="nothing-here.png" width="200" height="120" alt="Gone"></body>""");
        UxAssert.that(report).flags("broken-image");
        assertEquals(Severity.HIGH, report.allFindings().stream()
                .filter(f -> f.ruleId.equals("broken-image")).findFirst().orElseThrow().severity);
    }
}
