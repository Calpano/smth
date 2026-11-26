package com.calpano.uxlens.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * APCA, checked against the values that can actually be sourced.
 *
 * <p>Only the two published anchors are treated as external truth. Everything else here is
 * a property of the method rather than a remembered number -- the sign convention, the
 * asymmetry between dark-on-light and light-on-dark, monotonicity. That asymmetry is the
 * whole reason the method exists and the thing a ratio of luminances cannot express.
 */
@DisplayName("APCA perceptual contrast")
class ApcaTest {

    private static double lc(String text, String background) {
        return Apca.lightnessContrast(Rgba.parse(text), Rgba.parse(background));
    }

    @Test
    void reproduces_the_published_anchors() {
        assertEquals(106.04, lc("#000000", "#ffffff"), 0.02);
        assertEquals(-107.88, lc("#ffffff", "#000000"), 0.02);
    }

    @Test
    void the_sign_says_which_way_round_the_pair_is() {
        assertTrue(lc("#000000", "#ffffff") > 0, "dark text on light ground is positive");
        assertTrue(lc("#ffffff", "#000000") < 0, "light text on dark ground is negative");
    }

    @Test
    void a_pair_is_not_symmetric_which_is_the_point_of_the_method() {
        // A WCAG ratio is identical whichever way round the pair is; perception is not, and
        // modelling that asymmetry is why APCA exists at all.
        double darkOnLight = Math.abs(lc("#888888", "#ffffff"));
        double lightOnDark = Math.abs(lc("#ffffff", "#888888"));
        assertTrue(Math.abs(darkOnLight - lightOnDark) > 3,
                "expected the two directions to differ, got " + darkOnLight + " and " + lightOnDark);

        Rgba grey = Rgba.parse("#888888"), white = Rgba.WHITE;
        assertEquals(grey.contrastRatio(white), white.contrastRatio(grey), 1e-9,
                "the WCAG ratio, by contrast, is symmetric");
    }

    @Test
    void identical_colours_have_no_contrast() {
        assertEquals(0, lc("#345678", "#345678"), 0.001);
    }

    @Test
    void darker_text_on_white_reads_as_more_contrast() {
        double light = Math.abs(lc("#bbbbbb", "#ffffff"));
        double mid = Math.abs(lc("#888888", "#ffffff"));
        double dark = Math.abs(lc("#333333", "#ffffff"));
        assertTrue(light < mid && mid < dark,
                "expected monotonic growth, got " + light + ", " + mid + ", " + dark);
    }

    @Test
    void near_black_is_soft_clamped_rather_than_running_away() {
        // The ratio formula's +0.05 makes very dark pairs behave oddly; APCA clamps instead.
        double a = Math.abs(lc("#000000", "#0a0a0a"));
        assertTrue(a < 10, "near-black pairs should report almost no contrast, got " + a);
    }

    @Test
    void it_disagrees_with_wcag_exactly_where_the_literature_says_it_should() {
        // Light text on a dark ground is the case the ratio formula flatters.
        Rgba text = Rgba.parse("#767676"), background = Rgba.parse("#000000");
        double ratio = text.contrastRatio(background);
        double lightness = Apca.lightnessContrast(text, background);
        assertTrue(ratio >= 4.5, "this pair clears WCAG AA at " + ratio);
        assertTrue(Math.abs(lightness) < 75, "but APCA puts it at " + lightness);
        assertTrue(Apca.disagreesWithWcag(lightness, ratio, false));
    }

    @Test
    void it_agrees_with_wcag_on_the_easy_cases() {
        Rgba black = Rgba.BLACK, white = Rgba.WHITE;
        assertFalse(Apca.disagreesWithWcag(
                Apca.lightnessContrast(black, white), black.contrastRatio(white), false));
    }
}
