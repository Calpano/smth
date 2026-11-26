package com.calpano.uxlens;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rgba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every colour notation a stylesheet may use has to resolve.
 *
 * <p>This exists because of a silent failure that cost a great deal. The probe parsed
 * {@code rgb()} and hex with a regular expression, and modern design systems are written in
 * {@code oklch()}. On such a page it resolved 22 of 142 colours and returned null for the
 * rest, so every contrast check quietly did not run -- and one rule then fired confidently
 * on the few values that remained, reporting a peak contrast of 1.00:1 on a page whose
 * actual peak is 21:1. Silent non-execution that also produces a wrong answer is the worst
 * failure mode available, and the only defence against the next notation is a test that
 * names them.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Colour notations resolve to the right colour")
class ColorNotationTest {

    /** Channel distance within which two colours are the same colour for our purposes. */
    private static final double CLOSE_ENOUGH = 3.5;

    private static PageSnapshot measure(PageProbe probe, String declarations) {
        StringBuilder html = new StringBuilder("<body style=\"margin:0;background:#ffffff\">");
        for (String declaration : declarations.split(";;")) {
            html.append("<p style=\"font-size:16px;color:").append(declaration).append("\">x</p>");
        }
        return probe.captureHtml(html.append("</body>").toString(), Viewport.DESKTOP).snapshot();
    }

    private static List<Elem> texts(PageSnapshot s) { return s.textElements(); }

    @Test
    void oklch_resolves_to_the_same_srgb_the_browser_paints(PageProbe probe) {
        // The shadcn/Tailwind neutral ramp, which is what made this matter.
        PageSnapshot s = measure(probe,
                "oklch(0.141 0.005 285.823);;oklch(0.21 0.006 285.885);;"
                        + "oklch(0.967 0.001 286.375);;oklch(0.552 0.016 285.938)");
        String[] expected = {"#09090b", "#18181b", "#f4f4f5", "#71717b"};
        List<Elem> found = texts(s);
        assertEquals(expected.length, found.size());
        for (int i = 0; i < expected.length; i++) {
            Rgba got = found.get(i).fg();
            assertNotNull(got, "oklch must not resolve to null");
            assertTrue(got.distanceTo(Rgba.parse(expected[i])) <= CLOSE_ENOUGH,
                    "expected " + expected[i] + " but got " + got.hex());
        }
    }

    @Test
    void the_other_modern_notations_resolve_too(PageProbe probe) {
        PageSnapshot s = measure(probe,
                "lab(50% 40 59.5);;color(display-p3 1 0.5 0);;"
                        + "color-mix(in srgb, red 30%, blue);;chartreuse;;hsl(210 50% 40%)");
        String[] expected = {"#bf5700", "#ff7600", "#4d00b3", "#7fff00", "#336699"};
        List<Elem> found = texts(s);
        assertEquals(expected.length, found.size());
        for (int i = 0; i < expected.length; i++) {
            Rgba got = found.get(i).fg();
            assertNotNull(got, "notation " + i + " resolved to null");
            assertTrue(got.distanceTo(Rgba.parse(expected[i])) <= CLOSE_ENOUGH,
                    "expected " + expected[i] + " but got " + got.hex());
        }
    }

    @Test
    void an_out_of_gamut_colour_is_clamped_the_way_a_screen_clamps_it(PageProbe probe) {
        // oklch can name colours sRGB cannot show. What matters is what is painted.
        PageSnapshot s = measure(probe, "oklch(0.6 0.25 29)");
        Rgba got = texts(s).get(0).fg();
        assertTrue(got.distanceTo(Rgba.parse("#f20000")) <= CLOSE_ENOUGH, got.hex());
    }

    @Test
    void alpha_survives_the_round_trip(PageProbe probe) {
        PageSnapshot s = measure(probe,
                "rgba(200,100,50,0.1);;oklch(0.5 0.1 200 / 0.5);;rgba(200,100,50,1)");
        List<Elem> found = texts(s);
        assertEquals(0.1, found.get(0).fg().a(), 0.01);
        assertEquals(0.5, found.get(1).fg().a(), 0.01);
        assertEquals(1.0, found.get(2).fg().a(), 0.001);
        // and the colour itself is not mangled by the transparency
        assertTrue(found.get(0).fg().distanceTo(Rgba.parse("#c86432")) <= CLOSE_ENOUGH,
                "a translucent colour must keep its hue: " + found.get(0).fg().hex());
    }

    @Test
    void what_the_rules_actually_use_is_exact_even_at_low_alpha(PageProbe probe) {
        // At very low alpha an 8-bit sample cannot carry the declared colour precisely, and
        // that is fine: every rule works from the composite, which is exact. A contrast
        // threshold is 3.0, 4.5 or 7.0, so a hundredth of a ratio point cannot flip one.
        PageSnapshot s = measure(probe, "oklch(0.7 0.15 150 / 0.08)");
        Elem e = texts(s).get(0);
        Rgba ideal = new Rgba(76, 184, 106, 0.08).over(Rgba.WHITE);
        assertEquals(ideal.contrastRatio(Rgba.WHITE), e.textContrast(), 0.02);
    }

    @Test
    void an_unreadable_value_resolves_to_nothing_rather_than_to_black(PageProbe probe) {
        // The dangerous failure is not null; it is a confident wrong colour.
        assertNull(Rgba.parse("notacolour"));
        assertNull(Rgba.parse("rgb(this is not a colour)"));
        assertNull(Rgba.parse(""));
    }

    @Test
    void a_page_written_entirely_in_oklch_has_all_of_its_colours_read(PageProbe probe) {
        PageSnapshot s = probe.captureHtml("""
                <body style="margin:0;background:oklch(1 0 0)">
                  <style>
                    p{font-size:16px;color:oklch(0.141 0.005 285.823)}
                    .muted{color:oklch(0.552 0.016 285.938)}
                    .card{background:oklch(0.967 0.001 286.375);padding:16px}
                  </style>
                  <div class="card"><p>One</p><p class="muted">Two</p><p>Three</p></div>
                  <p>Four</p><p class="muted">Five</p>
                </body>""", Viewport.DESKTOP).snapshot();

        List<Elem> texts = texts(s);
        assertEquals(5, texts.size());
        for (Elem e : texts) {
            assertNotNull(e.fg(), e.text + " has no resolvable colour");
            assertTrue(e.textContrast() > 1.5,
                    e.text + " measured " + e.textContrast() + ":1, which means the background "
                            + "behind it was not resolved either");
        }
        // the card's surface must be read as the card's colour, not as the page's white
        Elem onCard = texts.get(0);
        assertTrue(onCard.bg().distanceTo(Rgba.parse("#f4f4f5")) <= CLOSE_ENOUGH,
                "effective background on the card was " + onCard.bg().hex());
    }
}
