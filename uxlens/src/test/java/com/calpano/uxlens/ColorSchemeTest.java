package com.calpano.uxlens;

import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Report;
import com.microsoft.playwright.options.ColorScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dark mode is usually bolted onto a light design, and the defects it introduces are
 * invisible to anyone reviewing the light version. Auditing both and comparing which rules
 * fired is the cheapest way to find them.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Light and dark are audited and compared")
class ColorSchemeTest {

    /** A muted grey chosen for a white page and never rechecked against the dark one. */
    private static final String BOLTED_ON_DARK_MODE = """
            <body style="margin:0"><style>
              :root{--bg:#ffffff;--ink:#14161a;--muted:#5a6270}
              @media (prefers-color-scheme:dark){
                :root{--bg:#15171b;--ink:#f2f4f7;--muted:#3a4049}
              }
              body{background:var(--bg);color:var(--ink);font:16px/1.6 system-ui;padding:48px}
              h1{font-size:40px;margin:0 0 24px}
              .muted{color:var(--muted);max-width:60ch}
            </style>
            <h1>Account settings</h1>
            <p class="muted">Secondary copy that was perfectly readable when the page was white.</p>
            </body>""";

    private static final String CONSISTENT = """
            <body style="margin:0"><style>
              :root{--bg:#ffffff;--ink:#14161a;--muted:#5a6270}
              @media (prefers-color-scheme:dark){
                :root{--bg:#15171b;--ink:#f2f4f7;--muted:#b6bdc8}
              }
              body{background:var(--bg);color:var(--ink);font:16px/1.6 system-ui;padding:48px}
              h1{font-size:40px;margin:0 0 24px}
              .muted{color:var(--muted);max-width:60ch}
            </style>
            <h1>Account settings</h1>
            <p class="muted">Secondary copy that was rechecked against the dark surface.</p>
            </body>""";

    @Test
    void each_viewport_is_audited_once_per_scheme(Auditor auditor) {
        Report report = auditor.viewports(Viewport.DESKTOP)
                .colorSchemes(ColorScheme.LIGHT, ColorScheme.DARK)
                .auditHtml(CONSISTENT);
        assertEquals(List.of("desktop", "desktop-dark"),
                report.viewports.stream().map(v -> v.viewportName).toList());
        assertEquals(List.of("light", "dark"),
                report.viewports.stream().map(v -> v.colorScheme).toList());
    }

    @Test
    void a_contrast_failure_only_in_dark_is_reported_as_a_parity_gap(Auditor auditor) {
        Report report = auditor.viewports(Viewport.DESKTOP)
                .colorSchemes(ColorScheme.LIGHT, ColorScheme.DARK)
                .config(com.calpano.uxlens.analysis.Config.defaults().only("text-contrast"))
                .auditHtml(BOLTED_ON_DARK_MODE);

        List<Report.SchemeParity> parity = report.schemeParity();
        assertEquals(1, parity.size());
        assertEquals(List.of("text-contrast"), parity.get(0).onlyInDark);
        assertTrue(parity.get(0).onlyInLight.isEmpty());
        assertTrue(!parity.get(0).isClean());
    }

    @Test
    void a_scheme_that_was_rechecked_reports_no_gap(Auditor auditor) {
        Report report = auditor.viewports(Viewport.DESKTOP)
                .colorSchemes(ColorScheme.LIGHT, ColorScheme.DARK)
                .config(com.calpano.uxlens.analysis.Config.defaults().only("text-contrast"))
                .auditHtml(CONSISTENT);
        assertTrue(report.schemeParity().get(0).isClean(),
                "both schemes should behave the same: " + report.allFindings());
    }

    @Test
    void auditing_one_scheme_produces_no_parity_comparison(Auditor auditor) {
        Report report = auditor.viewports(Viewport.DESKTOP).auditHtml(CONSISTENT);
        assertTrue(report.schemeParity().isEmpty(),
                "there is nothing to compare against a single scheme");
    }

    @Test
    void the_declared_transition_timing_survives_the_animation_freeze(Auditor auditor) {
        // The freeze has to stop transitions without erasing what the page declared --
        // otherwise transition-duration reads 0s everywhere and is unmeasurable.
        var capture = auditor.config(com.calpano.uxlens.analysis.Config.defaults())
                .viewports(Viewport.DESKTOP);
        Report report = capture.auditHtml("""
                <body style="margin:0;padding:24px"><style>
                  button{transition-duration:0.25s;padding:10px 16px;font-size:16px;cursor:pointer}
                </style><button>One</button><button>Two</button><button>Three</button></body>""");
        assertTrue(report.viewports.get(0).elementCount > 0);
    }
}
