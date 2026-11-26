package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.SpacingSystem;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;

/** Spacing rhythm: equal things equally spaced, on one scale. */
public final class RhythmRules {

    private RhythmRules() { }

    public static List<Rule> all() { return List.of(new GapConsistency(), new SpacingScale()); }

    /** Gaps between siblings that should be identical and are not. */
    public static final class GapConsistency implements Rule {
        @Override public String id() { return "gap-consistency"; }

        @Override public String title() { return "Gaps between siblings are uneven"; }

        @Override public String rationale() {
            return "A row of cards with gaps of 24, 24 and 21px looks mechanically wrong. "
                    + "Uneven gaps between like items are among the most noticeable defects in "
                    + "a layout, and they almost always mean spacing is set per item rather than "
                    + "by the container's gap property.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (VisualGroups.Group g : a.groups()) {
                if (g.size() < 3) continue;
                if (!isHomogeneous(g)) continue;
                List<Double> gaps = g.gaps();
                if (gaps.size() < 2) continue;
                if (gaps.stream().anyMatch(v -> v < -1)) continue;   // overlapping; another rule's business

                List<Double> sorted = gaps.stream().sorted().toList();
                double median = sorted.get(sorted.size() / 2);
                if (median < 1) continue;                            // flush items, nothing to space

                double worst = 0;
                int worstIndex = -1;
                for (int i = 0; i < gaps.size(); i++) {
                    double d = Math.abs(gaps.get(i) - median);
                    if (d > worst) { worst = d; worstIndex = i; }
                }
                double allowed = Math.max(a.config().gapToleranceMinPx, median * a.config().gapToleranceRatio);
                if (worst <= allowed) continue;

                Elem before = g.members().get(worstIndex);
                Elem after = g.members().get(worstIndex + 1);
                out.add(finding(worst > median * 0.5 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s gaps in this group of %d run %s; the gap between %s and %s is %.0fpx "
                                + "against a median of %.0fpx.",
                        g.axis() == VisualGroups.Axis.ROW ? "Horizontal" : "Vertical", g.size(),
                        gaps.stream().map(v -> String.format("%.0f", v)).toList(),
                        before.label(), after.label(), gaps.get(worstIndex), median))
                        .about(before, after)
                        .at(g.bounds())
                        .with("gaps", gaps.stream().map(v -> Math.round(v * 10) / 10.0).toList())
                        .with("median", median)
                        .with("worstDeviationPx", Math.round(worst * 10) / 10.0)
                        .suggest(String.format("Set one gap:%.0fpx on %s and drop the per-item margins.",
                                median, g.parent().selector)));
            }
            return out;
        }

        /**
         * Only like items are compared. A row of cards must be evenly spaced; the top-level
         * flow of a page -- heading, paragraph, form, figure -- must not, because its spacing
         * is meant to vary with what it separates.
         */
        private static boolean isHomogeneous(VisualGroups.Group g) {
            boolean sameTag = g.members().stream().map(e -> e.tag).distinct().count() == 1;
            if (sameTag) return true;
            double minW = Double.MAX_VALUE, maxW = 0, minH = Double.MAX_VALUE, maxH = 0;
            for (Elem e : g.members()) {
                minW = Math.min(minW, e.w); maxW = Math.max(maxW, e.w);
                minH = Math.min(minH, e.h); maxH = Math.max(maxH, e.h);
            }
            if (minW <= 0 || minH <= 0) return false;
            // Same shape, different tags: still a set of like items (an icon beside a chip).
            return maxW / minW <= 1.15 && maxH / minH <= 1.3;
        }
    }

    /** Spacing values off the page's own scale. */
    public static final class SpacingScale implements Rule {
        /** Report at most this many outliers; the list is sorted so the nearest misses come first. */
        private static final int MAX_REPORTED = 12;

        @Override public String id() { return "spacing-scale"; }

        @Override public String title() { return "Spacing values are off the page's own scale"; }

        @Override public String rationale() {
            return "Consistent spacing is what makes a layout feel designed rather than "
                    + "assembled. The scale is inferred from the page itself, so a page built on "
                    + "5px units is judged against 5px -- only the values that break the page's "
                    + "own system are reported.";
        }

        @Override public List<Finding> check(Analysis a) {
            SpacingSystem sp = a.spacing();
            if (sp.baseUnit() <= 0 || sp.sampleCount() < 8) return List.of();

            List<Finding> out = new ArrayList<>();
            if (sp.adherence() < 0.6) {
                out.add(finding(Severity.LOW, String.format(
                        "Only %.0f%% of spacing values sit on a common unit; the closest fit is %dpx "
                                + "across %d distinct values. The page has no spacing scale.",
                        sp.adherence() * 100, sp.baseUnit(), sp.histogram().size()))
                        .with("adherence", Math.round(sp.adherence() * 100) / 100.0)
                        .with("baseUnit", sp.baseUnit())
                        .with("distinctValues", sp.histogram().size())
                        .suggest("Adopt a spacing scale (4px or 8px steps) as CSS custom properties "
                                + "and replace ad-hoc px values with them."));
                return out;
            }

            int reported = 0;
            for (SpacingSystem.Outlier o : sp.outliers()) {
                if (reported++ >= MAX_REPORTED) break;
                if (o.drift() > sp.baseUnit() / 2.0) continue;   // far off the scale: likely deliberate
                Elem e = a.snapshot().byId(o.elementId());
                if (e == null) continue;
                Finding f = finding(Severity.LOW, String.format(
                        "%s on %s is %.1fpx, off the page's %dpx scale (nearest: %.0fpx).",
                        o.property(), e.label(), o.value(), sp.baseUnit(), o.nearestOnScale()))
                        .about(e)
                        .with("property", o.property())
                        .with("value", o.value())
                        .with("baseUnit", sp.baseUnit())
                        .with("nearestOnScale", o.nearestOnScale())
                        .suggest(String.format("Use %.0fpx.", o.nearestOnScale()));
                out.add(f);
            }
            return out;
        }
    }
}
