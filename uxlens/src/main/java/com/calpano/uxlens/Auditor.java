package com.calpano.uxlens;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.analysis.Prominence;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.perception.Overlays;
import com.calpano.uxlens.perception.Saliency;
import com.calpano.uxlens.perception.SquintTest;
import com.calpano.uxlens.rules.RuleSet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
    /** Elements to label in the Set-of-Mark overlay. More than this is unreadable. */
    private static final int MARKS = 60;

    private final PageProbe probe;
    private final boolean ownsProbe;
    private List<Viewport> viewports = Viewport.defaults();
    private Config config = Config.defaults();
    private RuleSet ruleSet = RuleSet.standard();
    private Path artifactDir;
    private double gridStep = 8;

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

    public Config config() { return config; }

    /** Audits a URL at every configured viewport. */
    public Report audit(String url) {
        return run(url, v -> probe.capture(url, v));
    }

    /** Audits an HTML string -- for component-level tests. */
    public Report auditHtml(String html) {
        return run("inline-html", v -> probe.captureHtml(html, v));
    }

    private Report run(String label, java.util.function.Function<Viewport, PageProbe.Capture> capturer) {
        Report report = new Report();
        report.url = label;
        report.generatedAt = Instant.now().toString();

        for (Viewport viewport : viewports) {
            PageProbe.Capture capture = capturer.apply(viewport);
            Analysis analysis = Analysis.of(capture, config);
            List<Finding> findings = ruleSet.run(analysis);

            // One gaze path, used by both the report and the overlay: an artifact that
            // disagrees with the table it illustrates is worse than no artifact.
            List<Saliency.Fixation> fixations = analysis.fixations(FIXATIONS);

            Report.ViewportResult vr = new Report.ViewportResult();
            vr.viewportName = viewport.name();
            vr.width = viewport.width();
            vr.height = viewport.height();
            vr.elementCount = analysis.snapshot().elements.size();
            vr.truncated = analysis.snapshot().truncated;
            vr.findings = findings;
            vr.prominence = prominenceOf(analysis);
            vr.focalPoints = focalPointsOf(analysis, fixations);
            vr.inferredTokens.put("spacing", analysis.spacing().asMap());
            vr.inferredTokens.put("type", analysis.type().asMap());
            if (report.url.equals("inline-html")) report.url = analysis.snapshot().url;

            if (artifactDir != null) {
                vr.artifacts.putAll(writeArtifacts(analysis, findings, fixations, viewport));
            }
            report.viewports.add(vr);
        }
        return report;
    }

    private List<Report.Prominence> prominenceOf(Analysis analysis) {
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

    private List<Report.FocalPoint> focalPointsOf(Analysis analysis, List<Saliency.Fixation> fixations) {
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

    /** Writes the screenshot and every overlay for one viewport; returns kind -> relative path. */
    private java.util.Map<String, String> writeArtifacts(Analysis analysis, List<Finding> findings,
                                                         List<Saliency.Fixation> fixations,
                                                         Viewport viewport) {
        java.util.Map<String, String> paths = new java.util.LinkedHashMap<>();
        try {
            Path dir = artifactDir.resolve(viewport.name());
            Files.createDirectories(dir);
            double scale = analysis.scale();
            BufferedImage full = analysis.fullPageImage().orElseThrow();
            BufferedImage fold = analysis.foldImage().orElseThrow();

            put(paths, dir, "screenshot", full);
            put(paths, dir, "fold", fold);
            put(paths, dir, "squint", SquintTest.squint(fold));
            analysis.foldSaliency().ifPresent(sal ->
                    put(paths, dir, "saliency", Overlays.saliencyOver(fold, sal, 0.55f)));
            if (!fixations.isEmpty()) put(paths, dir, "scanpath", Overlays.scanpath(fold, fixations, scale));
            put(paths, dir, "grid", Overlays.grid(fold, gridStep, scale));

            List<Elem> marks = VisualGroups.content(analysis.snapshot()).stream()
                    .filter(e -> e.rect().intersects(analysis.snapshot().viewportRect()))
                    .limit(MARKS).toList();
            put(paths, dir, "set-of-mark", Overlays.setOfMark(fold, marks, scale));

            if (!findings.isEmpty()) {
                put(paths, dir, "findings", Overlays.findings(full, findings, scale));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write artifacts to " + artifactDir, e);
        }
        return paths;
    }

    private void put(java.util.Map<String, String> paths, Path dir, String kind, BufferedImage img) {
        Path file = dir.resolve(kind + ".png");
        try {
            ImageIO.write(img, "png", file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
        paths.put(kind, artifactDir.relativize(file).toString());
    }

    @Override public void close() {
        if (ownsProbe) probe.close();
    }
}
