package com.calpano.uxlens.corpus;

import com.calpano.uxlens.Auditor;
import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.analysis.Consistency;
import com.calpano.uxlens.analysis.PageProfile;
import com.calpano.uxlens.analysis.Prominence;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Apca;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Rgba;
import com.calpano.uxlens.perception.Gray;
import com.calpano.uxlens.perception.Overlays;
import com.calpano.uxlens.perception.Saliency;
import com.calpano.uxlens.perception.SquintTest;
import com.calpano.uxlens.perception.VisualDiff;
import com.calpano.uxlens.report.AgentBriefing;
import com.calpano.uxlens.report.Artifacts;
import com.calpano.uxlens.report.HtmlReport;
import com.calpano.uxlens.report.JsonReport;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleSet;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Whether every rule and every measurement in this tool actually works.
 *
 * <p>Precision answers whether a finding was right. It cannot answer the question that
 * kept biting: did the check run at all? A rule that never got its turn reports nothing,
 * and so does a rule on a page with nothing wrong, and from outside the two are the same
 * silence. The pixel-reading rules sat switched off for the whole life of the corpus and
 * looked, the entire time, like admirable restraint.
 *
 * <p>So this counts turns rather than findings. For every rule it records how many
 * captures it ran on, how many it was skipped on and why, and whether it ever had
 * anything to say. For every measurement it records whether a finite value came out, and
 * what range of values -- a figure that is the same on twenty-two different pages is not
 * measuring those pages.
 *
 * <p>The expectations are deliberately weak. The census asks whether a measurement is
 * alive, not whether it is right; a wrong-but-varying number passes here and is caught by
 * the labels, the contracts or the fixtures. What it does catch is the whole class of
 * fault that those three cannot see, because a dead measurement breaks no test.
 */
public final class Census {

    private Census() { }

    // ----------------------------------------------------------------- rules

    /** What became of one rule across the corpus. */
    public record RuleRow(String ruleId, boolean needsPixels, boolean needsLiteralText,
                          int firedOn, int silentOn, int threwOn, int noPixels, int redactedOut,
                          int disabled, int findings, List<String> examples, String error) {

        public int ran() { return firedOn + silentOn + threwOn; }

        public int skipped() { return noPixels + redactedOut + disabled; }

        public Verdict verdict() {
            if (threwOn > 0) return Verdict.THREW;
            if (ran() == 0) return Verdict.NEVER_RAN;
            if (findings == 0) return Verdict.SILENT_EVERYWHERE;
            return Verdict.EXERCISED;
        }
    }

    /**
     * How much evidence there is that a rule works.
     *
     * <p>{@code SILENT_EVERYWHERE} is the honest middle: the rule ran, so it is not broken
     * in the way the pixel rules were, but nothing on these pages tripped it, so nothing
     * here says it can fire. That is what the synthetic fixtures are for.
     */
    public enum Verdict { EXERCISED, SILENT_EVERYWHERE, NEVER_RAN, THREW }

    // ---------------------------------------------------------- measurements

    /** What a live measurement should look like, stated weakly on purpose. */
    public enum Expect {
        /** Must differ between pages. A constant is not a measurement of the page. */
        VARIES,
        /** Must be above zero wherever it applies. */
        POSITIVE,
        /** Must be exactly one -- a self-check, not a measurement. */
        ONE,
        /** Any finite value will do. */
        ANY
    }

    /** One number, read once per capture. */
    public record Measure(String group, String name, Expect expect, boolean needsPixels) { }

    /**
     * What that number did across the corpus.
     *
     * @param notApplicable captures the measurement's own gate skipped -- a pixel reading
     *                  on a capture with no pixels. Expected, and not a fault.
     * @param noValue   captures where it applied and yet nothing came out. This is the
     *                  shape the pixel-rule bug had, so it is counted separately.
     * @param offenders the captures that fail the expectation, so a dead measurement points
     *                  at the page that reproduces it rather than leaving the reader to
     *                  find one
     */
    public record MeasureRow(Measure measure, int read, int notApplicable, int noValue,
                             int failed, double min, double max, double mean,
                             List<String> offenders, String error) {

        public Health health() {
            if (failed > 0) return Health.FAILED;
            if (read == 0) return Health.DEAD;
            return switch (measure.expect()) {
                case VARIES -> min == max ? Health.FLAT : Health.OK;
                case POSITIVE -> min <= 0 ? Health.ZERO : Health.OK;
                case ONE -> (min == 1 && max == 1) ? Health.OK : Health.WRONG;
                case ANY -> Health.OK;
            };
        }
    }

    /**
     * {@code DEAD} never produced a value; {@code FLAT} produced the same one everywhere;
     * {@code ZERO} produced nothing above zero; {@code WRONG} failed its own self-check;
     * {@code FAILED} threw.
     */
    public enum Health { OK, FLAT, ZERO, WRONG, DEAD, FAILED }

    // ------------------------------------------------------------- captures

    /**
     * Whether a pinned capture is the page it claims to be.
     *
     * <p>Two captures in this corpus turned out to be rate-limit error pages, pinned months
     * ago under the names of the Arabic and Japanese Wikipedia and counted ever since as
     * the project's right-to-left and CJK coverage. Nothing noticed, because a snapshot of
     * an error page is a perfectly valid snapshot. Every measurement read it, every label
     * judged findings on it, and the coverage it was there to provide never existed.
     *
     * <p>The signals have to survive redaction, so they are structural rather than textual:
     * an error page is small, flat, linkless and does not scroll. None of them is damning
     * alone -- a map application does not scroll either -- so a capture is called suspect
     * only when several agree, and the census prints the counts rather than just a verdict.
     */
    public record CaptureRow(String key, String url, int elements, int links, int headings,
                             int interactive, boolean scrolls, boolean hasPixels,
                             boolean redacted, boolean declaredSparse, List<String> flags) {

        /**
         * Suspect when several signals agree -- unless the site says it really is this
         * small, which only the person who added it can know.
         */
        public boolean isSuspect() { return flags.size() >= 2 && !declaredSparse; }
    }

    /** Elements below which a page of content is not plausibly a page of content. */
    private static final int THIN_PAGE = 30;

    /**
     * Elements below which nothing else needs to agree.
     *
     * <p>The Wikimedia rate-limit page got in a third time. At desktop width it is thin and
     * does not scroll, which is two signals and enough; at phone width the same page
     * reflows to 2121px tall, so "does not scroll" no longer applied and one signal was not
     * enough to reject it. It sat in the corpus as the English Wikipedia until a measurement
     * reported zero peer groups on it.
     *
     * <p>Twenty-two elements is not a web page. A page that really is that small says so
     * with {@code "sparse": true}, which is a decision somebody writes down, and that is the
     * right place for the judgement rather than in a conjunction of weak signals.
     */
    private static final int BARELY_A_PAGE = 25;

    /** Links below which a page is not part of a site. */
    private static final int FEW_LINKS = 3;

    static CaptureRow inspect(CorpusStore.Entry entry, boolean declaredSparse) {
        return inspect(entry.key(), entry.url(), entry.snapshot(), entry.hasPixels(),
                declaredSparse);
    }

    /**
     * Judges a snapshot on its own, so a capture can be checked before it is pinned rather
     * than discovered to be rubbish months later.
     */
    public static CaptureRow inspect(String key, String url,
                                     com.calpano.uxlens.model.PageSnapshot snapshot,
                                     boolean hasPixels, boolean declaredSparse) {
        int links = (int) snapshot.elements.stream().filter(e -> "a".equals(e.tag)).count();
        int headings = (int) snapshot.elements.stream()
                .filter(e -> e.tag != null && e.tag.length() == 2 && e.tag.charAt(0) == 'h'
                        && Character.isDigit(e.tag.charAt(1)))
                .count();
        boolean scrolls = snapshot.scrollHeight > snapshot.viewportHeight() + 1;
        List<String> flags = new ArrayList<>();
        if (snapshot.elements.size() < THIN_PAGE) flags.add("thin");
        if (snapshot.elements.size() < BARELY_A_PAGE) flags.add("barely a page");
        if (links < FEW_LINKS) flags.add("few links");
        if (headings == 0) flags.add("no headings");
        if (!scrolls) flags.add("does not scroll");
        if (snapshot.elements.isEmpty()) flags.add("empty");
        return new CaptureRow(key, url, snapshot.elements.size(), links, headings,
                snapshot.interactiveElements().size(), scrolls, hasPixels, snapshot.redacted,
                declaredSparse, flags);
    }

    /** One capture, fully analysed, as the gauges see it. */
    public record Subject(CorpusStore.Entry entry, Analysis analysis, List<Finding> findings,
                          List<Saliency.Fixation> fixations, Report report) { }

    /** Reads one number off a capture, or null when it does not apply. */
    public interface Gauge extends Function<Subject, Double> { }

    public record Result(List<RuleRow> rules, List<MeasureRow> measures,
                         List<CaptureRow> captureRows, int capturesWithPixels,
                         int capturesRedacted, List<String> notReachable) {

        public int captures() { return captureRows.size(); }

        public List<CaptureRow> suspectCaptures() {
            return captureRows.stream().filter(CaptureRow::isSuspect).toList();
        }

        public List<RuleRow> rulesWith(Verdict verdict) {
            return rules.stream().filter(r -> r.verdict() == verdict).toList();
        }

        public List<MeasureRow> measuresWith(Health health) {
            return measures.stream().filter(m -> m.health() == health).toList();
        }

        /** True when nothing is broken: nothing threw, nothing was dead, nothing lied. */
        public boolean isHealthy() {
            return rulesWith(Verdict.THREW).isEmpty() && rulesWith(Verdict.NEVER_RAN).isEmpty()
                    && measures.stream().allMatch(m -> m.health() == Health.OK)
                    && suspectCaptures().isEmpty();
        }
    }

    // ------------------------------------------------------------------ run

    /** Runs every rule and reads every measurement on every pinned capture. */
    public static Result run(List<CorpusStore.Entry> corpus, Config config, Path scratch) {
        return run(corpus, config, scratch, java.util.Set.of());
    }

    /**
     * @param sparseSites ids of sites that have declared themselves genuinely small, so the
     *                    capture check does not keep reporting a page that is doing nothing
     *                    wrong
     */
    public static Result run(List<CorpusStore.Entry> corpus, Config config, Path scratch,
                             java.util.Set<String> sparseSites) {
        RuleSet ruleSet = RuleSet.standard();
        Map<String, int[]> tally = new LinkedHashMap<>();   // fired, silent, threw, noPix, redact, off, findings
        Map<String, List<String>> examples = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        // Built once: two separate passes over probes() could drift apart, and a census
        // whose labels do not match its readings is worse than none.
        List<Probe> probes = probes(scratch);
        List<Measure> measures = probes.stream().map(Probe::measure).toList();
        List<Gauge> gauges = probes.stream().map(Probe::gauge).toList();
        double[][] readings = new double[measures.size()][corpus.size()];
        // 0 read, 1 skipped by its own gate, 2 threw, 3 applied but produced no value.
        // Telling 1 from 3 is the whole point: the first is expected, the second is the
        // fault the pixel rules had, where something simply never produced anything.
        int[][] status = new int[measures.size()][corpus.size()];
        String[] measureErrors = new String[measures.size()];

        int withPixels = 0;
        int redactedCaptures = 0;
        List<CaptureRow> captureRows = new ArrayList<>();
        for (int c = 0; c < corpus.size(); c++) {
            CorpusStore.Entry entry = corpus.get(c);
            if (entry.hasPixels()) withPixels++;
            if (entry.snapshot().redacted) redactedCaptures++;
            captureRows.add(inspect(entry, sparseSites.contains(entry.siteId())));

            Analysis analysis = entry.hasPixels()
                    ? Analysis.ofStoredCapture(entry.snapshot(), entry.viewport(), config,
                            entry.fullPage(), entry.fold())
                    : Analysis.ofSnapshotOnly(entry.snapshot(), entry.viewport(), config);

            List<Finding> findings = new ArrayList<>();
            for (RuleSet.Execution execution : ruleSet.explain(analysis)) {
                Rule rule = execution.rule();
                int[] counts = tally.computeIfAbsent(rule.id(), k -> new int[7]);
                switch (execution.outcome()) {
                    case FIRED -> {
                        counts[0]++;
                        counts[6] += execution.findings().size();
                        examples.computeIfAbsent(rule.id(), k -> new ArrayList<>()).add(entry.key());
                    }
                    case SILENT -> counts[1]++;
                    case THREW -> {
                        counts[2]++;
                        errors.putIfAbsent(rule.id(), execution.error());
                    }
                    case SKIPPED_NO_PIXELS -> counts[3]++;
                    case SKIPPED_REDACTED -> counts[4]++;
                    case SKIPPED_DISABLED -> counts[5]++;
                }
                findings.addAll(execution.findings());
            }
            findings.sort(Comparator.comparing((Finding f) -> f.severity).reversed());

            Subject subject = subject(entry, analysis, findings);
            for (int m = 0; m < measures.size(); m++) {
                if (measures.get(m).needsPixels() && !entry.hasPixels()) {
                    status[m][c] = 1;
                    continue;
                }
                try {
                    Double value = gauges.get(m).apply(subject);
                    if (value == null || !Double.isFinite(value)) {
                        status[m][c] = 3;
                    } else {
                        readings[m][c] = value;
                    }
                } catch (RuntimeException | StackOverflowError e) {
                    status[m][c] = 2;
                    if (measureErrors[m] == null) measureErrors[m] = String.valueOf(e);
                }
            }
        }

        List<RuleRow> ruleRows = new ArrayList<>();
        for (Rule rule : ruleSet.rules()) {
            int[] n = tally.getOrDefault(rule.id(), new int[7]);
            List<String> where = examples.getOrDefault(rule.id(), List.of());
            ruleRows.add(new RuleRow(rule.id(), rule.needsPixels(), rule.needsLiteralText(),
                    n[0], n[1], n[2], n[3], n[4], n[5], n[6],
                    where.size() <= 3 ? where : where.subList(0, 3),
                    errors.get(rule.id())));
        }

        List<MeasureRow> measureRows = new ArrayList<>();
        for (int m = 0; m < measures.size(); m++) {
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0;
            int read = 0, na = 0, failed = 0, empty = 0;
            for (int c = 0; c < corpus.size(); c++) {
                switch (status[m][c]) {
                    case 1 -> na++;
                    case 2 -> failed++;
                    case 3 -> empty++;
                    default -> {
                        read++;
                        min = Math.min(min, readings[m][c]);
                        max = Math.max(max, readings[m][c]);
                        sum += readings[m][c];
                    }
                }
            }
            List<String> offenders = new ArrayList<>();
            for (int c = 0; c < corpus.size() && offenders.size() < 4; c++) {
                boolean bad = switch (status[m][c]) {
                    case 1 -> false;          // its gate said no; that is not a fault
                    case 2, 3 -> true;
                    default -> switch (measures.get(m).expect()) {
                        case POSITIVE -> readings[m][c] <= 0;
                        case ONE -> readings[m][c] != 1;
                        case VARIES, ANY -> false;
                    };
                };
                if (bad) offenders.add(corpus.get(c).key());
            }
            measureRows.add(new MeasureRow(measures.get(m), read, na, empty, failed,
                    read == 0 ? Double.NaN : min, read == 0 ? Double.NaN : max,
                    read == 0 ? Double.NaN : sum / read, offenders, measureErrors[m]));
        }

        return new Result(ruleRows, measureRows, captureRows, withPixels, redactedCaptures,
                notReachable());
    }

    private static Subject subject(CorpusStore.Entry entry, Analysis analysis,
                                  List<Finding> findings) {
        List<Saliency.Fixation> fixations = analysis.fixations(6);
        Report report = new Report();
        report.url = entry.url();
        report.generatedAt = "census";
        report.viewports.add(Auditor.describe(analysis, findings, entry.viewport().name(),
                entry.key(), entry.isDark() ? "dark" : "light"));
        return new Subject(entry, analysis, findings, fixations, report);
    }

    /**
     * What the corpus cannot reach, stated rather than left as a gap in the table.
     *
     * <p>A pinned capture is one rendered moment. Anything that has to drive the page, or
     * compare two pages, is outside what these measurements can exercise, and saying so is
     * more useful than a row of zeroes that reads like a bug.
     */
    private static List<String> notReachable() {
        return List.of(
                "StateAudit -- focus and hover states need a live page to drive. "
                        + "Exercised by the fixture tests, not by the corpus.",
                "PageProbe / probe.js -- the corpus is its output, so a capture cannot test "
                        + "the capturing. Exercised by the fixture tests against a real browser.",
                "Redaction -- applied at capture time. Every pinned capture but one is "
                        + "redacted, which is evidence it ran, not that it ran correctly.",
                "VisualDiff across two different pages -- the corpus pins pages, not "
                        + "before-and-after pairs. Exercised here against a known-identical and a "
                        + "known-changed image instead.");
    }

    // ------------------------------------------------------------- the gauges

    private record Probe(Measure measure, Gauge gauge) { }

    private static List<Probe> probes(Path scratch) {
        List<Probe> p = new ArrayList<>();

        // ---- the page as measured, no pixels needed
        geometry(p);
        tokens(p);
        contrast(p);

        // ---- the page as rendered
        pixels(p);
        overlays(p, scratch);
        reports(p, scratch);
        return p;
    }

    private static void add(List<Probe> p, String group, String name, Expect expect,
                            boolean needsPixels, Gauge gauge) {
        p.add(new Probe(new Measure(group, name, expect, needsPixels), gauge));
    }

    private static void geometry(List<Probe> p) {
        add(p, "structure", "elements", Expect.VARIES, false,
                s -> (double) s.analysis().snapshot().elements.size());
        add(p, "structure", "content elements", Expect.VARIES, false,
                s -> (double) VisualGroups.content(s.analysis().snapshot()).size());
        add(p, "structure", "major blocks", Expect.VARIES, false,
                s -> (double) VisualGroups.majorBlocks(s.analysis().snapshot()).size());
        add(p, "structure", "deepest nesting", Expect.VARIES, false, s -> {
            var snapshot = s.analysis().snapshot();
            return snapshot.elements.stream().mapToInt(e -> VisualGroups.depth(snapshot, e))
                    .max().orElse(0) + 0.0;
        });
        add(p, "structure", "rows", Expect.VARIES, false, s -> (double) s.analysis().rows().size());
        add(p, "structure", "stacks", Expect.VARIES, false,
                s -> (double) s.analysis().stacks().size());
        add(p, "structure", "interactive", Expect.POSITIVE, false,
                s -> (double) s.analysis().snapshot().interactiveElements().size());
        add(p, "structure", "above the fold", Expect.POSITIVE, false,
                s -> (double) s.analysis().snapshot().aboveFold().size());

        add(p, "prominence", "ranked", Expect.POSITIVE, false,
                s -> (double) s.analysis().prominence().ranked().size());
        add(p, "prominence", "loudest score", Expect.POSITIVE, false,
                s -> s.analysis().prominence().ranked().stream()
                        .mapToDouble(x -> x.score).max().orElse(Double.NaN));
        add(p, "prominence", "score spread", Expect.POSITIVE, false, s -> {
            var ranked = s.analysis().prominence().ranked();
            if (ranked.size() < 2) return null;
            double hi = ranked.get(0).score;
            double lo = ranked.get(ranked.size() - 1).score;
            return hi - lo;
        });
        add(p, "prominence", "primary action rank", Expect.POSITIVE, false,
                s -> s.analysis().primaryAction()
                        .map(e -> (double) s.analysis().prominence().rankOf(e.id))
                        .orElse(null));
        // Not POSITIVE: on a full-bleed map every control sits on the canvas, so the gap to
        // an unrelated element is zero for everything on the page. That is the measurement
        // working, not failing.
        add(p, "prominence", "isolation px", Expect.VARIES, false,
                s -> s.analysis().prominence().ranked().stream()
                        .mapToDouble(x -> x.isolationPx).max().orElse(Double.NaN));

        add(p, "layout", "ink coverage", Expect.VARIES, false,
                s -> PageProfile.of(s.analysis()).inkCoverage());
        add(p, "layout", "balance", Expect.VARIES, false,
                s -> PageProfile.of(s.analysis()).balance());
        add(p, "layout", "alignment points", Expect.VARIES, false,
                s -> (double) PageProfile.of(s.analysis()).alignmentPoints());
        add(p, "layout", "grid tightness", Expect.VARIES, false,
                s -> PageProfile.of(s.analysis()).gridTightness());
        add(p, "layout", "horizontal overflow px", Expect.ANY, false,
                s -> (double) s.analysis().snapshot().maxScrollX);
    }

    private static void tokens(List<Probe> p) {
        // Not POSITIVE: zero is the answer for a page with no discernible scale, and
        // saying so is better than naming a spurious one. Three captures read zero.
        add(p, "spacing", "base unit", Expect.VARIES, false,
                s -> (double) s.analysis().spacing().baseUnit());
        add(p, "spacing", "adherence", Expect.VARIES, false,
                s -> s.analysis().spacing().adherence());
        add(p, "spacing", "distinct gaps", Expect.POSITIVE, false,
                s -> (double) s.analysis().spacing().histogram().size());
        add(p, "spacing", "off-scale outliers", Expect.ANY, false,
                s -> (double) s.analysis().spacing().outliers().size());
        add(p, "spacing", "samples", Expect.POSITIVE, false,
                s -> (double) s.analysis().spacing().sampleCount());

        add(p, "type", "body size", Expect.POSITIVE, false,
                s -> s.analysis().type().bodySize());
        add(p, "type", "steps on the scale", Expect.POSITIVE, false,
                s -> (double) s.analysis().type().scale().size());
        add(p, "type", "families", Expect.POSITIVE, false,
                s -> (double) s.analysis().type().families().size());
        add(p, "type", "largest to body", Expect.VARIES, false,
                s -> s.analysis().type().largestToBodyRatio());
        add(p, "type", "near-duplicate sizes", Expect.ANY, false,
                s -> (double) s.analysis().type().nearDuplicateSizes(1.5).size());

        add(p, "consistency", "peer groups", Expect.POSITIVE, false,
                s -> (double) Consistency.peerGroups(s.analysis().snapshot()).size());
        add(p, "consistency", "largest peer group", Expect.POSITIVE, false,
                s -> Consistency.peerGroups(s.analysis().snapshot()).stream()
                        .mapToDouble(Consistency.Peers::size).max().orElse(Double.NaN));
        add(p, "consistency", "properties mined", Expect.POSITIVE, false,
                s -> (double) Consistency.properties().size());
        add(p, "consistency", "outliers", Expect.ANY, false,
                s -> (double) s.analysis().consistencyOutliers().size());
    }

    private static void contrast(List<Probe> p) {
        add(p, "colour", "text colours parsed", Expect.POSITIVE, false,
                s -> (double) s.analysis().snapshot().textElements().stream()
                        .filter(e -> e.fg() != null).count());
        add(p, "colour", "unparseable colours", Expect.ANY, false,
                s -> (double) s.analysis().snapshot().elements.stream()
                        .filter(e -> e.color != null && !e.color.isBlank())
                        .filter(e -> Rgba.parse(e.color) == null).count());
        add(p, "colour", "wcag ratio min", Expect.POSITIVE, false,
                s -> s.analysis().snapshot().textElements().stream()
                        .mapToDouble(Elem::textContrast).filter(v -> v > 0)
                        .min().orElse(Double.NaN));
        add(p, "colour", "wcag ratio max", Expect.POSITIVE, false,
                s -> s.analysis().snapshot().textElements().stream()
                        .mapToDouble(Elem::textContrast).max().orElse(Double.NaN));
        add(p, "colour", "apca |Lc| max", Expect.POSITIVE, false, s -> {
            double worst = Double.NaN;
            for (Elem e : s.analysis().snapshot().textElements()) {
                Rgba fg = e.fg(), bg = e.bg();
                if (fg == null || bg == null) continue;
                double lc = Math.abs(Apca.lightnessContrast(fg, bg));
                if (Double.isNaN(worst) || lc > worst) worst = lc;
            }
            return worst;
        });
        add(p, "colour", "distinct backgrounds", Expect.POSITIVE, false,
                s -> (double) s.analysis().snapshot().elements.stream()
                        .map(e -> e.bg()).filter(java.util.Objects::nonNull)
                        .map(Object::toString).distinct().count());
        add(p, "colour", "min effective opacity", Expect.POSITIVE, false, s -> {
            var snapshot = s.analysis().snapshot();
            return snapshot.elements.stream().mapToDouble(snapshot::effectiveOpacity)
                    .min().orElse(Double.NaN);
        });
    }

    private static void pixels(List<Probe> p) {
        add(p, "render", "colourfulness", Expect.VARIES, true,
                s -> PageProfile.of(s.analysis()).colourfulness());
        add(p, "render", "visual complexity", Expect.VARIES, true,
                s -> PageProfile.of(s.analysis()).visualComplexity());
        add(p, "render", "fold pixels", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            return (double) fold.getWidth() * fold.getHeight();
        });
        add(p, "render", "luminance mean", Expect.VARIES, true, s -> {
            Gray g = Gray.luminanceOf(s.analysis().foldImage().orElseThrow());
            return g.sum() / (g.width * (double) g.height);
        });
        add(p, "render", "luminance spread", Expect.POSITIVE, true,
                s -> spread(Gray.luminanceOf(s.analysis().foldImage().orElseThrow())));
        add(p, "render", "blur flattens", Expect.POSITIVE, true, s -> {
            Gray g = Gray.luminanceOf(s.analysis().foldImage().orElseThrow());
            double before = spread(g);
            double after = spread(g.blur(6));
            return before <= 0 ? null : 1 - after / before;
        });
        add(p, "render", "resize keeps range", Expect.POSITIVE, true, s -> {
            Gray g = Gray.luminanceOf(s.analysis().foldImage().orElseThrow());
            return spread(g.resizeTo(64, 64));
        });
        add(p, "render", "heatmap colours", Expect.POSITIVE, true, s -> {
            Gray g = Gray.luminanceOf(s.analysis().foldImage().orElseThrow()).resizeTo(96, 96);
            return (double) distinctColours(g.toHeatMap());
        });

        add(p, "saliency", "fold peak", Expect.POSITIVE, true, s -> {
            Saliency sal = s.analysis().foldSaliency().orElseThrow();
            return sal.peakIn(0, 0, sal.width(), sal.height());
        });
        add(p, "saliency", "fold mean", Expect.POSITIVE, true, s -> {
            Saliency sal = s.analysis().foldSaliency().orElseThrow();
            return sal.meanIn(0, 0, sal.width(), sal.height());
        });
        add(p, "saliency", "whole-page mean", Expect.POSITIVE, true, s -> {
            Saliency sal = s.analysis().pageSaliency().orElse(null);
            return sal == null ? null : sal.meanIn(0, 0, sal.width(), sal.height());
        });
        add(p, "saliency", "fixations predicted", Expect.POSITIVE, true,
                s -> (double) s.fixations().size());
        add(p, "saliency", "fixations land on content", Expect.POSITIVE, true, s -> {
            double scale = s.analysis().scale();
            return (double) s.fixations().stream()
                    .filter(f -> s.analysis().contentNear(f.x() / scale, f.y() / scale).isPresent())
                    .count();
        });
        add(p, "saliency", "strongest fixation", Expect.POSITIVE, true,
                s -> s.fixations().stream().mapToDouble(Saliency.Fixation::strength)
                        .max().orElse(Double.NaN));

        // At the threshold the rule itself uses. Asked at 0.5 this read zero everywhere and
        // looked broken; it was the question that was wrong. Not POSITIVE either: an empty
        // to-do app really does blur into nothing, and "no masses" is the honest reading of a
        // page with no masses.
        add(p, "squint", "weight blobs", Expect.VARIES, true,
                s -> (double) SquintTest.countWeightBlobs(
                        SquintTest.squint(s.analysis().foldImage().orElseThrow())));
        add(p, "squint", "survives the blur", Expect.POSITIVE, true,
                s -> spread(Gray.luminanceOf(
                        SquintTest.squint(s.analysis().foldImage().orElseThrow()))));
        add(p, "squint", "colour squint differs", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            return changedRatio(fold, SquintTest.squintColor(fold, 8));
        });

        add(p, "diff", "identical reads identical", Expect.ONE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            return VisualDiff.compare(fold, fold).looksIdentical() ? 1.0 : 0.0;
        });
        add(p, "diff", "blur reads as changed", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            return VisualDiff.compare(fold, SquintTest.squint(fold)).changedPixelRatio();
        });
        add(p, "diff", "ssim drops under blur", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            return 1 - VisualDiff.compare(fold, SquintTest.squint(fold)).ssim();
        });
    }

    private static void overlays(List<Probe> p, Path scratch) {
        add(p, "overlay", "grid marks the page", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            return changedRatio(fold, Overlays.grid(fold, 8, s.analysis().scale()));
        });
        add(p, "overlay", "set-of-mark labels", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            List<Elem> marks = VisualGroups.content(s.analysis().snapshot()).stream()
                    .filter(e -> e.rect().intersects(s.analysis().snapshot().viewportRect()))
                    .limit(60).toList();
            if (marks.isEmpty()) return null;
            return changedRatio(fold, Overlays.setOfMark(fold, marks, s.analysis().scale()));
        });
        add(p, "overlay", "scanpath draws", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            if (s.fixations().isEmpty()) return null;
            return changedRatio(fold, Overlays.scanpath(fold, s.fixations(), s.analysis().scale()));
        });
        add(p, "overlay", "findings are drawn", Expect.POSITIVE, true, s -> {
            BufferedImage full = s.analysis().fullPageImage().orElse(null);
            if (full == null || s.findings().isEmpty()) return null;
            return changedRatio(full, Overlays.findings(full, s.findings(), s.analysis().scale()));
        });
        add(p, "overlay", "saliency tints", Expect.POSITIVE, true, s -> {
            BufferedImage fold = s.analysis().foldImage().orElseThrow();
            Saliency sal = s.analysis().foldSaliency().orElseThrow();
            return changedRatio(fold, Overlays.saliencyOver(fold, sal, 0.55f));
        });
        add(p, "overlay", "images written", Expect.POSITIVE, true, s -> {
            if (s.analysis().fullPageImage().isEmpty()) return null;
            Path dir = scratch.resolve("artifacts").resolve(s.entry().key().replace('|', '-'));
            return (double) Artifacts.write(s.analysis(), s.findings(), s.fixations(), dir,
                    dir.getParent(), 8).size();
        });
    }

    private static void reports(List<Probe> p, Path scratch) {
        add(p, "report", "json chars", Expect.VARIES, false,
                s -> (double) JsonReport.render(s.report()).length());
        add(p, "report", "html chars", Expect.VARIES, false,
                s -> (double) HtmlReport.render(s.report()).length());
        add(p, "report", "briefing chars", Expect.VARIES, false,
                s -> (double) AgentBriefing.render(s.report()).length());
        add(p, "report", "prominence rows", Expect.POSITIVE, false,
                s -> (double) s.report().viewports.get(0).prominence.size());
        add(p, "report", "focal points", Expect.POSITIVE, true,
                s -> (double) s.report().viewports.get(0).focalPoints.size());
        add(p, "report", "profile fields", Expect.POSITIVE, false,
                s -> (double) s.report().viewports.get(0).profile.size());
        add(p, "report", "inferred tokens", Expect.POSITIVE, false,
                s -> (double) s.report().viewports.get(0).inferredTokens.size());
    }

    // ------------------------------------------------------------- utilities

    /** Standard deviation of a grey field: zero means every pixel is the same. */
    private static double spread(Gray g) {
        double mean = g.sum() / (g.width * (double) g.height);
        double sum = 0;
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                double d = g.v[y][x] - mean;
                sum += d * d;
            }
        }
        return Math.sqrt(sum / (g.width * (double) g.height));
    }

    /** Share of pixels that differ. An overlay that draws nothing scores zero. */
    private static double changedRatio(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        if (w == 0 || h == 0) return 0;
        long changed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((a.getRGB(x, y) & 0xffffff) != (b.getRGB(x, y) & 0xffffff)) changed++;
            }
        }
        return changed / (double) (w * h);
    }

    private static int distinctColours(BufferedImage img) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                seen.add(img.getRGB(x, y) & 0xffffff);
                if (seen.size() > 4096) return seen.size();
            }
        }
        return seen.size();
    }
}
