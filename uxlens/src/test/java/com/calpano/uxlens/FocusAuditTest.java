package com.calpano.uxlens;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.interactive.FocusAudit;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keyboard focus visibility, checked by focusing controls for real and looking at the
 * pixels. No stylesheet analysis can answer this: a focus ring can be declared and then
 * painted outside a clipping ancestor, or overridden three stylesheets later.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Focus indicators are checked in the pixels")
class FocusAuditTest {

    private List<Finding> auditFragment(PageProbe probe, String body) {
        try (BrowserContext ctx = probe.newContext(Viewport.DESKTOP)) {
            Page page = ctx.newPage();
            page.setContent(body);
            var capture = probe.measureCurrentState(page, Viewport.DESKTOP);
            return FocusAudit.check(page, capture.snapshot(), Viewport.DESKTOP);
        }
    }

    @Test
    void a_control_with_outline_none_and_nothing_else_is_flagged(PageProbe probe) {
        List<Finding> findings = auditFragment(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <button id="bad" style="outline:none;border:1px solid #333;background:#eee;
                          padding:12px 20px;font-size:16px">Save</button>
                </body>""");
        assertEquals(1, findings.size(), "expected exactly the one bad control: " + findings);
        Finding f = findings.get(0);
        assertEquals("focus-not-visible", f.ruleId);
        assertEquals(Severity.HIGH, f.severity);
        assertTrue(f.selectors.contains("#bad"), f.selectors.toString());
        assertTrue(f.suggestion.contains("focus-visible"), f.suggestion);
    }

    @Test
    void a_control_with_a_real_focus_ring_passes(PageProbe probe) {
        List<Finding> findings = auditFragment(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <style>
                    #good{outline:none;border:1px solid #333;background:#eee;
                          padding:12px 20px;font-size:16px}
                    #good:focus{outline:3px solid #0b4fd4;outline-offset:2px}
                  </style>
                  <button id="good">Save</button>
                </body>""");
        assertTrue(findings.isEmpty(), "a 3px ring should be visible, but got: " + findings);
    }

    @Test
    void a_ring_too_faint_to_notice_is_flagged_less_severely(PageProbe probe) {
        List<Finding> findings = auditFragment(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <style>
                    #faint{outline:none;border:1px solid #333;background:#eee;
                           padding:12px 20px;font-size:16px}
                    #faint:focus{outline:1px solid #fcfcfc}
                  </style>
                  <button id="faint">Save</button>
                </body>""");
        assertEquals(1, findings.size(), findings.toString());
        assertTrue(findings.get(0).severity.atLeast(Severity.MEDIUM));
    }

    @Test
    void the_browser_default_ring_passes(PageProbe probe) {
        List<Finding> findings = auditFragment(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <button style="padding:12px 20px;font-size:16px">Save</button>
                  <input style="padding:12px;font-size:16px">
                </body>""");
        assertTrue(findings.isEmpty(), "the platform default is visible, but got: " + findings);
    }
}
