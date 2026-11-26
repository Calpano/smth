package com.calpano.uxlens.cli;

import com.calpano.uxlens.Auditor;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.interactive.FocusAudit;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.perception.VisualDiff;
import com.calpano.uxlens.report.AgentBriefing;
import com.calpano.uxlens.report.HtmlReport;
import com.calpano.uxlens.report.JsonReport;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleSet;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command line entry point.
 *
 * <pre>
 * uxlens audit URL  [--viewport desktop,mobile] [--out DIR] [--fail-on medium]
 *                   [--aaa] [--only id,...] [--skip id,...] [--grid 8] [--quiet]
 * uxlens focus URL  [--viewport desktop]
 * uxlens diff A B   [--viewport desktop] [--out DIR]
 * uxlens rules
 * </pre>
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException e) {
            System.err.println("uxlens: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("uxlens: " + e);
            System.exit(3);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            usage();
            return args.length == 0 ? 2 : 0;
        }
        String command = args[0];
        List<String> rest = List.of(args).subList(1, args.length);
        return switch (command) {
            case "audit" -> audit(rest);
            case "focus" -> focus(rest);
            case "diff" -> diff(rest);
            case "rules" -> rules();
            default -> throw new IllegalArgumentException("Unknown command '" + command
                    + "'. Try: audit, focus, diff, rules.");
        };
    }

    private static int audit(List<String> args) throws Exception {
        Args a = Args.parse(args, 1);
        String url = a.positional.get(0);
        Path out = a.path("out", null);
        Severity failOn = a.severity("fail-on", Severity.HIGH);
        boolean quiet = a.flag("quiet");

        Config config = Config.defaults();
        if (a.flag("aaa")) config.aaa();
        a.list("only").forEach(config.onlyRules::add);
        a.list("skip").forEach(config.disabledRules::add);

        Report report;
        try (Auditor auditor = Auditor.create()) {
            auditor.config(config).viewports(a.viewports());
            if (out != null) auditor.artifactDir(out);
            auditor.gridStep(a.number("grid", 8));
            report = auditor.audit(url);
        }

        if (out != null) {
            JsonReport.write(report, out.resolve("report.json"));
            HtmlReport.write(report, out);
            AgentBriefing.write(report, out.resolve("briefing.md"));
            System.out.println("Wrote " + out.resolve("report.html").toAbsolutePath());
            System.out.println("Wrote " + out.resolve("briefing.md").toAbsolutePath() + "  (for an LLM agent)");
        }
        if (!quiet) printFindings(report);

        List<Finding> blocking = report.findings(failOn);
        System.out.printf(Locale.ROOT, "%n%s: %s%n", blocking.isEmpty() ? "PASS" : "FAIL",
                summary(report, failOn));
        return blocking.isEmpty() ? 0 : 1;
    }

    private static int focus(List<String> args) {
        Args a = Args.parse(args, 1);
        String url = a.positional.get(0);
        Viewport viewport = a.viewports().get(0);
        List<Finding> findings;
        try (PageProbe probe = PageProbe.launch()) {
            findings = FocusAudit.run(probe, url, viewport);
        }
        if (findings.isEmpty()) {
            System.out.println("PASS: every focusable control shows a visible focus indicator.");
            return 0;
        }
        findings.forEach(f -> System.out.println(format(f)));
        System.out.printf("%nFAIL: %d control(s) without a usable focus indicator.%n", findings.size());
        return 1;
    }

    private static int diff(List<String> args) throws Exception {
        Args a = Args.parse(args, 2);
        Viewport viewport = a.viewports().get(0);
        Path out = a.path("out", null);
        VisualDiff.Result result;
        try (PageProbe probe = PageProbe.launch()) {
            var left = probe.capture(a.positional.get(0), viewport).fullPageImage();
            var right = probe.capture(a.positional.get(1), viewport).fullPageImage();
            if (left.getHeight() != right.getHeight()) {
                System.out.printf("Page heights differ: %dpx vs %dpx -- content height changed.%n",
                        left.getHeight(), right.getHeight());
                return 1;
            }
            result = VisualDiff.compare(left, right);
        }
        System.out.printf(Locale.ROOT,
                "changed pixels %.4f%%   ssim %.4f   layout similarity %.4f%n",
                result.changedPixelRatio() * 100, result.ssim(), result.layoutSimilarity());
        System.out.println(result.looksIdentical() ? "The two renders look identical."
                : result.layoutStable() ? "Detail changed; the layout blocks did not move."
                : "The layout moved.");
        if (out != null) {
            java.nio.file.Files.createDirectories(out);
            Path file = out.resolve("diff.png");
            ImageIO.write(result.diffImage(), "png", file.toFile());
            System.out.println("Wrote " + file.toAbsolutePath());
        }
        return result.looksIdentical() ? 0 : 1;
    }

    private static int rules() {
        for (Rule rule : RuleSet.standard().rules()) {
            System.out.printf("%-28s %s%s%n", rule.id(), rule.title(),
                    rule.needsPixels() ? "  [needs pixels]" : "");
            System.out.printf("%-28s %s%n%n", "", wrap(rule.rationale(), 88, 29));
        }
        return 0;
    }

    private static void printFindings(Report report) {
        for (Report.ViewportResult v : report.viewports) {
            System.out.printf("%n=== %s (%dx%d) -- %d elements, %d finding(s) ===%n",
                    v.viewportName, v.width, v.height, v.elementCount, v.findings.size());
            v.findings.forEach(f -> System.out.println(format(f)));
        }
    }

    private static String format(Finding f) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n[%-6s] %-26s %s", f.severity, f.ruleId, f.message));
        if (!f.selectors.isEmpty()) sb.append("\n           at: ").append(String.join(", ", f.selectors));
        if (f.suggestion != null) sb.append("\n           fix: ").append(wrap(f.suggestion, 88, 16));
        return sb.toString();
    }

    private static String summary(Report report, Severity failOn) {
        Map<Severity, Long> counts = report.countsBySeverity();
        List<String> parts = new ArrayList<>();
        for (Severity s : List.of(Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO)) {
            long n = counts.getOrDefault(s, 0L);
            if (n > 0) parts.add(n + " " + s.name().toLowerCase(Locale.ROOT));
        }
        return (parts.isEmpty() ? "nothing found" : String.join(", ", parts))
                + " (failing at " + failOn.name().toLowerCase(Locale.ROOT) + " and above)";
    }

    private static String wrap(String text, int width, int indent) {
        StringBuilder sb = new StringBuilder();
        int lineLength = indent;
        for (String word : text.split(" ")) {
            if (lineLength + word.length() > width) {
                sb.append('\n').append(" ".repeat(indent));
                lineLength = indent;
            }
            sb.append(word).append(' ');
            lineLength += word.length() + 1;
        }
        return sb.toString().stripTrailing();
    }

    private static void usage() {
        System.out.println("""
                uxlens -- automated visual and UX review of a rendered web page

                  uxlens audit URL [options]     measure, check and report
                  uxlens focus URL [options]     check keyboard focus indicators (drives the page)
                  uxlens diff URL_A URL_B [opts] compare two renders perceptually
                  uxlens rules                   list every check with its rationale

                Options
                  --viewport desktop,mobile  presets (desktop laptop tablet mobile wide) or 1440x900[@2x]
                  --out DIR                  write screenshots, overlays, report.html, report.json, briefing.md
                  --fail-on high|medium|low  exit 1 at this severity or above (default high)
                  --aaa                      hold text to WCAG AAA instead of AA
                  --only id,id               run only these rules
                  --skip id,id               skip these rules
                  --grid N                   grid overlay spacing in px (default 8)
                  --quiet                    write artifacts without printing findings

                Exit codes: 0 clean, 1 findings at or above --fail-on, 2 bad usage, 3 error.""");
    }

    /** Tiny argument parser: {@code --key value}, {@code --flag}, and positionals. */
    private record Args(List<String> positional, Map<String, String> options) {

        static Args parse(List<String> args, int requiredPositional) {
            List<String> positional = new ArrayList<>();
            Map<String, String> options = new LinkedHashMap<>();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.startsWith("--")) {
                    String key = arg.substring(2);
                    int eq = key.indexOf('=');
                    if (eq > 0) {
                        options.put(key.substring(0, eq), key.substring(eq + 1));
                    } else if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        options.put(key, args.get(++i));
                    } else {
                        options.put(key, "true");
                    }
                } else {
                    positional.add(arg);
                }
            }
            if (positional.size() < requiredPositional) {
                throw new IllegalArgumentException("Expected " + requiredPositional
                        + " argument(s), got " + positional.size() + ". Run 'uxlens --help'.");
            }
            return new Args(positional, options);
        }

        boolean flag(String key) { return "true".equals(options.get(key)); }

        List<String> list(String key) {
            String v = options.get(key);
            if (v == null || v.isBlank() || v.equals("true")) return List.of();
            return List.of(v.split(","));
        }

        Path path(String key, Path fallback) {
            String v = options.get(key);
            return v == null || v.equals("true") ? fallback : Path.of(v);
        }

        double number(String key, double fallback) {
            String v = options.get(key);
            if (v == null || v.equals("true")) return fallback;
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " needs a number, got '" + v + "'.");
            }
        }

        Severity severity(String key, Severity fallback) {
            String v = options.get(key);
            if (v == null || v.equals("true")) return fallback;
            try {
                return Severity.valueOf(v.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("--" + key + " must be one of high, medium, low, "
                        + "info; got '" + v + "'.");
            }
        }

        List<Viewport> viewports() {
            List<String> specs = list("viewport");
            if (specs.isEmpty()) return Viewport.defaults();
            return specs.stream().map(Viewport::parse).toList();
        }
    }
}
