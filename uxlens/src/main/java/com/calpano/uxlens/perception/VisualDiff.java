package com.calpano.uxlens.perception;

import java.awt.image.BufferedImage;

/**
 * Perceptual comparison of two renders -- the regression half of visual QA.
 *
 * <p>Raw pixel equality is useless (antialiasing alone defeats it), so this reports
 * both a pixel ratio with a perceptual tolerance and a structural similarity score,
 * plus a blurred-domain score that answers the question a designer actually asks:
 * "did the <em>layout</em> move, or did only the detail change?"
 */
public final class VisualDiff {

    private VisualDiff() { }

    /**
     * @param changedPixelRatio  fraction of pixels differing beyond {@code tolerance}
     * @param ssim               structural similarity, 1.0 = identical
     * @param layoutSimilarity   SSIM after a heavy blur; drops only when blocks move
     * @param diffImage          red where the images differ
     */
    public record Result(double changedPixelRatio, double ssim, double layoutSimilarity,
                         BufferedImage diffImage, int width, int height) {

        /** True when the two renders would look the same to a reviewer. */
        public boolean looksIdentical() { return changedPixelRatio < 0.001 && ssim > 0.995; }

        /** True when detail changed but the layout blocks stayed put. */
        public boolean layoutStable() { return layoutSimilarity > 0.98; }
    }

    /**
     * Compares two renders of the same page.
     *
     * @param tolerance per-channel difference, 0..1, below which a pixel counts as equal
     *                  (0.02 absorbs antialiasing and font hinting)
     * @throws IllegalArgumentException if the images differ in size
     */
    public static Result compare(BufferedImage a, BufferedImage b, double tolerance) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IllegalArgumentException("Cannot compare renders of different sizes: "
                    + a.getWidth() + "x" + a.getHeight() + " vs " + b.getWidth() + "x" + b.getHeight()
                    + ". Capture both at the same viewport, and expect full-page heights to differ "
                    + "when content height changes.");
        }
        int w = a.getWidth(), h = a.getHeight();
        BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        long changed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pa = a.getRGB(x, y), pb = b.getRGB(x, y);
                double d = Math.max(Math.max(
                                Math.abs(((pa >> 16) & 0xff) - ((pb >> 16) & 0xff)),
                                Math.abs(((pa >> 8) & 0xff) - ((pb >> 8) & 0xff))),
                        Math.abs((pa & 0xff) - (pb & 0xff))) / 255.0;
                if (d > tolerance) {
                    changed++;
                    diff.setRGB(x, y, 0xff2040);
                } else {
                    int g = (int) (((pa >> 16 & 0xff) * 0.2 + (pa >> 8 & 0xff) * 0.7 + (pa & 0xff) * 0.1) * 0.35 + 150);
                    g = Math.min(255, g);
                    diff.setRGB(x, y, (g << 16) | (g << 8) | g);
                }
            }
        }
        Gray ga = Gray.luminanceOf(a), gb = Gray.luminanceOf(b);
        double ssim = ssim(ga, gb);
        double blur = Math.max(4, w / 100.0);
        double layout = ssim(ga.blur(blur), gb.blur(blur));
        return new Result(changed / (double) (w * h), ssim, layout, diff, w, h);
    }

    public static Result compare(BufferedImage a, BufferedImage b) { return compare(a, b, 0.02); }

    /** Global SSIM over 8x8 windows, the usual constants for 0..1 data. */
    private static double ssim(Gray a, Gray b) {
        final double c1 = 0.01 * 0.01, c2 = 0.03 * 0.03;
        int win = 8;
        double total = 0;
        int windows = 0;
        for (int y = 0; y + win <= a.height; y += win) {
            for (int x = 0; x + win <= a.width; x += win) {
                double ma = 0, mb = 0;
                for (int j = 0; j < win; j++) for (int i = 0; i < win; i++) { ma += a.v[y + j][x + i]; mb += b.v[y + j][x + i]; }
                int n = win * win;
                ma /= n; mb /= n;
                double va = 0, vb = 0, cov = 0;
                for (int j = 0; j < win; j++) {
                    for (int i = 0; i < win; i++) {
                        double da = a.v[y + j][x + i] - ma, db = b.v[y + j][x + i] - mb;
                        va += da * da; vb += db * db; cov += da * db;
                    }
                }
                va /= n - 1; vb /= n - 1; cov /= n - 1;
                total += ((2 * ma * mb + c1) * (2 * cov + c2)) / ((ma * ma + mb * mb + c1) * (va + vb + c2));
                windows++;
            }
        }
        return windows == 0 ? 1 : total / windows;
    }
}
