package com.calpano.uxlens.analysis;

import com.calpano.uxlens.Fixtures;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures of a page rather than faults in it.
 *
 * <p>These are checked against images and layouts whose answer is known by construction,
 * because a figure that correlates only modestly with quality is worth nothing at all if it
 * does not even measure what it claims to.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Page measures")
class PageProfileTest {

    private static BufferedImage canvas(int w, int h, Color fill) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(fill);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    @Test
    void a_grey_page_is_not_colourful_and_a_rainbow_is() {
        double grey = PageProfile.colourfulness(canvas(200, 200, new Color(128, 128, 128)));
        assertTrue(grey < 5, "a flat grey measured " + grey);

        BufferedImage rainbow = canvas(200, 200, Color.WHITE);
        Graphics2D g = rainbow.createGraphics();
        Color[] colours = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA};
        for (int i = 0; i < colours.length; i++) {
            g.setColor(colours[i]);
            g.fillRect(i * 40, 0, 40, 200);
        }
        g.dispose();
        assertTrue(PageProfile.colourfulness(rainbow) > 60,
                "saturated bands measured " + PageProfile.colourfulness(rainbow));
    }

    @Test
    void a_flat_image_is_not_complex_and_noise_is() {
        assertTrue(PageProfile.visualComplexity(canvas(256, 256, Color.WHITE)) < 0.01);

        BufferedImage noise = canvas(256, 256, Color.WHITE);
        java.util.Random random = new java.util.Random(3);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) noise.setRGB(x, y, random.nextInt(0xffffff));
        }
        double complexity = PageProfile.visualComplexity(noise);
        assertTrue(complexity > 0.9, "noise measured " + complexity);
    }

    @Test
    void complexity_says_when_it_has_hit_its_ceiling() {
        BufferedImage noise = canvas(256, 256, Color.WHITE);
        java.util.Random random = new java.util.Random(5);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) noise.setRGB(x, y, random.nextInt(0xffffff));
        }
        PageProfile.Profile p = new PageProfile.Profile(
                0, PageProfile.visualComplexity(noise), 0, 1, 0, 1);
        assertTrue(p.complexitySaturated(), "noise should saturate the measure");
        assertEquals(true, p.asMap().get("complexitySaturated"));
    }

    @Test
    void the_tidy_fixture_is_emptier_better_balanced_and_less_colourful(PageProbe probe) {
        PageProfile.Profile clean = profileOf(probe, Fixtures.clean());
        PageProfile.Profile broken = profileOf(probe, Fixtures.broken());

        assertTrue(clean.inkCoverage() < broken.inkCoverage(),
                "clean " + clean.inkCoverage() + " vs broken " + broken.inkCoverage());
        assertTrue(clean.balance() > broken.balance(),
                "clean " + clean.balance() + " vs broken " + broken.balance());
        assertTrue(clean.colourfulness() < broken.colourfulness(),
                "clean " + clean.colourfulness() + " vs broken " + broken.colourfulness());
    }

    @Test
    void whitespace_is_the_complement_of_ink(PageProbe probe) {
        PageProfile.Profile p = profileOf(probe, Fixtures.clean());
        double ink = ((Number) p.asMap().get("inkCoverage")).doubleValue();
        double white = ((Number) p.asMap().get("whitespace")).doubleValue();
        assertEquals(1.0, ink + white, 0.002);
    }

    @Test
    void a_page_on_a_strict_grid_has_fewer_alignment_points_than_a_scattered_one(PageProbe probe) {
        String gridded = "<body style=\"margin:0;padding:0\">"
                + "<div style=\"margin:0;padding:0\">"
                + "<p style=\"margin:0;height:40px\">One</p><p style=\"margin:0;height:40px\">Two</p>"
                + "<p style=\"margin:0;height:40px\">Three</p><p style=\"margin:0;height:40px\">Four</p>"
                + "</div></body>";
        String scattered = "<body style=\"margin:0;padding:0\">"
                + "<p style=\"margin-left:3px;height:41px\">One</p>"
                + "<p style=\"margin-left:17px;height:39px\">Two</p>"
                + "<p style=\"margin-left:31px;height:43px\">Three</p>"
                + "<p style=\"margin-left:47px;height:37px\">Four</p></body>";

        PageProfile.Profile tidy = profileOfHtml(probe, gridded);
        PageProfile.Profile messy = profileOfHtml(probe, scattered);
        assertTrue(tidy.alignmentPoints() <= messy.alignmentPoints(),
                "gridded " + tidy.alignmentPoints() + " vs scattered " + messy.alignmentPoints());
    }

    private static PageProfile.Profile profileOf(PageProbe probe, String url) {
        return PageProfile.of(Analysis.of(probe.capture(url, Viewport.DESKTOP), Config.defaults()));
    }

    private static PageProfile.Profile profileOfHtml(PageProbe probe, String html) {
        return PageProfile.of(
                Analysis.of(probe.captureHtml(html, Viewport.DESKTOP), Config.defaults()));
    }
}
