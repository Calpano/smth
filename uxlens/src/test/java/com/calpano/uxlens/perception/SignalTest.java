package com.calpano.uxlens.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The perceptual layer, checked against images whose answer is known by construction.
 *
 * <p>Saliency and the squint test are the two pieces of this project that cannot be
 * verified by reading the output, so they are verified against synthetic images where
 * the right answer is a fact rather than a judgement.
 */
@DisplayName("Perceptual signals behave on images whose answer is known")
class SignalTest {

    private static BufferedImage canvas(int w, int h, Color bg) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static void box(BufferedImage img, Color c, int x, int y, int w, int h) {
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    @Test
    void blur_preserves_the_average_brightness() {
        BufferedImage img = canvas(128, 128, Color.GRAY);
        box(img, Color.WHITE, 0, 0, 64, 128);
        Gray g = Gray.luminanceOf(img);
        double before = g.sum() / (g.width * (double) g.height);
        double after = g.blur(6).sum() / (g.width * (double) g.height);
        assertEquals(before, after, 0.02);
    }

    @Test
    void resize_is_reversible_to_within_interpolation_error() {
        BufferedImage img = canvas(64, 64, Color.GRAY);
        box(img, Color.WHITE, 16, 16, 32, 32);
        Gray g = Gray.luminanceOf(img);
        Gray roundTripped = g.resizeTo(32, 32).resizeTo(64, 64);
        assertEquals(g.meanIn(0, 0, 64, 64), roundTripped.meanIn(0, 0, 64, 64), 0.02);
    }

    @Test
    void normalising_a_flat_image_does_not_divide_by_zero() {
        Gray flat = Gray.luminanceOf(canvas(32, 32, Color.GRAY));
        assertEquals(0.0, flat.normalized().sum(), 1e-9);
    }

    @Test
    void saliency_finds_the_odd_one_out() {
        // A field of identical grey squares with one bright square: the bright one is
        // statistically surprising, which is exactly what spectral residual detects.
        BufferedImage img = canvas(256, 256, new Color(130, 130, 130));
        for (int y = 16; y < 240; y += 64) {
            for (int x = 16; x < 240; x += 64) box(img, new Color(110, 110, 110), x, y, 32, 32);
        }
        box(img, Color.WHITE, 144, 144, 32, 32);

        Saliency saliency = Saliency.of(img);
        double odd = saliency.peakIn(144, 144, 32, 32);
        double typical = saliency.peakIn(16, 16, 32, 32);
        assertTrue(odd > typical,
                "the odd square should be the most salient: " + odd + " vs " + typical);
    }

    @Test
    void the_first_fixation_lands_on_the_most_salient_region() {
        BufferedImage img = canvas(256, 256, new Color(128, 128, 128));
        box(img, Color.WHITE, 180, 40, 40, 40);
        var path = Saliency.of(img).scanpath(3, 40);
        assertTrue(path.size() >= 1, "expected at least one fixation");
        var first = path.get(0);
        assertTrue(first.x() > 150 && first.x() < 250 && first.y() > 10 && first.y() < 110,
                "first fixation at (" + first.x() + ", " + first.y() + ") missed the bright box");
    }

    @Test
    void fixations_do_not_re_visit_a_region_already_looked_at() {
        BufferedImage img = canvas(256, 256, new Color(128, 128, 128));
        box(img, Color.WHITE, 30, 30, 40, 40);
        box(img, Color.BLACK, 180, 180, 40, 40);
        var path = Saliency.of(img).scanpath(2, 60);
        assertEquals(2, path.size());
        double apart = Math.hypot(path.get(0).x() - path.get(1).x(), path.get(0).y() - path.get(1).y());
        assertTrue(apart > 60, "inhibition of return failed; fixations were " + apart + "px apart");
    }

    @Test
    void squinting_removes_detail_but_keeps_the_blocks() {
        BufferedImage img = canvas(400, 200, Color.WHITE);
        for (int x = 10; x < 190; x += 6) box(img, Color.BLACK, x, 80, 3, 12);   // "text"
        box(img, Color.BLACK, 220, 60, 150, 80);                                 // a solid block

        BufferedImage squinted = SquintTest.squint(img, 8);
        Gray g = Gray.luminanceOf(squinted);
        // the text has blurred to a mid grey, the solid block is still dark
        assertTrue(g.meanIn(10, 80, 180, 12) > 0.35, "text should wash out when blurred");
        assertTrue(g.meanIn(240, 70, 100, 60) < 0.2, "a solid block should survive the blur");
    }

    @Test
    void a_blank_page_has_no_regions_of_visual_weight() {
        BufferedImage blank = canvas(400, 300, Color.WHITE);
        assertEquals(0, SquintTest.countWeightBlobs(SquintTest.squint(blank), 0.06));
    }

    @Test
    void three_separated_blocks_count_as_three_regions() {
        BufferedImage img = canvas(600, 200, Color.WHITE);
        box(img, Color.BLACK, 20, 40, 120, 120);
        box(img, Color.BLACK, 240, 40, 120, 120);
        box(img, Color.BLACK, 460, 40, 120, 120);
        assertEquals(3, SquintTest.countWeightBlobs(SquintTest.squint(img, 6), 0.06));
    }

    @Test
    void identical_renders_compare_as_identical() {
        BufferedImage img = canvas(200, 120, Color.WHITE);
        box(img, Color.BLUE, 20, 20, 80, 40);
        var result = VisualDiff.compare(img, deepCopy(img));
        assertEquals(0.0, result.changedPixelRatio(), 1e-9);
        assertTrue(result.looksIdentical());
        assertTrue(result.layoutStable());
    }

    @Test
    void a_moved_block_is_reported_as_the_layout_moving() {
        BufferedImage before = canvas(200, 120, Color.WHITE);
        box(before, Color.BLUE, 20, 20, 80, 40);
        BufferedImage after = canvas(200, 120, Color.WHITE);
        box(after, Color.BLUE, 90, 20, 80, 40);

        var result = VisualDiff.compare(before, after);
        assertTrue(result.changedPixelRatio() > 0.05, "ratio was " + result.changedPixelRatio());
        assertTrue(!result.looksIdentical() && !result.layoutStable());
    }

    @Test
    void comparing_different_sizes_says_why_rather_than_throwing_an_index_error() {
        BufferedImage a = canvas(100, 100, Color.WHITE);
        BufferedImage b = canvas(100, 120, Color.WHITE);
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> VisualDiff.compare(a, b));
        assertTrue(e.getMessage().contains("100x100"), e.getMessage());
    }

    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
