package com.calpano.uxlens;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.interactive.StateAudit;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focus and hover, checked by reaching for the control for real and looking at the pixels.
 *
 * <p>No stylesheet analysis can answer this: a ring can be declared and then painted
 * outside a clipping ancestor, or lost to specificity three stylesheets later.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Interaction states are checked in the pixels")
class StateAuditTest {

    private List<Finding> audit(PageProbe probe, String body, Set<StateAudit.State> states,
                                Viewport viewport) {
        try (BrowserContext ctx = probe.newContext(viewport)) {
            Page page = ctx.newPage();
            page.setContent(body);
            var capture = probe.measureCurrentState(page, viewport);
            return StateAudit.check(page, capture.snapshot(), viewport, states);
        }
    }

    private List<Finding> focus(PageProbe probe, String body) {
        return audit(probe, body, EnumSet.of(StateAudit.State.FOCUS), Viewport.DESKTOP);
    }

    private List<Finding> hover(PageProbe probe, String body) {
        return audit(probe, body, EnumSet.of(StateAudit.State.HOVER), Viewport.DESKTOP);
    }

    // ---------------------------------------------------------------- focus

    @Test
    void a_control_with_outline_none_and_nothing_else_is_flagged(PageProbe probe) {
        List<Finding> findings = focus(probe, """
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
        assertTrue(focus(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <style>
                    #good{outline:none;border:1px solid #333;background:#eee;
                          padding:12px 20px;font-size:16px}
                    #good:focus{outline:3px solid #0b4fd4;outline-offset:2px}
                  </style>
                  <button id="good">Save</button>
                </body>""").isEmpty(), "a 3px ring should be visible");
    }

    @Test
    void a_ring_too_faint_to_notice_is_flagged_less_severely(PageProbe probe) {
        List<Finding> findings = focus(probe, """
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
        assertTrue(focus(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <button style="padding:12px 20px;font-size:16px">Save</button>
                  <input style="padding:12px;font-size:16px">
                </body>""").isEmpty(), "the platform default is visible");
    }

    // ---------------------------------------------------------------- hover

    @Test
    void a_control_that_does_nothing_on_hover_is_flagged(PageProbe probe) {
        List<Finding> findings = hover(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <a id="inert" href="#x" style="background:#0b4fd4;color:#fff;text-decoration:none;
                     padding:12px 20px;font-size:16px;display:inline-block">Continue</a>
                </body>""");
        assertEquals(1, findings.size(), findings.toString());
        assertEquals("hover-not-visible", findings.get(0).ruleId);
        assertEquals(Severity.MEDIUM, findings.get(0).severity);
        assertTrue(findings.get(0).suggestion.contains(":hover"), findings.get(0).suggestion);
    }

    @Test
    void a_control_with_a_hover_style_passes(PageProbe probe) {
        assertTrue(hover(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <style>
                    #live{background:#0b4fd4;color:#fff;text-decoration:none;padding:12px 20px;
                          font-size:16px;display:inline-block}
                    #live:hover{background:#06308a}
                  </style>
                  <a id="live" href="#x">Continue</a>
                </body>""").isEmpty(), "a background change on hover is plainly visible");
    }

    @Test
    void hover_is_not_demanded_of_a_touch_viewport(PageProbe probe) {
        String body = """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <a id="inert" href="#x" style="background:#0b4fd4;color:#fff;text-decoration:none;
                     padding:12px 20px;font-size:16px;display:inline-block">Continue</a>
                </body>""";
        assertTrue(audit(probe, body, EnumSet.of(StateAudit.State.HOVER), Viewport.MOBILE).isEmpty(),
                "a phone has no pointer, so it cannot have a hover affordance");
    }

    @Test
    void both_states_are_reported_separately_for_one_control(PageProbe probe) {
        List<Finding> findings = audit(probe, """
                <body style="margin:0;padding:40px;background:#fff;font-family:system-ui">
                  <a id="dead" href="#x" style="outline:none;background:#0b4fd4;color:#fff;
                     text-decoration:none;padding:12px 20px;font-size:16px;display:inline-block">Go</a>
                </body>""", EnumSet.allOf(StateAudit.State.class), Viewport.DESKTOP);
        assertEquals(Set.of("focus-not-visible", "hover-not-visible"),
                findings.stream().map(f -> f.ruleId).collect(java.util.stream.Collectors.toSet()),
                findings.toString());
    }
}
