package com.calpano.uxlens.report;

import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The report as a page a human reads: findings beside the images they are about.
 *
 * <p>Written into the artifact directory so its image references resolve relatively, which
 * keeps the whole audit movable as one folder.
 */
public final class HtmlReport {

    private HtmlReport() { }

    /**
     * Writes {@code report.html} into the artifact directory.
     *
     * @param artifactDir the directory the audit wrote its images into
     */
    public static Path write(Report report, Path artifactDir) throws IOException {
        Files.createDirectories(artifactDir);
        Path file = artifactDir.resolve("report.html");
        Files.writeString(file, render(report), StandardCharsets.UTF_8);
        return file;
    }

    public static String render(Report report) {
        StringBuilder h = new StringBuilder();
        Map<Severity, Long> counts = report.countsBySeverity();

        h.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Visual QA</title>").append(css()).append("</head><body>\n");

        h.append("<header><h1>Visual QA</h1><p class=\"url\">").append(esc(report.url)).append("</p>")
                .append("<p class=\"meta\">").append(esc(report.generatedAt)).append("</p>")
                .append("<ul class=\"tally\">");
        for (Severity s : List.of(Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO)) {
            h.append("<li class=\"sev-").append(s.name().toLowerCase()).append("\"><b>")
                    .append(counts.getOrDefault(s, 0L)).append("</b> ").append(s.name().toLowerCase())
                    .append("</li>");
        }
        h.append("</ul></header>\n<main>");

        for (Report.ViewportResult v : report.viewports) {
            h.append("<section><h2>").append(esc(v.viewportName)).append(" <span class=\"dim\">")
                    .append(v.width).append("x").append(v.height).append(" - ")
                    .append(v.elementCount).append(" elements</span></h2>");

            if (!v.artifacts.isEmpty()) {
                h.append("<div class=\"shots\">");
                for (Map.Entry<String, String> e : v.artifacts.entrySet()) {
                    h.append("<figure><a href=\"").append(esc(e.getValue())).append("\">")
                            .append("<img loading=\"lazy\" src=\"").append(esc(e.getValue()))
                            .append("\" alt=\"").append(esc(e.getKey())).append("\"></a>")
                            .append("<figcaption>").append(esc(e.getKey())).append("</figcaption></figure>");
                }
                h.append("</div>");
            }

            h.append("<h3>Findings</h3>");
            if (v.findings.isEmpty()) {
                h.append("<p class=\"clean\">Nothing found.</p>");
            } else {
                h.append("<table class=\"findings\"><thead><tr><th>Severity</th><th>Rule</th>")
                        .append("<th>Finding</th><th>Fix</th></tr></thead><tbody>");
                for (Finding f : v.findings) {
                    h.append("<tr class=\"sev-").append(f.severity.name().toLowerCase()).append("\">")
                            .append("<td><span class=\"pill\">").append(f.severity.name().toLowerCase())
                            .append("</span></td><td><code>").append(esc(f.ruleId)).append("</code></td>")
                            .append("<td>").append(esc(f.message));
                    if (!f.selectors.isEmpty()) {
                        h.append("<div class=\"sel\">");
                        for (String s : f.selectors) h.append("<code>").append(esc(s)).append("</code> ");
                        h.append("</div>");
                    }
                    h.append("</td><td class=\"fix\">")
                            .append(f.suggestion == null ? "" : esc(f.suggestion))
                            .append("</td></tr>");
                }
                h.append("</tbody></table>");
            }

            if (!v.prominence.isEmpty()) {
                h.append("<h3>Measured visual prominence</h3>")
                        .append("<p class=\"dim\">What the page actually shouts, loudest first.</p>")
                        .append("<table class=\"prom\"><thead><tr><th>#</th><th>Element</th><th>Text</th>")
                        .append("<th>Score</th><th>Size/Weight</th><th>Contrast</th><th>Isolation</th>")
                        .append("<th>Fold</th></tr></thead><tbody>");
                for (Report.Prominence p : v.prominence) {
                    h.append("<tr><td>").append(p.rank).append("</td><td><code>").append(esc(p.selector))
                            .append("</code></td><td>").append(esc(trunc(p.text)))
                            .append("</td><td>").append(String.format("%.3f", p.score))
                            .append("</td><td>").append(String.format("%.0f / %d", p.fontSizePx, p.fontWeight))
                            .append("</td><td>").append(String.format("%.2f", p.contrastRatio))
                            .append("</td><td>").append(String.format("%.0fpx", p.whitespaceMarginPx))
                            .append("</td><td>").append(p.aboveFold ? "yes" : "no")
                            .append("</td></tr>");
                }
                h.append("</tbody></table>");
            }

            if (!v.focalPoints.isEmpty()) {
                h.append("<h3>Predicted gaze order</h3><ol class=\"gaze\">");
                for (Report.FocalPoint f : v.focalPoints) {
                    h.append("<li>(").append((int) f.x).append(", ").append((int) f.y).append(") ");
                    if (f.selector != null) h.append("<code>").append(esc(f.selector)).append("</code> ");
                    if (f.text != null && !f.text.isBlank()) h.append(esc(trunc(f.text)));
                    h.append("</li>");
                }
                h.append("</ol>");
            }

            if (!v.inferredTokens.isEmpty()) {
                h.append("<h3>Inferred design system</h3><pre class=\"tokens\">")
                        .append(esc(String.valueOf(v.inferredTokens))).append("</pre>");
            }
            h.append("</section>");
        }
        h.append("</main></body></html>\n");
        return h.toString();
    }

    private static String css() {
        return """
                <style>
                :root{--bg:#fff;--fg:#14161a;--dim:#6b7280;--line:#e5e7eb;--card:#f9fafb;
                  --high:#dc2626;--med:#ea8a00;--low:#b59f00;--info:#2d7fd3;--accent:#1d4ed8}
                @media (prefers-color-scheme:dark){:root:not([data-theme=light]){
                  --bg:#0e1013;--fg:#e8eaed;--dim:#9aa3af;--line:#272b31;--card:#171a1f;
                  --high:#f87171;--med:#fbbf24;--low:#e3d04a;--info:#7cb6ec;--accent:#8ab4ff}}
                *{box-sizing:border-box}
                body{margin:0;background:var(--bg);color:var(--fg);
                  font:15px/1.55 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
                header{padding:32px 16px 20px;border-bottom:1px solid var(--line);max-width:1280px;margin:0 auto}
                h1{margin:0 0 4px;font-size:26px;letter-spacing:-.02em}
                .url{margin:0;color:var(--accent);font-family:ui-monospace,monospace;font-size:13px;word-break:break-all}
                .meta{margin:2px 0 14px;color:var(--dim);font-size:12px}
                .tally{display:flex;gap:10px;list-style:none;padding:0;margin:0;flex-wrap:wrap}
                .tally li{padding:5px 11px;border:1px solid var(--line);border-radius:999px;font-size:12.5px;color:var(--dim)}
                .tally b{color:var(--fg);font-size:14px}
                .tally .sev-high b{color:var(--high)}.tally .sev-medium b{color:var(--med)}
                .tally .sev-low b{color:var(--low)}.tally .sev-info b{color:var(--info)}
                main{max-width:1280px;margin:0 auto;padding:0 16px 64px}
                section{padding:28px 0;border-bottom:1px solid var(--line)}
                h2{font-size:20px;margin:0 0 16px}h3{font-size:15px;margin:28px 0 8px;letter-spacing:.02em;
                  text-transform:uppercase;color:var(--dim)}
                .dim{color:var(--dim);font-weight:400;font-size:13px}
                .shots{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:12px}
                figure{margin:0;background:var(--card);border:1px solid var(--line);border-radius:8px;overflow:hidden}
                figure img{width:100%;height:150px;object-fit:cover;object-position:top;display:block}
                figcaption{padding:6px 8px;font-size:11.5px;color:var(--dim);font-family:ui-monospace,monospace}
                table{width:100%;border-collapse:collapse;font-size:13.5px;margin-top:6px}
                th{text-align:left;padding:7px 8px;border-bottom:1px solid var(--line);color:var(--dim);
                  font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.03em}
                td{padding:9px 8px;border-bottom:1px solid var(--line);vertical-align:top}
                code{font-family:ui-monospace,SFMono-Regular,monospace;font-size:12px;background:var(--card);
                  padding:1px 4px;border-radius:4px;word-break:break-all}
                .sel{margin-top:5px}
                .findings td:nth-child(2) code{word-break:keep-all;white-space:nowrap}
                .fix{color:var(--dim);max-width:30ch}
                .pill{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;
                  text-transform:uppercase;letter-spacing:.04em;color:#fff}
                .sev-high .pill{background:var(--high)}.sev-medium .pill{background:var(--med)}
                .sev-low .pill{background:var(--low);color:#000}.sev-info .pill{background:var(--info)}
                .clean{color:var(--dim)}
                .gaze{font-size:13.5px;color:var(--fg);padding-left:22px}
                .tokens{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:12px;
                  font-size:12px;overflow-x:auto;color:var(--dim)}
                @media(max-width:700px){.fix{display:none}figure img{height:110px}}
                </style>""";
    }

    private static String trunc(String s) {
        if (s == null || s.isBlank()) return "-";
        String t = s.replace('\n', ' ').trim();
        return t.length() > 48 ? t.substring(0, 48) + "..." : t;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
