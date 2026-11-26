package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.TypeSystem;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typography: the scale, the measure, the leading, and the ragged edge. */
public final class TypographyRules {

    private TypographyRules() { }

    public static List<Rule> all() {
        return List.of(new FlatHierarchy(), new NoisyTypeScale(), new FontFamilySprawl(),
                new Measure(), new Leading(), new Orphan(), new AllCapsRun());
    }

    /** Headings barely larger than body text. */
    public static final class FlatHierarchy implements Rule {
        @Override public String id() { return "flat-type-hierarchy"; }

        @Override public String title() { return "Type hierarchy is too flat to read as hierarchy"; }

        @Override public String rationale() {
            return "If the largest text on the page is less than about 1.5x the body size, the "
                    + "page reads as an undifferentiated wall of text. The reader cannot find the "
                    + "entry point, and skimming fails.";
        }

        @Override public List<Finding> check(Analysis a) {
            TypeSystem t = a.type();
            double ratio = t.largestToBodyRatio();
            double required = a.config().minHeadingToBodyRatio;
            if (ratio >= required || t.scale().size() < 2) return List.of();

            Finding f = finding(ratio < required * 0.85 ? Severity.MEDIUM : Severity.LOW, String.format(
                    "The largest type on the page is %.1fpx against a %.1fpx body size -- a ratio of "
                            + "%.2f, below the %.2f at which hierarchy becomes legible.",
                    t.scale().get(t.scale().size() - 1), t.bodySize(), ratio, required))
                    .with("largestSize", t.scale().get(t.scale().size() - 1))
                    .with("bodySize", t.bodySize())
                    .with("ratio", Math.round(ratio * 100) / 100.0)
                    .with("required", required)
                    .suggest(String.format("Raise the leading heading to about %.0fpx, or lower the "
                            + "body size. A modular scale (1.25 or 1.333 per step) gives steps that "
                            + "stay distinguishable.", t.bodySize() * 2));
            a.snapshot().dominantHeading().ifPresent(f::about);
            return List.of(f);
        }
    }

    /** Too many sizes, or sizes too close together to read as different. */
    public static final class NoisyTypeScale implements Rule {
        @Override public String id() { return "noisy-type-scale"; }

        @Override public String title() { return "Type scale has too many or near-duplicate steps"; }

        @Override public String rationale() {
            return "Two sizes 1px apart are not two levels of hierarchy, they are an accident. "
                    + "A page using fifteen sizes has no scale -- it has fifteen decisions, each "
                    + "made separately.";
        }

        @Override public List<Finding> check(Analysis a) {
            TypeSystem t = a.type();
            List<Finding> out = new ArrayList<>();

            if (t.scale().size() > a.config().maxDistinctFontSizes) {
                out.add(finding(Severity.LOW, String.format(
                        "The page sets text at %d distinct sizes (%s).", t.scale().size(),
                        t.scale().stream().map(s -> String.format("%.0f", s)).toList()))
                        .with("distinctSizes", t.scale().size())
                        .with("sizes", t.scale())
                        .with("limit", a.config().maxDistinctFontSizes)
                        .suggest("Collapse to a named scale of 5-7 steps and reference it by token."));
            }

            List<Double> dupes = t.nearDuplicateSizes(a.config().minTypeStepPx);
            for (Double size : dupes.stream().limit(6).toList()) {
                int chars = t.sizeUsage().getOrDefault(size, 0);
                Double neighbour = t.scale().stream()
                        .filter(s -> !s.equals(size) && Math.abs(s - size) < a.config().minTypeStepPx)
                        .findFirst().orElse(null);
                if (neighbour == null) continue;
                Finding f = finding(Severity.LOW, String.format(
                        "%.1fpx sits only %.1fpx from %.1fpx and sets just %d characters -- the step "
                                + "is invisible to the reader.", size, Math.abs(neighbour - size), neighbour, chars))
                        .with("size", size).with("neighbour", neighbour).with("characters", chars)
                        .suggest(String.format("Use %.1fpx for both.", neighbour));
                a.snapshot().textElements().stream()
                        .filter(e -> Math.abs(Math.round(e.fontSize * 2) / 2.0 - size) < 0.01)
                        .findFirst().ifPresent(f::about);
                out.add(f);
            }
            return out;
        }
    }

    /** More typefaces than a page can own. */
    public static final class FontFamilySprawl implements Rule {
        @Override public String id() { return "font-family-sprawl"; }

        @Override public String title() { return "Too many typefaces"; }

        @Override public String rationale() {
            return "Two families, occasionally three, is the working limit: one for text, one "
                    + "for display, one for code. Beyond that the page stops having a voice, and "
                    + "each extra family is also a web font to load.";
        }

        @Override public List<Finding> check(Analysis a) {
            Map<String, Integer> fams = a.type().families();
            if (fams.size() <= a.config().maxDistinctFontFamilies) return List.of();
            List<Finding> out = new ArrayList<>();
            out.add(finding(Severity.LOW, String.format("The page uses %d typefaces: %s.",
                    fams.size(), String.join(", ", fams.keySet())))
                    .with("families", fams.keySet())
                    .with("limit", a.config().maxDistinctFontFamilies)
                    .suggest("Reduce to a text face and a display face, set as CSS custom properties."));
            for (String marginal : a.type().marginalFamilies(0.02)) {
                a.snapshot().textElements().stream()
                        .filter(e -> marginal.equals(e.fontFamily)).findFirst()
                        .ifPresent(e -> out.add(finding(Severity.INFO, String.format(
                                "'%s' sets almost no text on the page (%s).", marginal, e.label()))
                                .about(e).with("family", marginal)
                                .suggest("Fold this one-off into the page's own families.")));
            }
            return out;
        }
    }

    /** Line length outside the comfortable reading range. */
    public static final class Measure implements Rule {
        /** Below this viewport width a short measure is physics, not a choice. */
        private static final int NARROW_VIEWPORT_PX = 600;

        @Override public String id() { return "measure"; }

        @Override public String title() { return "Line length is outside the comfortable measure"; }

        @Override public String rationale() {
            return "Reading breaks down past about 85 characters a line: the eye loses its place "
                    + "on the return sweep. Below about 45 it breaks down the other way, with too "
                    + "many sweeps per paragraph. This is the oldest measurement in typography and "
                    + "the one most often lost when a container goes full-width.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (e.lines == null || e.lines.lineCount < 2) continue;    // single lines have no measure
                if (e.textLength < 80) continue;
                double cpl = e.charsPerLine();
                if (cpl > a.config().maxMeasureChars) {
                    out.add(finding(cpl > a.config().maxMeasureChars * 1.4 ? Severity.MEDIUM : Severity.LOW,
                            String.format("%s runs about %.0f characters per line over %d lines; the "
                                            + "comfortable maximum is %d.",
                                    e.label(), cpl, e.lines.lineCount, a.config().maxMeasureChars))
                            .about(e)
                            .with("charsPerLine", Math.round(cpl))
                            .with("lineCount", e.lines.lineCount)
                            .with("maxChars", a.config().maxMeasureChars)
                            .suggest(String.format("Constrain the text column to about %dch "
                                    + "(roughly %.0fpx at this size).",
                                    a.config().maxMeasureChars, a.config().maxMeasureChars * e.fontSize * 0.5)));
                } else if (cpl < a.config().minMeasureChars && e.lines.lineCount >= 4
                           && a.viewport().width() >= NARROW_VIEWPORT_PX) {
                    out.add(finding(Severity.LOW, String.format(
                            "%s runs only about %.0f characters per line over %d lines, so the "
                                    + "paragraph reads as a narrow ribbon.",
                            e.label(), cpl, e.lines.lineCount))
                            .about(e)
                            .with("charsPerLine", Math.round(cpl))
                            .with("lineCount", e.lines.lineCount)
                            .with("minChars", a.config().minMeasureChars)
                            .suggest("Widen the column, or reduce the type size so more fits per line."));
                }
            }
            return out;
        }
    }

    /** Leading too tight for the measure. */
    public static final class Leading implements Rule {
        @Override public String id() { return "leading"; }

        @Override public String title() { return "Line height is too tight for body text"; }

        @Override public String rationale() {
            return "Multi-line text set solid is hard to track: the eye needs a clear channel "
                    + "between lines to find the next one. 1.4-1.6 suits body copy; display type "
                    + "can and should be tighter.";
        }

        @Override public List<Finding> check(Analysis a) {
            double body = a.type().bodySize();
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (e.lines == null || e.lines.lineCount < 2) continue;
                if (e.fontSize > body * 1.4) continue;          // display type, judged differently
                double ratio = e.effectiveLineHeight() / Math.max(1, e.fontSize);
                if (ratio >= a.config().minBodyLineHeightRatio) continue;
                out.add(finding(ratio < 1.15 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s sets %.1fpx text on %.1fpx leading (%.2f), below the %.2f that "
                                + "multi-line body copy needs.",
                        e.label(), e.fontSize, e.effectiveLineHeight(), ratio,
                        a.config().minBodyLineHeightRatio))
                        .about(e)
                        .with("fontSize", e.fontSize)
                        .with("lineHeight", Math.round(e.effectiveLineHeight() * 10) / 10.0)
                        .with("ratio", Math.round(ratio * 100) / 100.0)
                        .suggest("Set line-height:1.5 (unitless, so it scales with the size)."));
            }
            return out;
        }
    }

    /** A heading or paragraph whose last line holds almost nothing. */
    public static final class Orphan implements Rule {
        @Override public String id() { return "orphan-line"; }

        @Override public String title() { return "A wrapped line ends with an orphan"; }

        @Override public String rationale() {
            return "One short word alone on the last line of a heading is the defect designers "
                    + "fix by hand before anything else. It draws the eye to nothing and makes the "
                    + "block look accidental.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (e.lines == null || e.lines.lineCount < 2 || e.lines.maxLineWidth <= 0) continue;
                double share = e.lines.lastLineWidth / e.lines.maxLineWidth;
                boolean heading = e.heading > 0 || e.fontSize > a.type().bodySize() * 1.3;
                if (share > (heading ? 0.18 : 0.12)) continue;
                out.add(finding(heading ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s wraps to %d lines and the last holds %.0f%% of a full line.",
                        e.label(), e.lines.lineCount, share * 100))
                        .about(e)
                        .with("lineCount", e.lines.lineCount)
                        .with("lastLineShare", Math.round(share * 100) / 100.0)
                        .suggest(heading
                                ? "Add text-wrap:balance to the heading, or bind the last two words "
                                  + "with a non-breaking space."
                                : "Add text-wrap:pretty, or adjust the column width."));
            }
            return out;
        }
    }

    /** Long runs of capitals. */
    public static final class AllCapsRun implements Rule {
        @Override public String id() { return "all-caps-run"; }

        @Override public String title() { return "A long run of text is set in capitals"; }

        @Override public String rationale() {
            return "Capitals remove the word shapes readers recognise, so reading slows markedly "
                    + "beyond a few words. Capitals belong on labels and small headings, not on "
                    + "sentences.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                boolean upper = "uppercase".equals(e.textTransform)
                        || (e.textLength > 25 && e.text.equals(e.text.toUpperCase())
                            && e.text.chars().anyMatch(Character::isLetter));
                if (!upper || e.textLength <= 25) continue;
                out.add(finding(Severity.LOW, String.format(
                        "%s sets %d characters in capitals.", e.label(), e.textLength))
                        .about(e).with("characters", e.textLength)
                        .suggest("Use sentence case and carry the emphasis with weight, size or "
                                + "letter-spacing instead."));
            }
            return out;
        }
    }
}
