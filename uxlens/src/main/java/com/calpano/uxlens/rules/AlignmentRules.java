package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Near-miss alignment: the defect class a multimodal model cannot see and arithmetic
 * catches every time.
 *
 * <p>The signature of a misalignment is that it is <em>almost</em> right. A 2px offset is
 * a bug; a 40px offset is a layout decision. So every rule here reports only offsets
 * inside a tolerance window, and never a clean difference.
 */
public final class AlignmentRules {

    private AlignmentRules() { }

    public static List<Rule> all() {
        return List.of(new EdgeAlignment(), new BaselineDrift(), new RowCenterDrift(), new PageMargin());
    }

    /** Left or right edges in a vertical stack that nearly agree. */
    public static final class EdgeAlignment implements Rule {
        @Override public String id() { return "edge-alignment"; }

        @Override public String title() { return "Stacked elements have nearly-aligned edges"; }

        @Override public String rationale() {
            return "The eye follows the vertical line formed by the left edges of stacked "
                    + "content. One element a few pixels off breaks that line, and the page "
                    + "reads as sloppy without the reader being able to say why.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            double tol = a.config().alignmentTolerancePx;
            double ignore = a.config().alignmentIgnoreBelowPx;

            for (VisualGroups.Group g : a.stacks()) {
                if (g.size() < 3) continue;
                out.addAll(checkEdge(g, tol, ignore, true));
                out.addAll(checkEdge(g, tol, ignore, false));
            }
            return out;
        }

        private List<Finding> checkEdge(VisualGroups.Group g, double tol, double ignore, boolean left) {
            // A shrink-to-fit box ends where its text ends, so its right edge carries no
            // alignment intent. Left edges are set by the layout either way.
            List<Elem> candidates = left ? g.members()
                    : g.members().stream().filter(e -> !isShrinkToFit(e)).toList();
            if (candidates.size() < 3) return List.of();

            // The modal edge is the intended one; anything near it but not on it is a slip.
            Map<Long, List<Elem>> byEdge = new HashMap<>();
            for (Elem e : candidates) {
                double edge = left ? e.x : e.rect().right();
                byEdge.computeIfAbsent(Math.round(edge * 2), k -> new ArrayList<>()).add(e);
            }
            Map.Entry<Long, List<Elem>> modal = byEdge.entrySet().stream()
                    .max(Comparator.comparingInt(en -> en.getValue().size())).orElse(null);
            if (modal == null || modal.getValue().size() < 2) return List.of();

            double intended = modal.getKey() / 2.0;
            List<Finding> out = new ArrayList<>();
            for (Elem e : candidates) {
                double edge = left ? e.x : e.rect().right();
                double delta = Math.abs(edge - intended);
                if (delta <= ignore || delta > tol) continue;
                out.add(new Finding(id(), delta <= 1.5 ? Severity.LOW : Severity.MEDIUM, String.format(
                        "%s edge of %s is %.1fpx off the %.0fpx line that %d sibling%s share.",
                        left ? "Left" : "Right", e.label(), delta, intended,
                        modal.getValue().size(), modal.getValue().size() == 1 ? "" : "s"))
                        .about(e)
                        .with("edge", left ? "left" : "right")
                        .with("actual", Math.round(edge * 10) / 10.0)
                        .with("expected", intended)
                        .with("deltaPx", Math.round(delta * 10) / 10.0)
                        .with("siblingsOnLine", modal.getValue().size())
                        .suggest(String.format("Set the %s edge to %.0fpx. A stray padding, border or "
                                        + "margin of %.1fpx is the usual cause -- let the shared container "
                                        + "own the inset instead.",
                                left ? "left" : "right", intended, delta)));
            }
            return out;
        }

        /** True for a box whose width is decided by its content rather than by the layout. */
        private static boolean isShrinkToFit(Elem e) {
            if (e.display == null) return false;
            return e.display.startsWith("inline") || e.display.equals("table")
                    || e.display.equals("flow-root") && e.w < 1;
        }
    }

    /** Text in the same row sitting on nearly the same baseline. */
    public static final class BaselineDrift implements Rule {
        @Override public String id() { return "baseline-drift"; }

        @Override public String title() { return "Text baselines in a row do not line up"; }

        @Override public String rationale() {
            return "Typographers align text on its baseline, not its box. Two labels in a "
                    + "navbar whose baselines differ by 2px look wrong even though both boxes "
                    + "are centred -- usually because the font sizes differ and the flex "
                    + "alignment is centre rather than baseline.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            double tol = Math.max(a.config().alignmentTolerancePx, 5);
            for (VisualGroups.Group g : a.rows()) {
                List<Elem> texts = g.members().stream()
                        .filter(e -> e.hasOwnText() && e.lines != null && e.lines.lineCount == 1)
                        .toList();
                if (texts.size() < 2) continue;

                Map<Long, List<Elem>> byBaseline = new HashMap<>();
                for (Elem e : texts) byBaseline.computeIfAbsent(Math.round(e.baselineY()), k -> new ArrayList<>()).add(e);
                if (byBaseline.size() < 2) continue;

                Map.Entry<Long, List<Elem>> modal = byBaseline.entrySet().stream()
                        .max(Comparator.comparingInt(en -> en.getValue().size())).orElseThrow();
                double intended = modal.getKey();
                for (Elem e : texts) {
                    double delta = Math.abs(e.baselineY() - intended);
                    if (delta < 0.75 || delta > tol) continue;
                    out.add(new Finding(id(), delta <= 2 ? Severity.LOW : Severity.MEDIUM, String.format(
                            "%s sits %.1fpx off the baseline shared by the rest of its row.",
                            e.label(), delta))
                            .about(e)
                            .with("baselineY", Math.round(e.baselineY() * 10) / 10.0)
                            .with("expectedBaselineY", intended)
                            .with("deltaPx", Math.round(delta * 10) / 10.0)
                            .suggest("Use align-items:baseline on the row instead of centre, or match "
                                    + "the line-heights of the items so their boxes centre identically."));
                }
            }
            return out;
        }
    }

    /** Icons and controls in a row whose vertical centres nearly agree. */
    public static final class RowCenterDrift implements Rule {
        @Override public String id() { return "row-center-drift"; }

        @Override public String title() { return "Items in a row are not vertically centred together"; }

        @Override public String rationale() {
            return "An icon one pixel above the label beside it is the classic unpolished "
                    + "detail. Non-text items align on their centres, so that is what to compare.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            double tol = a.config().alignmentTolerancePx;
            for (VisualGroups.Group g : a.rows()) {
                List<Elem> items = g.members().stream().filter(e -> e.h < 120).toList();
                if (items.size() < 2) continue;

                Map<Long, List<Elem>> byCenter = new HashMap<>();
                for (Elem e : items) byCenter.computeIfAbsent(Math.round(e.rect().centerY()), k -> new ArrayList<>()).add(e);
                if (byCenter.size() < 2) continue;
                Map.Entry<Long, List<Elem>> modal = byCenter.entrySet().stream()
                        .max(Comparator.comparingInt(en -> en.getValue().size())).orElseThrow();
                if (modal.getValue().size() < 2) continue;

                double intended = modal.getKey();
                for (Elem e : items) {
                    double delta = Math.abs(e.rect().centerY() - intended);
                    if (delta <= a.config().alignmentIgnoreBelowPx || delta > tol) continue;
                    out.add(new Finding(id(), delta <= 1.5 ? Severity.LOW : Severity.MEDIUM, String.format(
                            "%s is %.1fpx off the vertical centre of its row.", e.label(), delta))
                            .about(e)
                            .with("centerY", Math.round(e.rect().centerY() * 10) / 10.0)
                            .with("expectedCenterY", intended)
                            .with("deltaPx", Math.round(delta * 10) / 10.0)
                            .suggest("Centre the row's items with align-items:center and remove the "
                                    + "per-item vertical nudge."));
                }
            }
            return out;
        }
    }

    /** The page's own left margin, broken by one section. */
    public static final class PageMargin implements Rule {
        @Override public String id() { return "page-margin"; }

        @Override public String title() { return "A section breaks the page's left margin"; }

        @Override public String rationale() {
            return "Full-width sections establish a margin that the reader perceives as the "
                    + "page's edge. One section inset differently reads as a mistake even when "
                    + "its own internals are perfect.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Elem> blocks = VisualGroups.majorBlocks(a.snapshot());
            if (blocks.size() < 3) return List.of();

            Map<Long, List<Elem>> byLeft = new HashMap<>();
            for (Elem e : blocks) byLeft.computeIfAbsent(Math.round(e.x), k -> new ArrayList<>()).add(e);
            Map.Entry<Long, List<Elem>> modal = byLeft.entrySet().stream()
                    .max(Comparator.comparingInt(en -> en.getValue().size())).orElseThrow();
            if (modal.getValue().size() < 2) return List.of();

            double intended = modal.getKey();
            double tol = Math.max(a.config().alignmentTolerancePx, 8);
            List<Finding> out = new ArrayList<>();
            for (Elem e : blocks) {
                double delta = Math.abs(e.x - intended);
                if (delta <= a.config().alignmentIgnoreBelowPx || delta > tol) continue;
                out.add(new Finding(id(), Severity.MEDIUM, String.format(
                        "%s starts at %.0fpx while %d other full-width block%s start at %.0fpx.",
                        e.label(), e.x, modal.getValue().size(),
                        modal.getValue().size() == 1 ? "" : "s", intended))
                        .about(e)
                        .with("left", e.x).with("expectedLeft", intended)
                        .with("deltaPx", Math.round(delta * 10) / 10.0)
                        .suggest("Put the inset on a shared container or a single spacing token "
                                + "rather than on each section."));
            }
            return out;
        }
    }
}
