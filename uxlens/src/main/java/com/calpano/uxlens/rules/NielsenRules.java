package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Corners of Nielsen's heuristics that a rendered page genuinely answers.
 *
 * <p>Most of the ten are about a sequence of interactions and are out of reach here; {@link
 * StandardsCoverage} says which and why. The three below are different: each is a specific,
 * checkable consequence of a broad principle, visible in one render, and each is among the
 * most common defects in production forms.
 *
 * <p>They are deliberately narrow. "Help users recover from errors" is not implemented --
 * what is implemented is "this field is marked invalid and says nothing about why", which
 * is one failure mode of it. Claiming the heuristic rather than the corner would be the
 * dishonest move.
 */
public final class NielsenRules {

    private NielsenRules() { }

    public static List<Rule> all() {
        return List.of(new PlaceholderAsLabel(), new InvalidFieldWithoutMessage(),
                new RequiredFieldUnmarked());
    }

    /** A field whose only label is the hint that disappears when you use it. */
    public static final class PlaceholderAsLabel implements Rule {

        @Override public String id() { return "placeholder-as-label"; }

        @Override public String title() { return "A field is labelled only by its placeholder"; }

        @Override public String rationale() {
            return "Recognition rather than recall, stated as precisely as it can be: a "
                    + "placeholder vanishes the moment the field is typed into, so the user is "
                    + "asked to remember what they are filling in while they fill it in. Anyone "
                    + "interrupted mid-form comes back to a page of filled boxes with nothing "
                    + "saying what any of them are. It also fails review in a mock-up, where the "
                    + "placeholder is always visible and the form looks fine.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (!isTextField(e) || e.disabled) continue;
                if (!e.isNamedOnlyByPlaceholder()) continue;

                out.add(finding(Severity.MEDIUM, String.format(
                        "%s has no label; it is named only by its placeholder, \"%s\".",
                        e.label(), e.placeholder))
                        .about(e)
                        .with("placeholder", e.placeholder)
                        .with("nameSource", e.nameSource)
                        .suggest(String.format("Add a visible label reading \"%s\" and keep the "
                                + "placeholder for an example of the format, or drop it. A label "
                                + "that disappears is not a label.", e.placeholder)));
            }
            return out;
        }

        static boolean isTextField(Elem e) {
            if ("textarea".equals(e.tag) || "select".equals(e.tag)) return true;
            if (!"input".equals(e.tag)) return false;
            String type = e.type == null ? "text" : e.type.toLowerCase(Locale.ROOT);
            return !List.of("button", "submit", "reset", "image", "hidden", "checkbox", "radio")
                    .contains(type);
        }
    }

    /** A field marked wrong that does not say what is wrong. */
    public static final class InvalidFieldWithoutMessage implements Rule {

        @Override public String id() { return "invalid-field-without-message"; }

        @Override public String title() { return "A field is marked invalid but says nothing"; }

        @Override public String rationale() {
            return "Telling someone they are wrong without telling them why leaves them "
                    + "guessing, and guessing at a form is where people give up. A red border "
                    + "is a diagnosis with no explanation; for anyone who cannot see the red "
                    + "border it is not even that.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Finding> out = new ArrayList<>();
            for (Elem e : s.elements) {
                if (!e.ariaInvalid || e.disabled) continue;
                if (e.hasDescription()) continue;
                // A message sitting right under the field counts, even if nothing links them.
                if (hasAdjacentText(s, e)) continue;

                out.add(finding(Severity.MEDIUM, String.format(
                        "%s is marked invalid with no message explaining why.", e.label()))
                        .about(e)
                        .with("ariaInvalid", true)
                        .suggest("Put the reason next to the field and point at it with "
                                + "aria-describedby. \"Enter a date in the past\" beats \"invalid "
                                + "input\", which beats a red border and silence."));
            }
            return out;
        }

        /** Text immediately below the field, which a sighted user will read as the message. */
        private static boolean hasAdjacentText(PageSnapshot s, Elem field) {
            return s.textElements().stream().anyMatch(t ->
                    t.id != field.id
                            && t.rect().columnOverlap(field.rect()) > 0.3
                            && t.y >= field.rect().bottom() - 2
                            && t.y - field.rect().bottom() < 24);
        }
    }

    /** A field that must be filled in without saying so. */
    public static final class RequiredFieldUnmarked implements Rule {

        /** Words that mark a field as required, in the languages the corpus is likely to meet. */
        private static final List<String> MARKERS =
                List.of("*", "required", "obligatorisch", "pflichtfeld", "erforderlich",
                        "obligatoire", "requerido", "obligatorio");

        @Override public String id() { return "required-field-unmarked"; }

        @Override public String title() { return "A required field is not marked as required"; }

        @Override public String rationale() {
            return "Error prevention is cheaper than error recovery, and the cheapest form of "
                    + "it is saying which fields must be filled in before the user submits. A "
                    + "field that is required only in the validator teaches the user by "
                    + "rejecting them.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            boolean allRequired = s.elements.stream()
                    .filter(PlaceholderAsLabel::isTextField)
                    .allMatch(e -> e.required);
            long fields = s.elements.stream().filter(PlaceholderAsLabel::isTextField).count();
            // When every field is required, marking each one adds nothing; the form says it once.
            if (allRequired && fields > 1) return List.of();

            List<Finding> out = new ArrayList<>();
            for (Elem e : s.elements) {
                if (!PlaceholderAsLabel.isTextField(e) || !e.required || e.disabled) continue;
                if (isMarked(e) || markedNearby(s, e)) continue;

                out.add(finding(Severity.LOW, String.format(
                        "%s must be filled in, and nothing visible says so.", e.label()))
                        .about(e)
                        .with("accessibleName", e.accessibleName)
                        .suggest("Mark it in the label -- an asterisk with a key, or the word "
                                + "required. Marking the optional ones instead works too, and "
                                + "reads better on a form where most fields are required."));
            }
            return out;
        }

        private static boolean isMarked(Elem e) {
            String name = (e.accessibleName == null ? "" : e.accessibleName)
                    .toLowerCase(Locale.ROOT);
            return MARKERS.stream().anyMatch(name::contains);
        }

        /** A marker in the label beside or above the field, where forms usually put it. */
        private static boolean markedNearby(PageSnapshot s, Elem field) {
            return s.textElements().stream().anyMatch(t -> {
                if (t.rect().gapTo(field.rect()) > 32) return false;
                String text = t.text.toLowerCase(Locale.ROOT);
                return MARKERS.stream().anyMatch(text::contains);
            });
        }
    }
}
