package com.calpano.uxlens.report;

import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Report rendering")
class ReportRenderingTest {

    private Report report;

    @BeforeEach
    void buildReport() {
        report = new Report();
        report.url = "https://example.test/landing?a=1&b=2";
        report.generatedAt = "2026-01-01T00:00:00Z";

        Report.ViewportResult v = new Report.ViewportResult();
        v.viewportName = "desktop";
        v.width = 1440;
        v.height = 900;
        v.elementCount = 42;

        Finding f = new Finding("text-contrast", Severity.HIGH,
                "p \"<script>\" has a contrast ratio of 1.92:1 against its background.");
        f.suggestion = "Use #707070 or darker.";
        f.selectors.add("body > p");
        f.elements.add(7);
        f.focus = new Rect(10, 20, 300, 40);
        f.data.put("ratio", 1.92);
        v.findings.add(f);

        Report.Prominence p = new Report.Prominence();
        p.rank = 1;
        p.elementId = 3;
        p.selector = "h1";
        p.tag = "h1";
        p.text = "Ship | faster";
        p.score = 0.9123;
        p.fontSizePx = 40;
        p.fontWeight = 700;
        p.contrastRatio = 18.1;
        p.whitespaceMarginPx = 24;
        p.viewportYPercent = 18;
        p.aboveFold = true;
        v.prominence.add(p);

        Report.FocalPoint fp = new Report.FocalPoint();
        fp.order = 1;
        fp.x = 416;
        fp.y = 203;
        fp.strength = 1.0;
        fp.selector = "h1";
        fp.text = "Ship faster";
        v.focalPoints.add(fp);

        v.inferredTokens.put("spacing", java.util.Map.of("baseUnit", 8, "adherence", 0.98));
        v.artifacts.put("squint", "desktop/squint.png");
        report.viewports.add(v);
    }

    @Test
    void severity_counts_cover_every_level() {
        var counts = report.countsBySeverity();
        assertEquals(4, counts.size());
        assertEquals(1L, counts.get(Severity.HIGH));
        assertEquals(0L, counts.get(Severity.LOW));
    }

    @Test
    void thresholds_filter_by_severity() {
        assertEquals(1, report.findings(Severity.HIGH).size());
        assertEquals(1, report.findings(Severity.LOW).size());
        assertFalse(report.isClean(Severity.HIGH));
    }

    @Test
    void json_round_trips_through_gson() {
        String json = JsonReport.render(report);
        assertTrue(json.contains("\"ruleId\": \"text-contrast\""), json);
        assertTrue(json.contains("\"ratio\": 1.92"), json);
        Report back = new com.google.gson.Gson().fromJson(json, Report.class);
        assertEquals(report.url, back.url);
        assertEquals(1, back.viewports.get(0).findings.size());
        assertEquals(Severity.HIGH, back.viewports.get(0).findings.get(0).severity);
    }

    @Test
    void html_escapes_text_that_came_from_the_page() {
        String html = HtmlReport.render(report);
        assertTrue(html.contains("&lt;script&gt;"), "page text must be escaped");
        assertFalse(html.contains("<script>"), "an unescaped tag would execute in the report");
        assertTrue(html.contains("example.test/landing?a=1&amp;b=2"));
        assertTrue(html.contains("desktop/squint.png"));
        assertTrue(html.startsWith("<!doctype html>"));
    }

    @Test
    void html_is_written_beside_the_images_it_references(@TempDir Path dir) throws IOException {
        Path file = HtmlReport.write(report, dir);
        assertEquals(dir.resolve("report.html"), file);
        assertTrue(Files.readString(file).contains("desktop/squint.png"),
                "image paths are relative, so the report must sit in the artifact directory");
    }

    @Test
    void the_briefing_states_the_measurements_and_names_the_images() {
        String md = AgentBriefing.render(report);
        assertTrue(md.contains("# Visual QA briefing"));
        assertTrue(md.contains("desktop/squint.png"), "must tell the agent which image to look at");
        assertTrue(md.contains("blurred and desaturated"), "and what that image is for");
        assertTrue(md.contains("`body > p`"), "must name the element by selector");
        assertTrue(md.contains("Use #707070 or darker."), "must carry the fix through");
        assertTrue(md.contains("## What to check, and how"), "must end with the checklist");
        assertTrue(md.contains("Predicted gaze order"));
        assertTrue(md.contains("| 1 |"), "the prominence table must have rows");
    }

    @Test
    void the_briefing_can_be_filtered_to_the_findings_worth_an_agent_s_time() {
        assertTrue(AgentBriefing.render(report, Severity.HIGH).contains("text-contrast"));
        Report empty = new Report();
        empty.url = "about:blank";
        empty.generatedAt = "now";
        Report.ViewportResult v = new Report.ViewportResult();
        v.viewportName = "desktop";
        empty.viewports.add(v);
        assertTrue(AgentBriefing.render(empty, Severity.MEDIUM).contains("None at or above MEDIUM"));
    }

    @Test
    void json_is_written_to_the_path_given(@TempDir Path dir) throws IOException {
        Path file = JsonReport.write(report, dir.resolve("nested/report.json"));
        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("text-contrast"));
    }
}
