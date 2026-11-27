package com.calpano.uxlens.interactive;

import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timing the page's answer to a click: the Doherty threshold.
 *
 * <p>The figure is from Doherty and Thadani at IBM in 1982 -- productivity rose sharply
 * once the machine answered inside about 400ms -- and it is one of the few numbers in this
 * project that comes from watching people work rather than from a style guide.
 *
 * <p>What is tested hardest is not the stopwatch but the refusals. An audit that clicks
 * nothing and reports a confident pass is worse than no audit, and that is exactly what the
 * first version of this did on a static site: thirty-six controls, none of them clicked,
 * "every control answered inside 400ms".
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Latency: how long the page takes to admit you clicked")
class LatencyAuditTest {

    private static final String BODY =
            "margin:0;padding:32px;font:16px/1.6 system-ui;background:#fff;color:#14161a";

    @Test
    void a_slow_answer_is_timed_and_reported(PageProbe probe) throws Exception {
        Path page = page("slow", """
                <button id="go" style="width:160px;height:44px">Open the panel</button>
                <div id="panel"></div>
                <script>
                  document.getElementById('go').addEventListener('click', () => {
                    setTimeout(() => {
                      document.getElementById('panel').textContent = 'here at last';
                    }, 900);
                  });
                </script>
                """);

        LatencyAudit.Result result = LatencyAudit.audit(probe, page.toUri().toString(),
                Viewport.DESKTOP, Config.defaults(), false);

        assertTrue(result.summary().isConclusive(),
                () -> "nothing measured: " + result.summary().whyInconclusive().orElse("?"));
        assertEquals(1, result.summary().measured());
        assertTrue(result.summary().slowest() >= 800,
                "a 900ms answer should read as roughly 900ms, read " + result.summary().slowest());
        assertTrue(result.findings().stream()
                        .anyMatch(f -> f.ruleId.equals(LatencyAudit.SLOW_RULE_ID)),
                () -> "no slow finding in " + result.findings());
    }

    @Test
    void an_immediate_answer_is_not_reported(PageProbe probe) throws Exception {
        Path page = page("fast", """
                <button id="go" style="width:160px;height:44px">Open the panel</button>
                <div id="panel"></div>
                <script>
                  document.getElementById('go').addEventListener('click', () => {
                    document.getElementById('panel').textContent = 'here at once';
                  });
                </script>
                """);

        LatencyAudit.Result result = LatencyAudit.audit(probe, page.toUri().toString(),
                Viewport.DESKTOP, Config.defaults(), false);

        assertTrue(result.summary().isConclusive());
        assertTrue(result.summary().slowest() < 400, "read " + result.summary().slowest());
        assertTrue(result.findings().stream()
                        .noneMatch(f -> f.ruleId.equals(LatencyAudit.SLOW_RULE_ID)),
                () -> "an immediate answer should be silent: " + result.findings());
    }

    @Test
    void a_control_that_does_nothing_is_reported_as_doing_nothing(PageProbe probe)
            throws Exception {
        Path page = page("inert",
                "<button id=\"go\" style=\"width:160px;height:44px\">Does nothing</button>");

        LatencyAudit.Result result = LatencyAudit.audit(probe, page.toUri().toString(),
                Viewport.DESKTOP, Config.defaults(), false);

        assertEquals(1, result.summary().silent());
        assertTrue(result.findings().stream()
                        .anyMatch(f -> f.ruleId.equals(LatencyAudit.SILENT_RULE_ID)),
                () -> result.findings().toString());
        assertFalse(result.summary().isConclusive(),
                "a page where nothing responds has nothing to time");
        assertTrue(result.summary().whyInconclusive().orElseThrow().contains("changed nothing"));
    }

    @Test
    void a_restless_page_is_called_unmeasurable_rather_than_fast(PageProbe probe)
            throws Exception {
        // A page that mutates by itself gives a spuriously fast reading, because the next
        // unrelated mutation lands within milliseconds of any click.
        Path page = page("restless", """
                <button id="go" style="width:160px;height:44px">Open the panel</button>
                <div id="clock"></div>
                <script>
                  setInterval(() => {
                    document.getElementById('clock').textContent = String(Date.now());
                  }, 20);
                </script>
                """);

        LatencyAudit.Result result = LatencyAudit.audit(probe, page.toUri().toString(),
                Viewport.DESKTOP, Config.defaults(), false);

        assertFalse(result.summary().isConclusive());
        assertTrue(result.summary().mutationsPerSecond() > 4,
                "read " + result.summary().mutationsPerSecond());
        assertEquals(List.of(LatencyAudit.UNMEASURABLE_RULE_ID),
                result.findings().stream().map(f -> f.ruleId).distinct().toList());
    }

    // ------------------------------------------------------------ the refusals

    @Test
    void destructive_wording_is_never_clicked() {
        PageSnapshot s = withControls(
                button("Delete account"), button("Remove from list"), button("Sign out"),
                button("Open the panel"));
        assertEquals(List.of("Open the panel"),
                LatencyAudit.safeToClick(s).stream().map(e -> e.visibleText).toList());
    }

    @Test
    void a_form_submit_is_never_clicked() {
        Elem submit = button("Go");
        submit.type = "submit";
        Elem bare = button("Also go");
        bare.type = null;
        Elem safe = button("Open the panel");
        safe.tag = "div";
        safe.role = "button";

        // Only dangerous inside a form: a bare button reports type="submit" because that is
        // the DOM default, and submits nothing at all when there is no form around it.
        Elem form = new Elem();
        form.tag = "form";
        form.selector = "form";
        PageSnapshot s = withControls(form, submit, bare, safe);
        submit.parentId = form.id;
        bare.parentId = form.id;

        assertEquals(List.of("Open the panel"),
                LatencyAudit.safeToClick(s).stream().map(e -> e.visibleText).toList());
    }

    @Test
    void a_link_that_leaves_the_page_is_clicked_only_as_a_navigation() {
        Elem inPage = link("Open the tab", "in-page");
        Elem ownSite = link("About", "same-site");
        Elem away = link("Elsewhere", "external");
        Elem file = link("The PDF", "download");
        PageSnapshot s = withControls(inPage, ownSite, away, file);

        assertEquals(List.of("Open the tab"),
                LatencyAudit.safeToClick(s).stream().map(e -> e.visibleText).toList());
        assertEquals(List.of("About"),
                LatencyAudit.safeToFollow(s).stream().map(e -> e.visibleText).toList());
    }

    // ------------------------------------------------------------- the static half

    @Test
    void a_declared_transition_is_read_without_waiting_for_it() {
        Elem e = new Elem();
        e.transitionDuration = "0.12s, 900ms";
        assertEquals(900, e.longestTransitionMs(), 0.001,
                "what a person waits for is the slowest of the list");
        e.transitionDuration = "none";
        assertEquals(0, e.longestTransitionMs(), 0.001);
        e.transitionDuration = null;
        assertEquals(0, e.longestTransitionMs(), 0.001);
        e.transitionDuration = "0s";
        assertEquals(0, e.longestTransitionMs(), 0.001);
    }

    // ------------------------------------------------------------------ helpers

    private static Path page(String name, String body) throws Exception {
        Path file = Files.createTempDirectory("uxlens-latency").resolve(name + ".html");
        Files.writeString(file, "<!doctype html><meta charset=\"utf-8\"><title>" + name
                + "</title><body style=\"" + BODY + "\">" + body + "</body>");
        return file;
    }

    private static Elem button(String text) {
        Elem e = new Elem();
        e.tag = "button";
        e.type = "button";
        e.selector = "button";
        e.visibleText = text;
        e.text = text;
        e.accessibleName = text;
        e.actionable = true;
        e.interactive = true;
        e.w = 140;
        e.h = 44;
        e.opacity = 1;
        return e;
    }

    private static Elem link(String text, String target) {
        Elem e = button(text);
        e.tag = "a";
        e.type = null;
        e.selector = "a";
        e.linkTarget = target;
        return e;
    }

    private static PageSnapshot withControls(Elem... controls) {
        PageSnapshot s = new PageSnapshot();
        s.requestedWidth = 1440;
        s.requestedHeight = 900;
        s.scrollHeight = 900;
        s.elements = new java.util.ArrayList<>();
        int id = 0;
        for (Elem e : controls) {
            e.id = id++;
            e.y = 20 * e.id;
            if (e.parentId == 0 && e.id != 0) e.parentId = -1;
            s.elements.add(e);
        }
        return s;
    }
}
