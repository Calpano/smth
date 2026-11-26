package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestalt proximity: things that belong together must sit together.
 *
 * <p>This is the defect class that reads as confusing rather than ugly, and it is
 * invisible in the markup -- a label is correctly associated with its field by {@code for}
 * and still sits closer to the field above it.
 */
public final class ProximityRules {

    private ProximityRules() { }

    public static List<Rule> all() { return List.of(new LabelProximity(), new OrphanedHeading()); }

    /** A field label closer to the previous field than to its own. */
    public static final class LabelProximity implements Rule {
        @Override public String id() { return "label-proximity"; }

        @Override public String title() { return "A label sits closer to the wrong field"; }

        @Override public String rationale() {
            return "Readers group by distance before they read anything. A label with 16px below "
                    + "it and 8px above it belongs, visually, to the field above -- whatever the "
                    + "'for' attribute says. Forms fail this constantly because the margin is put "
                    + "on the wrong side of the label.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (VisualGroups.Group g : a.stacks()) {
                List<Elem> m = g.members();
                for (int i = 1; i < m.size() - 1; i++) {
                    Elem label = m.get(i);
                    Elem below = m.get(i + 1);
                    Elem above = m.get(i - 1);
                    if (!isLabelLike(label) || !isControl(below)) continue;

                    double gapBelow = below.y - label.rect().bottom();
                    double gapAbove = label.y - above.rect().bottom();
                    if (gapBelow < 0 || gapAbove < 0) continue;
                    if (gapAbove >= gapBelow - 0.5) continue;        // correctly grouped

                    out.add(finding(gapBelow > gapAbove * 2 ? Severity.MEDIUM : Severity.LOW, String.format(
                            "%s sits %.0fpx above the field it labels but only %.0fpx below %s, so it "
                                    + "reads as belonging to the field above.",
                            label.label(), gapBelow, gapAbove, above.label()))
                            .about(label, below)
                            .with("gapToOwnField", Math.round(gapBelow))
                            .with("gapToPrevious", Math.round(gapAbove))
                            .suggest(String.format("Move the spacing to the top of the label: it needs "
                                    + "more than %.0fpx above it and less than %.0fpx below.",
                                    gapBelow, gapAbove)));
                }
            }
            return out;
        }

        private static boolean isLabelLike(Elem e) {
            return e.hasOwnText() && e.textLength < 60
                    && ("label".equals(e.tag) || e.lines == null || e.lines.lineCount <= 2);
        }

        private static boolean isControl(Elem e) {
            return "input".equals(e.tag) || "select".equals(e.tag) || "textarea".equals(e.tag)
                    || (e.interactive && !e.hasOwnText());
        }
    }

    /** A heading nearer the block before it than the block it introduces. */
    public static final class OrphanedHeading implements Rule {
        @Override public String id() { return "orphaned-heading"; }

        @Override public String title() { return "A heading sits closer to the previous section"; }

        @Override public String rationale() {
            return "A heading is a lid, not a floor. When the space above it is smaller than the "
                    + "space below it, the page appears to divide in the wrong places and skimming "
                    + "produces the wrong outline.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (VisualGroups.Group g : a.stacks()) {
                List<Elem> m = g.members();
                for (int i = 1; i < m.size() - 1; i++) {
                    Elem heading = m.get(i);
                    if (heading.interactive) continue;            // a large CTA is not a heading
                    if (heading.heading == 0
                            && !(heading.fontSize > a.type().bodySize() * 1.25 && heading.fontWeight >= 600)) {
                        continue;
                    }
                    double gapAbove = heading.y - m.get(i - 1).rect().bottom();
                    double gapBelow = m.get(i + 1).y - heading.rect().bottom();
                    if (gapAbove < 0 || gapBelow < 0) continue;
                    if (gapAbove >= gapBelow - 0.5) continue;
                    if (gapBelow - gapAbove < 4) continue;          // a wobble, not a grouping error

                    out.add(finding(gapBelow > gapAbove * 2.5 ? Severity.MEDIUM : Severity.LOW, String.format(
                            "%s has %.0fpx above it and %.0fpx below, so it groups with the preceding "
                                    + "content rather than the section it heads.",
                            heading.label(), gapAbove, gapBelow))
                            .about(heading)
                            .with("gapAbove", Math.round(gapAbove))
                            .with("gapBelow", Math.round(gapBelow))
                            .suggest(String.format("Give the heading more space above than below -- "
                                    + "swap the values, or set margin-top:%.0fpx and margin-bottom:%.0fpx.",
                                    gapBelow, gapAbove)));
                }
            }
            return out;
        }
    }
}
