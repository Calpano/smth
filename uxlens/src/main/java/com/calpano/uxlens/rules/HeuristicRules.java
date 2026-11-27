package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Consistency;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The named heuristics, where they can be measured.
 *
 * <p>Most of Nielsen's ten and Shneiderman's eight are about behaviour over time and are
 * out of reach of a rendered page; {@link StandardsCoverage} goes through them item by item
 * and says which and why. What survives is the handful below, and they survive because a
 * static render genuinely answers them: whether a control says what it is, whether what it
 * says matches what it shows, whether there is a visible way out, and whether things that
 * belong together share a region.
 */
public final class HeuristicRules {

    private HeuristicRules() { }

    public static List<Rule> all() {
        return List.of(new UnlabelledIconControl(), new LabelInName(),
                new DialogWithoutEscape(), new InconsistentCommonRegion());
    }

    /** A control showing an icon and announcing nothing. */
    public static final class UnlabelledIconControl implements Rule {

        @Override public String id() { return "unlabelled-icon-control"; }

        @Override public String title() { return "An icon-only control has no name"; }

        @Override public String rationale() {
            return "Recognition beats recall, and an unlabelled icon asks for recall: the user "
                    + "has to remember what that glyph did last time. For anyone using a screen "
                    + "reader there is nothing to recall from -- the control announces its tag "
                    + "and stops, or announces a symbol's Unicode name, which is worse. A "
                    + "multiplication sign is not the word close.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (!e.actionable || e.disabled || e.ariaHidden) continue;
                if (!isPushable(e)) continue;
                if (!e.isIconOnly()) continue;
                if (hasWord(e.accessibleName)) continue;

                boolean nameless = e.hasNoAccessibleName();
                out.add(finding(Severity.HIGH, String.format(
                        "%s shows no words and %s.", e.label(),
                        nameless ? "has no accessible name at all"
                                : "is named only \"" + e.accessibleName + "\", which is a symbol "
                                  + "rather than a word"))
                        .about(e)
                        .with("accessibleName", e.accessibleName)
                        .with("visibleText", e.visibleText)
                        .suggest("Add an aria-label naming the action in words -- \"Close\", "
                                + "\"Delete item\" -- or put a visible label beside the icon. A "
                                + "tooltip is not enough on its own; it is not reachable by touch."));
            }
            return out;
        }

        /** True when the name contains an actual word rather than only punctuation or symbols. */
        private static boolean hasWord(String name) {
            return name != null && name.codePoints().anyMatch(Character::isLetter);
        }

        /**
         * True for something you press: a button, a link, a summary. A text field shows no
         * words either, and that is simply what a text field looks like -- whether it is
         * labelled is a different question from whether a glyph has a name.
         */
        private static boolean isPushable(Elem e) {
            if ("button".equals(e.role) || "link".equals(e.role)) return true;
            if (e.tag == null) return false;    // a string switch on null throws
            return switch (e.tag) {
                case "button", "a", "summary" -> true;
                case "input" -> e.type != null
                        && Set.of("button", "submit", "reset", "image").contains(e.type);
                default -> false;
            };
        }
    }

    /** A control whose spoken name does not contain the words printed on it. */
    public static final class LabelInName implements Rule {

        @Override public String id() { return "label-in-name"; }

        @Override public String title() { return "The accessible name omits the visible label"; }

        @Override public String rationale() {
            return "Someone using voice control says what they can see: \"click send\". If the "
                    + "control reads Send but is named \"submit form\", the command fails and the "
                    + "user has no way to discover why. The visible words must appear in the "
                    + "accessible name, which is what WCAG 2.5.3 requires and what an aria-label "
                    + "written for screen readers alone quietly breaks.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (!e.actionable || e.disabled || e.ariaHidden) continue;
                if (e.ariaLabel == null || e.ariaLabel.isBlank()) continue;   // nothing overrode it
                String visible = normalise(e.visibleText);
                if (visible.isEmpty() || visible.length() > 60) continue;
                if (!visible.codePoints().anyMatch(Character::isLetter)) continue;

                String name = normalise(e.ariaLabel);
                if (name.contains(visible)) continue;

                out.add(finding(Severity.MEDIUM, String.format(
                        "%s reads \"%s\" but is named \"%s\", which does not contain it.",
                        e.tag, e.visibleText.trim(), e.ariaLabel.trim()))
                        .about(e)
                        .with("visibleText", e.visibleText.trim())
                        .with("accessibleName", e.ariaLabel.trim())
                        .suggest(String.format("Start the accessible name with the visible words: "
                                + "aria-label=\"%s ...\". Or drop the aria-label and let the "
                                + "visible text be the name, which is usually what you want.",
                                e.visibleText.trim())));
            }
            return out;
        }

        private static String normalise(String s) {
            return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        }
    }

    /** A dialog with no visible way out. */
    public static final class DialogWithoutEscape implements Rule {

        private static final Set<String> ESCAPE_WORDS = Set.of(
                "close", "cancel", "dismiss", "done", "back", "no thanks", "not now",
                "schlie", "abbrechen", "zur", "fermer", "annuler", "cerrar", "cancelar");

        @Override public String id() { return "dialog-without-escape"; }

        @Override public String title() { return "A dialog offers no visible way out"; }

        @Override public String rationale() {
            return "Users choose things by mistake and need a clearly marked emergency exit. A "
                    + "dialog that can only be left by completing it -- or by pressing an Escape "
                    + "key nobody advertised -- turns a misclick into being trapped, which is the "
                    + "feeling people describe as the site being broken.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Finding> out = new ArrayList<>();
            for (Elem dialog : s.elements) {
                if (!isDialog(dialog)) continue;

                boolean hasExit = s.elements.stream()
                        .filter(e -> e.actionable && !e.disabled)
                        .filter(e -> dialog.rect().contains(e.rect()))
                        .anyMatch(DialogWithoutEscape::looksLikeAnExit);
                if (hasExit) continue;

                out.add(finding(Severity.MEDIUM, String.format(
                        "%s contains no control named as a way out.", dialog.label()))
                        .about(dialog)
                        .with("role", dialog.role)
                        .suggest("Add a close control with a name in words, and make Escape do the "
                                + "same thing. A bare glyph in the corner is not discoverable by "
                                + "everyone who needs it."));
            }
            return out;
        }

        private static boolean isDialog(Elem e) {
            return "dialog".equals(e.role) || "alertdialog".equals(e.role) || "dialog".equals(e.tag);
        }

        private static boolean looksLikeAnExit(Elem e) {
            String name = (e.accessibleName == null ? "" : e.accessibleName)
                    .toLowerCase(Locale.ROOT);
            return ESCAPE_WORDS.stream().anyMatch(name::contains);
        }
    }

    /** Like items where only some sit on a shared surface. */
    public static final class InconsistentCommonRegion implements Rule {

        /** Share of the group that must carry the surface for its absence to be the odd case. */
        private static final double MAJORITY = 0.65;

        @Override public String id() { return "inconsistent-common-region"; }

        @Override public String title() { return "One of a set is missing the surface the rest share"; }

        @Override public String rationale() {
            return "Enclosing items in a shared region is one of the strongest grouping devices "
                    + "there is -- stronger than proximity, which is why cards work. An item that "
                    + "is part of the set but sits outside the region reads as not belonging, "
                    + "whatever the markup says. Reported only when an item is missing the "
                    + "surface its peers have; an item with an extra one is usually a state.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Consistency.Peers peers : Consistency.peerGroups(a.snapshot())) {
                if (peers.size() < 3) continue;
                if (!"row".equals(peers.kind()) && !"stack".equals(peers.kind())) continue;

                List<Elem> withSurface = peers.members().stream().filter(Elem::paintsSurface).toList();
                List<Elem> without = peers.members().stream().filter(e -> !e.paintsSurface()).toList();
                if (without.isEmpty()) continue;
                if (withSurface.size() / (double) peers.size() < MAJORITY) continue;
                if (without.size() > 2) continue;             // a second variant, not an omission

                for (Elem e : without) {
                    out.add(finding(Severity.LOW, String.format(
                            "%s sits in a set of %d where %d carry a surface and it does not.",
                            e.label(), peers.size(), withSurface.size()))
                            .about(e)
                            .with("peers", peers.size())
                            .with("withSurface", withSurface.size())
                            .suggest("Give it the same surface as its peers, or take the surface "
                                    + "off all of them. A set grouped by a shared region has to "
                                    + "include everything in the set."));
                }
            }
            return out;
        }
    }
}
