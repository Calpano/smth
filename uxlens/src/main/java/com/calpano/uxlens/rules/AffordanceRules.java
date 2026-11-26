package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rgba;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Does the page look like what it is?
 *
 * <p>Affordance defects are the ones users blame themselves for. A link that looks like
 * text is never clicked; text that looks like a link is clicked and does nothing. Neither
 * shows up in a screenshot review, because both look perfectly fine -- the defect is in
 * the relationship between appearance and behaviour, and that needs both to be measured.
 */
public final class AffordanceRules {

    private AffordanceRules() { }

    public static List<Rule> all() {
        return List.of(new LinkNotDistinguishable(), new FalseAffordance(), new DestructiveAdjacency());
    }

    /** A link in running text that is set apart by colour alone, and not enough colour. */
    public static final class LinkNotDistinguishable implements Rule {
        /** WCAG F73: colour alone separates a link only at 3:1 against the surrounding text. */
        private static final double REQUIRED = 3.0;

        @Override public String id() { return "link-not-distinguishable"; }

        @Override public String title() { return "A link in text is not distinguishable from the text"; }

        @Override public String rationale() {
            return "Removing underlines is a defensible style choice everywhere except inside "
                    + "a paragraph, where the only thing left marking a link is its colour. "
                    + "WCAG F73 puts the bar at 3:1 against the surrounding text -- below that, "
                    + "readers with ordinary colour vision miss the link entirely, and readers "
                    + "without it never had a chance.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Finding> out = new ArrayList<>();
            for (Elem e : s.elements) {
                if (!"a".equals(e.tag) || !e.actionable || !e.hasOwnText()) continue;
                if (e.isUnderlined() || e.paintsSurface()) continue;      // marked by more than colour

                Elem parent = s.byId(e.parentId);
                if (parent == null || !parent.hasOwnText()) continue;     // not inside running text
                if (Math.abs(parent.fontSize - e.fontSize) > 1
                        || Math.abs(parent.fontWeight - e.fontWeight) >= 150) continue;   // set apart by type

                Rgba link = e.fg(), around = parent.fg();
                if (link == null || around == null) continue;
                Rgba bg = e.bg();
                double ratio = link.over(bg).contrastRatio(around.over(bg));
                if (ratio >= REQUIRED - 0.005) continue;

                out.add(finding(ratio < 1.5 ? Severity.HIGH : Severity.MEDIUM, String.format(
                        "%s is a link inside running text with no underline, and its colour (%s) "
                                + "is only %.2f:1 against the surrounding text (%s); %.1f:1 is needed "
                                + "when colour is the only cue.",
                        e.label(), link.hex(), ratio, around.hex(), REQUIRED))
                        .about(e)
                        .with("linkColor", link.hex())
                        .with("textColor", around.hex())
                        .with("ratio", Math.round(ratio * 100) / 100.0)
                        .with("required", REQUIRED)
                        .suggest("Underline links inside paragraphs, or raise the colour difference "
                                + "to 3:1 against the surrounding text. An underline is the one cue "
                                + "that survives both colour blindness and a greyscale print."));
            }
            return out;
        }
    }

    /** Text that wears a link's clothes without being one. */
    public static final class FalseAffordance implements Rule {
        /** Tags for which an underline means something other than "this is a link". */
        private static final Set<String> LEGITIMATELY_UNDERLINED =
                Set.of("ins", "u", "abbr", "del", "s", "mark");

        @Override public String id() { return "false-affordance"; }

        @Override public String title() { return "Text looks like a link but is not one"; }

        @Override public String rationale() {
            return "Underlined text has meant 'click me' on the web for thirty years. Using it "
                    + "for emphasis produces clicks that do nothing, and a reader who concludes "
                    + "the page is broken rather than that they misread it.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (e.actionable || !e.isUnderlined()) continue;
                if (LEGITIMATELY_UNDERLINED.contains(e.tag)) continue;
                // A heading is not mistaken for a link; a short run inside a paragraph is.
                if (e.heading > 0 || e.textLength > 120) continue;

                out.add(finding(Severity.LOW, String.format(
                        "%s is underlined but does nothing when clicked.", e.label()))
                        .about(e)
                        .with("textDecoration", e.textDecorationLine)
                        .suggest("Carry the emphasis with weight or colour and keep the underline "
                                + "for links. If it should be a link, make it an <a href>."));
            }
            return out;
        }
    }

    /** A destructive control sitting where the harmless one is expected. */
    public static final class DestructiveAdjacency implements Rule {
        /** Gap below which a mis-aimed tap lands on the wrong control. */
        private static final double DANGEROUS_GAP_PX = 8;

        private static final Set<String> DESTRUCTIVE_WORDS = Set.of(
                "delete", "remove", "discard", "destroy", "erase", "revoke", "deactivate",
                "cancel subscription", "close account", "reset", "wipe", "unsubscribe",
                "löschen", "entfernen", "verwerfen", "kündigen", "zurücksetzen",
                "supprimer", "eliminar", "borrar");

        @Override public String id() { return "destructive-adjacency"; }

        @Override public String title() { return "A destructive control is flush against a benign one"; }

        @Override public String rationale() {
            return "Pointing is imprecise -- that is Fitts's law, and it is why an irreversible "
                    + "action should not share an edge with a reversible one. The cost of a miss "
                    + "is asymmetric, so the spacing should be too.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Elem> controls = s.elements.stream()
                    .filter(e -> e.actionable && !e.disabled && e.childCount <= 2)
                    .toList();

            List<Finding> out = new ArrayList<>();
            for (Elem destructive : controls) {
                if (!isDestructive(destructive)) continue;
                for (Elem neighbour : controls) {
                    if (neighbour.id == destructive.id || isDestructive(neighbour)) continue;
                    double gap = destructive.rect().gapTo(neighbour.rect());
                    if (gap > DANGEROUS_GAP_PX) continue;
                    // Only controls sharing a row or column read as one group of buttons.
                    if (destructive.rect().rowOverlap(neighbour.rect()) < 0.5
                            && destructive.rect().columnOverlap(neighbour.rect()) < 0.5) continue;

                    out.add(finding(Severity.MEDIUM, String.format(
                            "%s sits %.0fpx from %s, so a mis-aimed click on a reversible action "
                                    + "lands on an irreversible one.",
                            destructive.label(), gap, neighbour.label()))
                            .about(destructive, neighbour)
                            .with("gapPx", Math.round(gap))
                            .with("minimumGapPx", DANGEROUS_GAP_PX)
                            .suggest("Separate them, move the destructive action out of the primary "
                                    + "group, or require a confirmation step. Spacing should reflect "
                                    + "the cost of a miss, not the tidiness of the row."));
                    break;    // one finding per destructive control is enough
                }
            }
            return out;
        }

        private static boolean isDestructive(Elem e) {
            String text = (e.hasOwnText() ? e.text : e.ariaLabel == null ? "" : e.ariaLabel)
                    .toLowerCase(Locale.ROOT).trim();
            if (text.isBlank() || text.length() > 40) return false;
            return DESTRUCTIVE_WORDS.stream().anyMatch(text::contains);
        }
    }
}
