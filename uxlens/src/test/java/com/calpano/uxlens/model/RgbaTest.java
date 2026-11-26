package com.calpano.uxlens.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Colour maths matches the WCAG reference values")
class RgbaTest {

    @Test
    void black_on_white_is_the_maximum_ratio() {
        assertEquals(21.0, Rgba.BLACK.contrastRatio(Rgba.WHITE), 0.01);
    }

    @Test
    void a_colour_against_itself_is_one_to_one() {
        Rgba c = Rgba.parse("#3366cc");
        assertEquals(1.0, c.contrastRatio(c), 0.0001);
    }

    /** Values checked against the WebAIM contrast checker. */
    @ParameterizedTest(name = "{0} on white = {1}:1")
    @CsvSource({
            "#767676, 4.54",
            "#595959, 7.00",
            "#bbbbbb, 1.92",
            "#0b4fd4, 6.81",
            "#ff0000, 3.998",
    })
    void known_ratios_against_white(String hex, double expected) {
        assertEquals(expected, Rgba.parse(hex).contrastRatio(Rgba.WHITE), 0.02);
    }

    @Test
    void contrast_is_symmetric() {
        Rgba a = Rgba.parse("#123456"), b = Rgba.parse("#fedcba");
        assertEquals(a.contrastRatio(b), b.contrastRatio(a), 1e-9);
    }

    @ParameterizedTest(name = "parses {0}")
    @CsvSource({
            "'#fff', 255, 255, 255, 1",
            "'#000000', 0, 0, 0, 1",
            "'#ff000080', 255, 0, 0, 0.502",
            "'rgb(10, 20, 30)', 10, 20, 30, 1",
            "'rgba(10,20,30,0.5)', 10, 20, 30, 0.5",
            "'rgba(10 20 30 / 0.25)', 10, 20, 30, 0.25",
    })
    void parses_the_forms_a_browser_emits(String input, double r, double g, double b, double a) {
        Rgba c = Rgba.parse(input);
        assertEquals(r, c.r(), 0.5);
        assertEquals(g, c.g(), 0.5);
        assertEquals(b, c.b(), 0.5);
        assertEquals(a, c.a(), 0.01);
    }

    @Test
    void unparseable_input_is_null_rather_than_a_guess() {
        assertNull(Rgba.parse(null));
        assertNull(Rgba.parse("transparent"));
        assertNull(Rgba.parse("chartreuse"));
        assertNull(Rgba.parse(""));
    }

    @Test
    void half_transparent_black_over_white_is_mid_grey() {
        Rgba composited = new Rgba(0, 0, 0, 0.5).over(Rgba.WHITE);
        assertEquals(127.5, composited.r(), 0.5);
        assertEquals(1.0, composited.a(), 0.0001);
    }

    @Test
    void compositing_is_what_makes_the_contrast_correct() {
        // 50% black text on white measures under 4:1 -- it fails AA -- while the declared
        // colour implies 21:1. This is the gap the probe's compositing closes.
        double declared = Rgba.BLACK.contrastRatio(Rgba.WHITE);
        double actual = new Rgba(0, 0, 0, 0.5).over(Rgba.WHITE).contrastRatio(Rgba.WHITE);
        assertEquals(21.0, declared, 0.01);
        assertEquals(3.98, actual, 0.02);
    }

    @Test
    void saturation_separates_a_brand_colour_from_a_grey() {
        assertEquals(0.0, Rgba.parse("#808080").saturation(), 0.001);
        assertTrue(Rgba.parse("#ff0000").saturation() > 0.99);
    }

    @Test
    void near_identical_colours_are_a_short_distance_apart() {
        assertTrue(Rgba.parse("#1a1a1a").distanceTo(Rgba.parse("#1b1b1b")) < 2);
        assertTrue(Rgba.parse("#000000").distanceTo(Rgba.parse("#ffffff")) > 400);
    }

    @Test
    void hex_round_trips() {
        assertEquals("#3366cc", Rgba.parse("rgb(51, 102, 204)").hex());
    }
}
