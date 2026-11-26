package com.calpano.uxlens.browser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Viewport specifications")
class ViewportTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({"desktop, 1440, 900", "mobile, 390, 844", "tablet, 834, 1112", "wide, 1920, 1080"})
    void presets_resolve_by_name(String name, int w, int h) {
        Viewport v = Viewport.parse(name);
        assertEquals(w, v.width());
        assertEquals(h, v.height());
    }

    @Test
    void preset_names_are_case_insensitive() {
        assertEquals(Viewport.DESKTOP, Viewport.parse("DESKTOP"));
    }

    @Test
    void explicit_sizes_parse() {
        Viewport v = Viewport.parse("1024x768");
        assertEquals(1024, v.width());
        assertEquals(768, v.height());
        assertEquals(1.0, v.deviceScaleFactor());
        assertEquals("1024x768", v.name());
    }

    @Test
    void a_density_can_be_requested() {
        assertEquals(2.0, Viewport.parse("390x844@2x").deviceScaleFactor());
        assertEquals(1560, Viewport.parse("390x844@2x").devicePx(780));
    }

    @Test
    void narrow_explicit_sizes_are_treated_as_touch_devices() {
        assertTrue(Viewport.parse("360x800").mobile());
        assertTrue(!Viewport.parse("1200x800").mobile());
    }

    @Test
    void a_bad_spec_names_the_alternatives() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Viewport.parse("phone-ish"));
        assertTrue(e.getMessage().contains("desktop"), e.getMessage());
        assertTrue(e.getMessage().contains("WIDTHxHEIGHT"), e.getMessage());
    }

    @Test
    void the_defaults_cover_a_wide_and_a_narrow_viewport() {
        assertEquals(2, Viewport.defaults().size());
        assertTrue(Viewport.defaults().get(0).width() > 1000);
        assertTrue(Viewport.defaults().get(1).width() < 500);
    }
}
