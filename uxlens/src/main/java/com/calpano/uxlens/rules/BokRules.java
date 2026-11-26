package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rgba;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks taken from the professional body of knowledge rather than from this project's
 * own reasoning.
 *
 * <p>Each one comes from somewhere a practitioner would recognise -- the Gestalt
 * principles, ISO 9241's guidance on forms and visual presentation, the typographic canon,
 * platform human-interface guidance -- and each had to survive the same gate as everything
 * else: a predicate, a threshold, and a pair of pages proving it discriminates. Several
 * well-known principles did not survive that gate and are documented as out of scope rather
 * than implemented badly; see {@code StandardsCoverage}.
 */
public final class BokRules {

    private BokRules() { }

    public static List<Rule> all() {
        return List.of(new InvisibleSurface(), new MinimumTextSize(),
                new MixedLabelPlacement(), new LeadingForMeasure());
    }

    /** A surface painted so close to its background that it is not a surface. */
    public static final class InvisibleSurface implements Rule {

        /** Contrast below which one surface does not separate from the one behind it. */
        private static final double SEPARATION = 1.08;
        /** Big enough to be a panel rather than a highlighted word. */
        private static final double MIN_AREA = 4000;
        /** At or below this the two surfaces are the same colour, which is not a defect. */
        private static final double IDENTICAL = 1.002;

        @Override public String id() { return "invisible-surface"; }

        @Override public String title() { return "A surface does not separate from its background"; }

        @Override public String rationale() {
            return "Figure and ground is the first thing vision does, before shape and long "
                    + "before meaning. A card whose fill differs from the page by less than a "
                    + "rounding error, with no border and no shadow, is not a card: the work of "
                    + "painting it has been done and none of the benefit collected. This is "
                    + "distinct from text contrast, which is about the ink rather than the "
                    + "surface under it.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Finding> out = new ArrayList<>();
            for (Elem e : s.elements) {
                if (e.area() < MIN_AREA || e.fixedLayer) continue;

                Rgba own = Rgba.parse(e.backgroundColor);
                if (own == null || own.a() < 0.05) continue;        // nothing was painted
                if (e.hasVisibleBorder()) continue;                  // delineated by its edge
                if (e.boxShadow != null && !e.boxShadow.isBlank()) continue;

                Elem parent = s.byId(e.parentId);
                if (parent == null) continue;
                Rgba behind = parent.bg();
                Rgba painted = own.over(behind);
                double separation = painted.contrastRatio(behind);
                if (separation >= SEPARATION) continue;
                // Exactly the same colour is the same surface -- a grid row carrying the app's
                // own background, which is ordinary and costs nothing. The defect is a surface
                // that tried to differ and failed, so there has to be an attempt to detect.
                if (separation <= IDENTICAL) continue;
                // And it has to look like something meant to read as a panel.
                boolean looksLikeAPanel = e.borderRadius >= 2
                        || (e.padTop >= 8 && e.padLeft >= 8);
                if (!looksLikeAPanel) continue;

                out.add(finding(Severity.LOW, String.format(
                        "%s paints a %s surface on a %s background -- %.3f:1, which is no "
                                + "separation at all, and it has neither a border nor a shadow.",
                        e.label(), painted.hex(), behind.hex(), separation))
                        .about(e)
                        .with("surface", painted.hex())
                        .with("behind", behind.hex())
                        .with("separation", Math.round(separation * 1000) / 1000.0)
                        .suggest("Either make the surface differ enough to be seen, or give it a "
                                + "border or a shadow, or drop the background and group by "
                                + "whitespace instead. What it must not do is almost separate."));
            }
            return out;
        }
    }

    /** Type below the size at which it stops being readable, whatever its contrast. */
    public static final class MinimumTextSize implements Rule {

        /** Below this, text is a problem for everybody, not only for the short-sighted. */
        private static final double ABSOLUTE_FLOOR_PX = 10;
        /** Below this, running text is too small even though a caption would be fine. */
        private static final double PROSE_FLOOR_PX = 12;
        /** Characters above which a run counts as something meant to be read, not glanced at. */
        private static final int PROSE_CHARS = 120;

        @Override public String id() { return "minimum-text-size"; }

        @Override public String title() { return "Text is set below a readable size"; }

        @Override public String rationale() {
            return "Contrast rules say nothing about size, so text can pass every contrast "
                    + "check and still be unreadable. Platform guidance converges on about 11 "
                    + "points as a floor for anything a user is expected to read, and the "
                    + "smaller a face is set the more its contrast actually needs to be -- so "
                    + "small text is rarely small in isolation.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                boolean prose = e.textLength >= PROSE_CHARS;
                double floor = prose ? PROSE_FLOOR_PX : ABSOLUTE_FLOOR_PX;
                if (e.fontSize >= floor - 0.01) continue;

                out.add(finding(e.fontSize < 9 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s is set at %.1fpx%s, below the %.0fpx floor.",
                        e.label(), e.fontSize,
                        prose ? " and carries " + e.textLength + " characters of running text" : "",
                        floor))
                        .about(e)
                        .with("fontSize", e.fontSize)
                        .with("floor", floor)
                        .with("characters", e.textLength)
                        .suggest(prose
                                ? "Set running text at 16px and let the user's own settings scale it."
                                : "Raise it to at least 12px, or accept that nobody will read it "
                                  + "and remove it."));
            }
            return out;
        }
    }

    /** A form that puts its labels above some fields and beside others. */
    public static final class MixedLabelPlacement implements Rule {

        /** Enough label-field pairs to be describing a pattern. */
        private static final int MIN_PAIRS = 3;

        @Override public String id() { return "mixed-label-placement"; }

        @Override public String title() { return "Labels are placed inconsistently within a form"; }

        @Override public String rationale() {
            return "A reader learns where a form keeps its labels within the first two fields "
                    + "and then stops looking. Changing the rule halfway down costs them that, "
                    + "and the cost is paid on every field after it. Each individual field is "
                    + "fine, which is why this survives review and why no amount of checking a "
                    + "component in isolation finds it.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Elem> above = new ArrayList<>();
            List<Elem> beside = new ArrayList<>();

            for (Elem label : s.elements) {
                if (!"label".equals(label.tag) || !label.hasOwnText()) continue;
                Elem field = nearestField(s, label);
                if (field == null) continue;
                // Above: the label's box ends before the field's begins, vertically.
                if (label.rect().bottom() <= field.y + 1) {
                    above.add(label);
                } else if (label.rect().rowOverlap(field.rect()) > 0.5
                        && label.rect().right() <= field.x + 1) {
                    beside.add(label);
                }
            }
            int pairs = above.size() + beside.size();
            if (pairs < MIN_PAIRS || above.isEmpty() || beside.isEmpty()) return List.of();

            boolean aboveWins = above.size() >= beside.size();
            List<Elem> minority = aboveWins ? beside : above;
            Finding f = finding(Severity.MEDIUM, String.format(
                    "Of %d labelled fields, %d put the label above and %d put it beside.",
                    pairs, above.size(), beside.size()))
                    .with("above", above.size())
                    .with("beside", beside.size())
                    .suggest(String.format("Put every label %s, as %d of the %d already do.",
                            aboveWins ? "above its field" : "beside its field",
                            Math.max(above.size(), beside.size()), pairs));
            minority.stream().limit(4).forEach(f::about);
            return List.of(f);
        }

        /** The control this label most plausibly belongs to: the nearest one it can reach. */
        private static Elem nearestField(PageSnapshot s, Elem label) {
            Elem best = null;
            double bestGap = Double.MAX_VALUE;
            for (Elem e : s.elements) {
                if (!("input".equals(e.tag) || "select".equals(e.tag) || "textarea".equals(e.tag))) continue;
                double gap = label.rect().gapTo(e.rect());
                if (gap > 48 || gap >= bestGap) continue;
                bestGap = gap;
                best = e;
            }
            return best;
        }
    }

    /** Tight leading on a long measure: the two defects that compound. */
    public static final class LeadingForMeasure implements Rule {

        /** Characters per line past which the return sweep needs help from the leading. */
        private static final int LONG_MEASURE_CHARS = 60;
        /** Leading a long measure needs. Shorter lines get by on less. */
        private static final double REQUIRED_RATIO = 1.5;

        @Override public String id() { return "leading-for-measure"; }

        @Override public String title() { return "Leading is too tight for the length of the line"; }

        @Override public String rationale() {
            return "Leading and measure are one decision, not two. The longer the line, the "
                    + "further the eye travels on the return sweep and the more vertical "
                    + "separation it needs to land on the right line. 1.4 is comfortable at 50 "
                    + "characters and not at 90. A page can pass a flat leading threshold and "
                    + "still be hard to read for exactly this reason.";
        }

        @Override public List<Finding> check(Analysis a) {
            double body = a.snapshot().bodyFontSize();
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (e.lines == null || e.lines.lineCount < 3) continue;
                if (e.fontSize > body * 1.4) continue;                   // display type, judged apart
                double ratio = e.effectiveLineHeight() / Math.max(1, e.fontSize);
                // Below 1.3 the flat leading rule already reports it; this is the band it allows.
                if (ratio < a.config().minBodyLineHeightRatio || ratio >= REQUIRED_RATIO) continue;

                double cpl = e.charsPerLine();
                if (cpl < LONG_MEASURE_CHARS) continue;

                out.add(finding(Severity.LOW, String.format(
                        "%s runs about %.0f characters a line on %.2f leading. A measure that "
                                + "long needs %.2f to stay trackable.",
                        e.label(), cpl, ratio, REQUIRED_RATIO))
                        .about(e)
                        .with("charsPerLine", Math.round(cpl))
                        .with("ratio", Math.round(ratio * 100) / 100.0)
                        .with("required", REQUIRED_RATIO)
                        .suggest(String.format("Either open the leading to %.2f, or narrow the "
                                        + "column to about %dch so the current leading is enough. "
                                        + "They trade against each other.",
                                REQUIRED_RATIO, LONG_MEASURE_CHARS)));
            }
            return out;
        }
    }
}
