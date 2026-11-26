package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Rgba;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Colour: readable, distinguishable, and few enough to be a palette. */
public final class ColorRules {

    private ColorRules() { }

    public static List<Rule> all() {
        return List.of(new TextContrast(), new ControlBorderContrast(),
                new PaletteSprawl(), new NearDuplicateColors(), new ContrastModelDisagreement());
    }

    /**
     * Where the WCAG 2 ratio and the perceptual model disagree about the same pair.
     *
     * <p>Not a failure, and deliberately not reported as one: WCAG 2 is what teams are
     * audited against. But the research literature is clear that its ratio of luminances
     * mispredicts perceived contrast in systematic ways, and this says where that is
     * happening on this page -- which is overwhelmingly light text on dark backgrounds.
     */
    public static final class ContrastModelDisagreement implements Rule {

        /** Report at most this many; the pattern matters more than the instances. */
        private static final int MAX_REPORTED = 8;

        @Override public String id() { return "contrast-model-disagreement"; }

        @Override public String title() { return "WCAG and the perceptual model disagree"; }

        @Override public String rationale() {
            return "The WCAG 2 contrast ratio is a ratio of relative luminances, and it is "
                    + "known to mispredict perceived contrast: too lenient on light grey on "
                    + "white, too flattering to light text on dark grounds, and blind to font "
                    + "weight. APCA, the candidate method for WCAG 3, models that asymmetry. "
                    + "Where the two agree there is nothing to discuss; where they disagree, the "
                    + "pair clears the audit and may still be hard to read, which is worth "
                    + "knowing before dark mode ships.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (out.size() >= MAX_REPORTED) break;
                double ratio = e.textContrast();
                if (ratio <= 0) continue;
                Rgba fg = e.fg(), bg = e.bg();
                if (fg == null) continue;

                double lightness = com.calpano.uxlens.model.Apca.lightnessContrast(fg.over(bg), bg);
                if (!com.calpano.uxlens.model.Apca.disagreesWithWcag(
                        lightness, ratio, e.isLargeText())) continue;

                boolean wcagPasses = ratio >= (e.isLargeText() ? 3.0 : 4.5);
                out.add(finding(Severity.INFO, String.format(
                        "%s measures %.2f:1 by WCAG 2 (%s) but Lc %.0f perceptually (%s). %s",
                        e.label(), ratio, wcagPasses ? "a pass" : "a fail",
                        lightness, Math.abs(lightness) >= (e.isLargeText() ? 60 : 75)
                                ? "readable" : "below the perceptual guideline",
                        bg.relativeLuminance() < fg.over(bg).relativeLuminance()
                                ? "Light text on a dark ground, which is where the ratio formula "
                                  + "is most optimistic."
                                : "Dark text on a light ground."))
                        .about(e)
                        .with("wcagRatio", Math.round(ratio * 100) / 100.0)
                        .with("apcaLc", Math.round(lightness * 10) / 10.0)
                        .with("wcagPasses", wcagPasses)
                        .suggest("Treat WCAG as the requirement and this as a warning. Where they "
                                + "disagree on light-on-dark, raising the text lightness a little "
                                + "usually satisfies both."));
            }
            return out;
        }
    }

    /** Text that does not meet WCAG contrast against what is actually painted behind it. */
    public static final class TextContrast implements Rule {
        @Override public String id() { return "text-contrast"; }

        @Override public String title() { return "Text contrast is below the required ratio"; }

        @Override public String rationale() {
            return "Contrast is the one perceptual property with a settled numeric threshold, so "
                    + "there is no reason to guess at it. The background is resolved by compositing "
                    + "through transparent ancestors, which is where hand-checking usually goes "
                    + "wrong: a 60%-opacity label on a tinted card is not the colour either "
                    + "declaration says.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                double ratio = e.textContrast();
                if (ratio <= 0) continue;
                double required = a.config().requiredContrast(e.isLargeText());
                if (ratio >= required - 0.005) continue;

                Rgba fg = e.fg(), bg = e.bg();
                Severity sev = ratio < 2 ? Severity.HIGH : ratio < required * 0.8 ? Severity.MEDIUM : Severity.LOW;
                Finding f = finding(sev, String.format(
                        "%s has a contrast ratio of %.2f:1 against its background (%s on %s); "
                                + "%.1f:1 is required for %s text at %.0fpx/%d.",
                        e.label(), ratio, fg == null ? "?" : fg.hex(), bg.hex(), required,
                        e.isLargeText() ? "large" : "normal", e.fontSize, e.fontWeight))
                        .about(e)
                        .with("ratio", Math.round(ratio * 100) / 100.0)
                        .with("required", required)
                        .with("foreground", fg == null ? null : fg.hex())
                        .with("background", bg.hex())
                        .with("fontSize", e.fontSize)
                        .with("fontWeight", e.fontWeight)
                        .with("largeText", e.isLargeText())
                        .with("apcaLc", Math.round(
                                com.calpano.uxlens.model.Apca.lightnessContrast(
                                        fg.over(bg), bg) * 10) / 10.0)
                        .suggest(suggestion(fg, bg, required, e));
                if (e.backgroundUncertain) {
                    f.severity = sev == Severity.HIGH ? Severity.MEDIUM : Severity.LOW;
                    f.with("backgroundUncertain", true);
                    f.message += " A background image or gradient sits behind it, so the measured "
                            + "background is the colour layer only.";
                }
                out.add(f);
            }
            return out;
        }

        /** Names a concrete colour that would pass, rather than telling the reader to think. */
        private static String suggestion(Rgba fg, Rgba bg, double required, Elem e) {
            if (fg == null) return String.format("Raise the contrast to at least %.1f:1.", required);
            boolean darken = bg.relativeLuminance() > 0.5;
            Rgba fixed = solveFor(fg, bg, required, darken);
            String base = fixed == null
                    ? String.format("Raise the contrast to at least %.1f:1.", required)
                    : String.format("Use %s (%.2f:1) or darker.", fixed.hex(), fixed.contrastRatio(bg));
            if (!darken && fixed != null) {
                base = String.format("Use %s (%.2f:1) or lighter.", fixed.hex(), fixed.contrastRatio(bg));
            }
            if (e.isLargeText()) return base;
            return base + String.format(" Alternatively set it at 24px or bold 19px, where %.1f:1 suffices.",
                    required == 7.0 ? 4.5 : 3.0);
        }

        /** Walks the foreground toward black or white until it clears the ratio. */
        private static Rgba solveFor(Rgba fg, Rgba bg, double required, boolean darken) {
            Rgba target = darken ? Rgba.BLACK : Rgba.WHITE;
            for (double t = 0.05; t <= 1.0; t += 0.05) {
                Rgba c = new Rgba(fg.r() + (target.r() - fg.r()) * t,
                        fg.g() + (target.g() - fg.g()) * t,
                        fg.b() + (target.b() - fg.b()) * t, 1);
                if (c.contrastRatio(bg) >= required) return c;
            }
            return null;
        }
    }

    /** Form controls whose outline is too faint to find. */
    public static final class ControlBorderContrast implements Rule {
        @Override public String id() { return "control-border-contrast"; }

        @Override public String title() { return "A control's boundary is too faint to see"; }

        @Override public String rationale() {
            return "A text field you cannot find is worse than an ugly one. WCAG 1.4.11 asks for "
                    + "3:1 on the parts of a control that convey its shape -- and a 'minimal' input "
                    + "with a #eee border on white is at 1.2:1.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                boolean control = "input".equals(e.tag) || "select".equals(e.tag) || "textarea".equals(e.tag);
                if (!control || e.disabled) continue;
                // A shadow can be the boundary. Tailwind's ring utilities draw it that way and
                // so does every design system built on them; the border really is transparent
                // and the control really is perfectly visible. The shadow's own contrast is not
                // readable from the computed string, so this is a deliberate miss rather than a
                // confident wrong answer.
                if (e.boxShadow != null && !e.boxShadow.isBlank() && !"none".equals(e.boxShadow)) {
                    continue;
                }

                Rgba bg = e.bg();
                Rgba own = Rgba.parse(e.backgroundColor);
                Rgba border = Rgba.parse(e.borderColor);

                // The control is findable if either its fill or its border separates from the page.
                double fillContrast = own != null && own.a() > 0.05 ? own.over(bg).contrastRatio(bg) : 1;
                double borderContrast = e.hasVisibleBorder() && border != null
                        ? border.over(bg).contrastRatio(bg) : 1;
                double best = Math.max(fillContrast, borderContrast);
                if (best >= a.config().contrastNonText - 0.005) continue;

                out.add(finding(best < 1.5 ? Severity.HIGH : Severity.MEDIUM, String.format(
                        "%s has no visible boundary: its fill reaches %.2f:1 and its border %.2f:1 "
                                + "against the surrounding %s, below the %.1f:1 required.",
                        e.label(), fillContrast, borderContrast, bg.hex(), a.config().contrastNonText))
                        .about(e)
                        .with("fillContrast", Math.round(fillContrast * 100) / 100.0)
                        .with("borderContrast", Math.round(borderContrast * 100) / 100.0)
                        .with("required", a.config().contrastNonText)
                        .suggest("Give the control a 3:1 border, or a fill that separates it from the "
                                + "page. An underline-only field needs the same ratio."));
            }
            return out;
        }
    }

    /** More colours than a palette. */
    public static final class PaletteSprawl implements Rule {
        /** Distinct colours above which the page has no palette. */
        private static final int LIMIT = 16;

        @Override public String id() { return "palette-sprawl"; }

        @Override public String title() { return "The page uses more colours than a palette"; }

        @Override public String rationale() {
            return "A designed interface runs on a small palette: a few neutrals, one or two "
                    + "accents, and semantic colours for state. Dozens of near-identical greys is "
                    + "the signature of colours chosen per component.";
        }

        @Override public List<Finding> check(Analysis a) {
            Map<String, Integer> counts = colorCounts(a);
            if (counts.size() <= LIMIT) return List.of();
            List<String> most = counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10).map(Map.Entry::getKey).toList();
            return List.of(finding(Severity.LOW, String.format(
                    "The page paints text and surfaces in %d distinct colours.", counts.size()))
                    .with("distinctColors", counts.size())
                    .with("mostUsed", most)
                    .with("limit", LIMIT)
                    .suggest("Define the palette as CSS custom properties and reference tokens; "
                            + "most of these are probably meant to be the same colour."));
        }

        static Map<String, Integer> colorCounts(Analysis a) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Elem e : a.snapshot().elements) {
                if (e.hasOwnText()) {
                    Rgba fg = e.fg();
                    if (fg != null && fg.a() > 0.05) counts.merge(fg.over(e.bg()).hex(), 1, Integer::sum);
                }
                Rgba bgc = Rgba.parse(e.backgroundColor);
                if (bgc != null && bgc.a() > 0.05) counts.merge(bgc.over(Rgba.WHITE).hex(), 1, Integer::sum);
            }
            return counts;
        }
    }

    /** Two colours so close that only a colour picker can tell them apart. */
    public static final class NearDuplicateColors implements Rule {
        /** RGB distance below which two colours are the same colour, badly managed. */
        private static final double SAME = 10;

        @Override public String id() { return "near-duplicate-colors"; }

        @Override public String title() { return "Two colours are visually identical"; }

        @Override public String rationale() {
            return "#1a1a1a and #1b1b1b are the same colour to every reader, but they are two "
                    + "values to maintain, and they mean one of them was typed by hand.";
        }

        @Override public List<Finding> check(Analysis a) {
            Map<String, Integer> counts = PaletteSprawl.colorCounts(a);
            List<String> hexes = List.copyOf(counts.keySet());
            List<Finding> out = new ArrayList<>();
            for (int i = 0; i < hexes.size() && out.size() < 8; i++) {
                for (int j = i + 1; j < hexes.size() && out.size() < 8; j++) {
                    Rgba x = Rgba.parse(hexes.get(i)), y = Rgba.parse(hexes.get(j));
                    if (x == null || y == null) continue;
                    double d = x.distanceTo(y);
                    if (d == 0 || d > SAME) continue;
                    out.add(finding(Severity.INFO, String.format(
                            "%s and %s are visually identical (RGB distance %.1f) and are used "
                                    + "%d and %d times.",
                            hexes.get(i), hexes.get(j), d, counts.get(hexes.get(i)), counts.get(hexes.get(j))))
                            .with("colorA", hexes.get(i)).with("colorB", hexes.get(j))
                            .with("distance", Math.round(d * 10) / 10.0)
                            .suggest("Keep one and reference it from a token."));
                }
            }
            return out;
        }
    }
}
