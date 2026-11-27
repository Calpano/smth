package com.calpano.uxlens;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.analysis.Prominence;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.perception.Saliency;
import com.calpano.uxlens.report.Artifacts;
import com.calpano.uxlens.rules.RuleSet;
import com.microsoft.playwright.options.ColorScheme;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a design review of a page: measure, analyse, check, and -- optionally -- render the
 * artifacts that let a human or an agent see what was measured.
 *
 * <pre>{@code
 * try (Auditor auditor = Auditor.create()) {
 *     Report report = auditor
 *         .viewports(Viewport.DESKTOP, Viewport.MOBILE)
 *         .artifactDir(Path.of("target/uxlens"))
 *         .audit("https://example.com");
 *     report.findings(Severity.MEDIUM).forEach(System.out::println);
 * }
 * }</pre>
 */
public class Auditor implements AutoCloseable {

    /** How many prominence ranks to record in the report. */
    private static final int PROMINENCE_DEPTH = 15;
    /** How many fixations to predict. */
    private static final int FIXATIONS = 6;

    private final PageProbe probe;
    private final boolean ownsProbe;
    private List<Viewport> viewports = Viewport.defaults();
    private Config config = Config.defaults();
    private RuleSet ruleSet = RuleSet.standard();
    private Path artifactDir;
    private double gridStep = 8;
    private List<ColorScheme> colorSchemes = List.of(ColorScheme.LIGHT);

    private Auditor(PageProbe probe, boolean ownsProbe) {
        this.probe = probe;
        this.ownsProbe = ownsProbe;
    }

    /** Launches its own browser; close the auditor to shut it down. */
    public static Auditor create() { return new Auditor(PageProbe.launch(), true); }

    /** Reuses a probe you own. Closing this auditor leaves the probe open. */
    public static Auditor using(PageProbe probe) { return new Auditor(probe, false); }

    public Auditor viewports(Viewport... vs) { this.viewports = List.of(vs); return this; }

    public Auditor viewports(List<Viewport> vs) { this.viewports = List.copyOf(vs); return this; }

    public Auditor config(Config config) { this.config = config; return this; }

    public Auditor rules(RuleSet ruleSet) { this.ruleSet = ruleSet; return this; }

    /** Where to write screenshots and overlays. Nothing is written when unset. */
    public Auditor artifactDir(Path dir) { this.artifactDir = dir; return this; }

    /** Grid spacing for the grid overlay, in CSS px. Default 8. */
    public Auditor gridStep(double step) { this.gridStep = step; return this; }

    /**
     * Colour schemes to audit, each viewport once per scheme. Default light only.
     *
     * <p>Auditing both and comparing is the cheapest way to catch a dark mode that was
     * bolted on: see {@link Report#schemeParity()}.
     */
    public Auditor colorSchemes(ColorScheme... schemes) {
        return colorSchemes(List.of(schemes));
    }

    public Auditor colorSchemes(List<ColorScheme> schemes) {
        if (schemes.isEmpty()) throw new IllegalArgumentException("At least one colour scheme");
        this.colorSchemes = List.copyOf(schemes);
        return this;
    }

    public Config config() { return config; }

    /** Audits a URL at every configured viewport and colour scheme. */
    public Report audit(String url) {
        return run(url, (v, scheme) -> probe.capture(url, v, scheme));
    }

    /** Audits an HTML string -- for component-level tests. */
    public Report auditHtml(String html) {
        return run("inline-html", (v, scheme) -> probe.captureHtml(html, v, scheme));
    }

    /** Captures one viewport in one colour scheme. */
    private interface Capturer {
        PageProbe.Capture capture(Viewport viewport, ColorScheme scheme);
    }

    private Report run(String label, Capturer capturer) {
        Report report = new Report();
        report.url = label;
        report.generatedAt = Instant.now().toString();

        for (Viewport viewport : viewports) {
            for (ColorScheme scheme : colorSchemes) {
                runOne(report, capturer, viewport, scheme);
            }
        }
        return report;
    }

    private void runOne(Report report, Capturer capturer, Viewport viewport, ColorScheme scheme) {
        PageProbe.Capture capture = capturer.capture(viewport, scheme);
        Analysis analysis = Analysis.of(capture, config);
        boolean dark = scheme == ColorScheme.DARK;
        String name = dark ? viewport.name() + "-dark" : viewport.name();

        Report.ViewportResult vr = describe(analysis, ruleSet, viewport.name(), name,
                dark ? "dark" : "light");
        if (report.url.equals("inline-html")) report.url = analysis.snapshot().url;

        if (artifactDir != null) {
            vr.artifacts.putAll(Artifacts.write(analysis, vr.findings,
                    analysis.fixations(FIXATIONS), artifactDir.resolve(name), artifactDir,
                    gridStep));
        }
        report.viewports.add(vr);
    }

    /**
     * Everything one capture has to say, as a report section: the findings, the prominence
     * ranking, the predicted gaze path, the inferred tokens and the page profile.
     *
     * <p>Static and browser-free on purpose. A stored capture can be put through exactly
     * the code an audit runs, which is what lets the census check that these measurements
     * really produce values rather than quietly returning zero.
     */
    public static Report.ViewportResult describe(Analysis analysis, RuleSet ruleSet,
                                                 String viewportBase, String viewportName,
                                                 String colorScheme) {
        return describe(analysis, ruleSet.run(analysis), viewportBase, viewportName, colorScheme);
    }

    /** As above, for a caller that has already run the rules and does not want them run twice. */
    public static Report.ViewportResult describe(Analysis analysis, List<Finding> findings,
                                                 String viewportBase, String viewportName,
                                                 String colorScheme) {
        // One gaze path, used by both the report and the overlay: an artifact that
        // disagrees with the table it illustrates is worse than no artifact.
        List<Saliency.Fixation> fixations = analysis.fixations(FIXATIONS);

        Report.ViewportResult vr = new Report.ViewportResult();
        vr.viewportBase = viewportBase;
        vr.colorScheme = colorScheme;
        vr.viewportName = viewportName;
        vr.width = analysis.snapshot().viewportWidth();
        vr.height = analysis.snapshot().viewportHeight();
        vr.elementCount = analysis.snapshot().elements.size();
        vr.truncated = analysis.snapshot().truncated;
        vr.findings = findings;
        vr.prominence = prominenceOf(analysis);
        vr.focalPoints = focalPointsOf(analysis, fixations);
        vr.inferredTokens.put("spacing", analysis.spacing().asMap());
        vr.inferredTokens.put("type", analysis.type().asMap());
        vr.profile.putAll(com.calpano.uxlens.analysis.PageProfile.of(analysis).asMap());
        return vr;
    }

    private static List<Report.Prominence> prominenceOf(Analysis analysis) {
        List<Report.Prominence> out = new ArrayList<>();
        for (Prominence.Scored s : analysis.prominence().top(PROMINENCE_DEPTH)) {
            Report.Prominence p = new Report.Prominence();
            p.rank = s.rank;
            p.elementId = s.elem.id;
            p.selector = s.elem.selector;
            p.tag = s.elem.tag;
            p.text = s.elem.hasOwnText() ? s.elem.text : s.elem.ariaLabel;
            p.score = s.score;
            p.areaPx = Math.round(s.elem.area());
            p.fontSizePx = s.elem.fontSize;
            p.fontWeight = s.elem.fontWeight;
            p.contrastRatio = Math.round((s.contrastRatio + 1) * 100) / 100.0;
            p.whitespaceMarginPx = Math.round(s.isolationPx);
            p.viewportYPercent = Math.round(s.elem.y / Math.max(1.0, analysis.snapshot().viewportHeight()) * 1000) / 10.0;
            p.interactive = s.elem.interactive;
            p.aboveFold = s.aboveFold;
            p.components = s.components;
            out.add(p);
        }
        return out;
    }

    private static List<Report.FocalPoint> focalPointsOf(Analysis analysis,
                                                        List<Saliency.Fixation> fixations) {
        double scale = analysis.scale();
        List<Report.FocalPoint> out = new ArrayList<>();
        for (Saliency.Fixation f : fixations) {
            Report.FocalPoint fp = new Report.FocalPoint();
            fp.order = f.order();
            fp.x = Math.round(f.x() / scale);
            fp.y = Math.round(f.y() / scale);
            fp.strength = f.strength();
            // name what is under the fixation, so the point means something
            analysis.contentNear(fp.x, fp.y).ifPresent(e -> {
                fp.elementId = e.id;
                fp.selector = e.selector;
                fp.text = e.hasOwnText() ? e.text : e.ariaLabel;
            });
            out.add(fp);
        }
        return out;
    }

    @Override public void close() {
        if (ownsProbe) probe.close();
    }
}
