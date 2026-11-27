package com.calpano.uxlens.perception;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * The oldest trick in the designer's book, automated: screw up your eyes until the text
 * is unreadable, and whatever still stands out <em>is</em> the visual hierarchy.
 *
 * <p>Blurring strips the vision encoder of text it would otherwise read instead of
 * looking, leaving only Gestalt grouping and contrast islands. A multimodal model that
 * is bad at judging a crisp screenshot is markedly better at judging this.
 */
public final class SquintTest {

    private SquintTest() { }

    /**
     * Grayscale and heavily blurred: pure visual weight.
     *
     * @param sigma blur radius in pixels; scale it with the viewport
     *              ({@code width / 120} is a good default)
     */
    public static BufferedImage squint(BufferedImage src, double sigma) {
        Gray blurred = Gray.luminanceOf(src).blur(sigma);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                // back to display gamma so the result looks like a squint, not a washed-out plate
                int g = (int) Math.round(Math.pow(Math.max(0, Math.min(1, blurred.v[y][x])), 1 / 2.2) * 255);
                out.setRGB(x, y, (g << 16) | (g << 8) | g);
            }
        }
        return out;
    }

    /** The default blur for a given render width. */
    public static BufferedImage squint(BufferedImage src) {
        return squint(src, Math.max(4, src.getWidth() / 120.0));
    }

    /**
     * Blurred but keeping color, so saturation-driven attention survives. Useful when the
     * question is "does the brand color land on the primary action".
     */
    public static BufferedImage squintColor(BufferedImage src, double sigma) {
        Gray r = channel(src, 16).blur(sigma);
        Gray g = channel(src, 8).blur(sigma);
        Gray b = channel(src, 0).blur(sigma);
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, new Color(c(r.v[y][x]), c(g.v[y][x]), c(b.v[y][x])).getRGB());
            }
        }
        return out;
    }

    /**
     * Counts the distinct blobs of visual weight left after squinting -- a page that blurs
     * into one undifferentiated mass has no hierarchy, and a page that blurs into forty
     * blobs has no grouping.
     *
     * @param threshold luminance distance from the page's own surface that counts as a blob
     */
    /**
     * Luminance distance from the page's own surface that counts as visual weight.
     *
     * <p>Lives here rather than at the call site so that everything asking "how many masses
     * does this page have" asks the same question. Measuring it at one threshold and
     * checking it at another is how a measurement comes to look dead when it is only being
     * asked something absurd -- half the luminance range admits near-black alone.
     */
    public static final double BLOB_THRESHOLD = 0.06;

    /** Counts blobs at {@link #BLOB_THRESHOLD}. */
    public static int countWeightBlobs(BufferedImage squinted) {
        return countWeightBlobs(squinted, BLOB_THRESHOLD);
    }

    public static int countWeightBlobs(BufferedImage squinted, double threshold) {
        Gray g = Gray.luminanceOf(squinted);
        // The baseline is the page's surface, which is the median rather than the mean: on a
        // page with heavy ink coverage the mean sits between surface and ink, and then the
        // background itself counts as a region of visual weight.
        double base = median(g);
        boolean[][] seen = new boolean[g.height][g.width];
        int step = Math.max(1, g.width / 300);   // sample; blobs are large by construction
        int sampled = ((g.height + step - 1) / step) * ((g.width + step - 1) / step);
        int minBlobSamples = Math.max(2, (int) Math.round(sampled * MIN_BLOB_SHARE));
        int blobs = 0;
        for (int y = 0; y < g.height; y += step) {
            for (int x = 0; x < g.width; x += step) {
                if (seen[y][x] || Math.abs(g.v[y][x] - base) < threshold) continue;
                // A speck is not a region of visual weight, however much it differs.
                if (flood(g, seen, x, y, base, threshold, step) >= minBlobSamples) blobs++;
            }
        }
        return blobs;
    }

    /** Share of the image a blob must cover to count as a region of visual weight. */
    private static final double MIN_BLOB_SHARE = 0.004;

    /** Median luminance, sampled; the colour the page is mostly made of. */
    private static double median(Gray g) {
        int step = Math.max(1, Math.min(g.width, g.height) / 64);
        java.util.List<Double> values = new java.util.ArrayList<>();
        for (int y = 0; y < g.height; y += step) {
            for (int x = 0; x < g.width; x += step) values.add(g.v[y][x]);
        }
        java.util.Collections.sort(values);
        return values.isEmpty() ? 0 : values.get(values.size() / 2);
    }

    private static int flood(Gray g, boolean[][] seen, int sx, int sy,
                             double base, double threshold, int step) {
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        q.add(new int[]{sx, sy});
        seen[sy][sx] = true;
        boolean bright = g.v[sy][sx] > base;
        int size = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            size++;
            for (int[] d : new int[][]{{step, 0}, {-step, 0}, {0, step}, {0, -step}}) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= g.width || ny >= g.height || seen[ny][nx]) continue;
                if (Math.abs(g.v[ny][nx] - base) < threshold) continue;
                if ((g.v[ny][nx] > base) != bright) continue;
                seen[ny][nx] = true;
                q.add(new int[]{nx, ny});
            }
        }
        return size;
    }

    private static Gray channel(BufferedImage img, int shift) {
        Gray g = new Gray(img.getWidth(), img.getHeight());
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) g.v[y][x] = ((img.getRGB(x, y) >> shift) & 0xff) / 255.0;
        }
        return g;
    }

    private static int c(double v) { return Math.max(0, Math.min(255, (int) Math.round(v * 255))); }
}
