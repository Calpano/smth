package com.calpano.uxlens.perception;

import java.awt.image.BufferedImage;

/**
 * Where a human eye lands first, computed from the pixels.
 *
 * <p>Implements spectral-residual saliency (Hou &amp; Zhang, CVPR 2007): the log
 * amplitude spectrum of a natural image is close to its own local average, so
 * whatever is left over after subtracting that average is the part of the image
 * that is <em>statistically surprising</em> -- which is what pre-attentive vision
 * is drawn to. It is a handful of FFTs, needs no model weights, and on UI
 * screenshots it reliably picks out the things a designer would call focal points:
 * a saturated button in a muted field, a large headline, an isolated logo.
 *
 * <p>This is the computation an LLM cannot do by looking at a screenshot, and the
 * reason to compute it here and hand the agent the answer.
 *
 * <p><b>A caveat the literature insists on.</b> Models of this family were derived on, and
 * validated against, photographs of natural scenes. Work on attention in graphic designs
 * and interfaces finds that they transfer poorly: a user interface is not a natural scene,
 * its statistics are dominated by text and flat regions, and what draws the eye in one is
 * substantially governed by learned convention -- where the navigation lives, what a button
 * looks like -- which no bottom-up model represents at all. Purpose-built models for designs
 * and visualisations do markedly better.
 *
 * <p>So this map should be read as a weak prior on pre-attentive pop-out, not as a
 * prediction of where a visitor will look. It is at its most trustworthy saying "this
 * region is the strongest contrast island on the page" and at its least trustworthy saying
 * "therefore it is what gets attention". The rules built on it are correspondingly
 * conservative: {@code primary-action-not-salient} requires both this and the measured
 * prominence ranking to agree before it reports anything.
 */
public final class Saliency {

    /** Spectral working resolution. 64x64 is the size the original paper uses. */
    private static final int WORK = 64;
    /**
     * Width of the border ring, in working pixels, whose values are tapered away.
     *
     * <p>The transform treats the image as periodic, so the inverse rings along the
     * borders. On a page with real content the ringing is swamped; on a plain one it is the
     * largest value in the map, and normalisation then promotes a corner artifact to a
     * fixation. Tapering the outer ring costs about 4% of each edge and removes it.
     */
    private static final int BORDER_TAPER = 3;
    /** Log floor, as a fraction of the largest amplitude in the spectrum. */
    private static final double AMPLITUDE_FLOOR = 1e-4;
    /** Longest side the retained map is upsampled to. A saliency map is a smooth, low
     *  frequency field, so a full-page-sized one would cost a lot of memory for nothing. */
    private static final int MAP_MAX_SIDE = 256;

    private final Gray map;
    private final int width, height;
    /** Map pixels per source pixel. */
    private final double mapScale;

    private Saliency(Gray map, int width, int height) {
        this.map = map;
        this.width = width;
        this.height = height;
        this.mapScale = map.width / (double) width;
    }

    /**
     * Computes the saliency map of a screenshot, upsampled back to the screenshot's size
     * and normalized to 0..1.
     */
    public static Saliency of(BufferedImage screenshot) {
        Gray lum = Gray.luminanceOf(screenshot);
        Gray small = lum.resizeTo(WORK, WORK);

        double[][] re = new double[WORK][WORK], im = new double[WORK][WORK];
        for (int y = 0; y < WORK; y++) System.arraycopy(small.v[y], 0, re[y], 0, WORK);
        Fft.transform2d(re, im, false);

        double[][] amp = new double[WORK][WORK], phase = new double[WORK][WORK];
        double maxAmp = 0;
        for (int y = 0; y < WORK; y++) {
            for (int x = 0; x < WORK; x++) {
                amp[y][x] = Math.hypot(re[y][x], im[y][x]);
                phase[y][x] = Math.atan2(im[y][x], re[y][x]);
                maxAmp = Math.max(maxAmp, amp[y][x]);
            }
        }
        if (maxAmp <= 0) return new Saliency(new Gray(8, 8), screenshot.getWidth(), screenshot.getHeight());

        // The floor under the log is relative to the spectrum, not absolute. A UI screenshot
        // has large flat regions, so many frequency bins are exactly zero; an absolute floor
        // of 1e-9 turns each of those into a log of -20 that dwarfs every real value and
        // leaves the residual describing nothing but the floor. Photographs, which the
        // original method was derived on, never contain exact zeros.
        final double floor = maxAmp * AMPLITUDE_FLOOR;
        double[][] logAmp = new double[WORK][WORK];
        for (int y = 0; y < WORK; y++) {
            for (int x = 0; x < WORK; x++) logAmp[y][x] = Math.log(amp[y][x] + floor);
        }

        // Whiten: divide each amplitude by the geometric mean of its frequency neighbourhood.
        // This is the spectral residual written multiplicatively, which keeps a zero bin at
        // zero instead of amplifying it. Neighbourhoods wrap, because frequency space does.
        double[][] whitened = new double[WORK][WORK];
        for (int y = 0; y < WORK; y++) {
            for (int x = 0; x < WORK; x++) {
                double sum = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        sum += logAmp[wrap(y + dy)][wrap(x + dx)];
                    }
                }
                whitened[y][x] = Math.min(amp[y][x] / Math.exp(sum / 9.0), maxAmp);
            }
        }
        // Drop the constant term: a uniform offset says nothing about where to look.
        whitened[0][0] = 0;

        for (int y = 0; y < WORK; y++) {
            for (int x = 0; x < WORK; x++) {
                re[y][x] = whitened[y][x] * Math.cos(phase[y][x]);
                im[y][x] = whitened[y][x] * Math.sin(phase[y][x]);
            }
        }
        Fft.transform2d(re, im, true);

        Gray sal = new Gray(WORK, WORK);
        for (int y = 0; y < WORK; y++) {
            for (int x = 0; x < WORK; x++) {
                sal.v[y][x] = (re[y][x] * re[y][x] + im[y][x] * im[y][x]) * taper(x, y);
            }
        }
        Gray smoothed = sal.blur(2.5).normalized();
        int sw = screenshot.getWidth(), sh = screenshot.getHeight();
        double shrink = Math.min(1.0, MAP_MAX_SIDE / (double) Math.max(sw, sh));
        int mw = Math.max(8, (int) Math.round(sw * shrink));
        int mh = Math.max(8, (int) Math.round(sh * shrink));
        return new Saliency(smoothed.resizeTo(mw, mh).normalized(), sw, sh);
    }

    /** The retained map. Its resolution is capped; use {@link #meanIn} for source coordinates. */
    public Gray map() { return map; }

    public int width() { return width; }

    public int height() { return height; }

    /** Mean saliency inside a box given in <em>screenshot</em> pixels. */
    public double meanIn(double x, double y, double w, double h) {
        return map.meanIn((int) Math.round(x * mapScale), (int) Math.round(y * mapScale),
                Math.max(1, (int) Math.round(w * mapScale)), Math.max(1, (int) Math.round(h * mapScale)));
    }

    /** Peak saliency inside a box given in <em>screenshot</em> pixels. */
    public double peakIn(double x, double y, double w, double h) {
        return map.maxIn((int) Math.round(x * mapScale), (int) Math.round(y * mapScale),
                Math.max(1, (int) Math.round(w * mapScale)), Math.max(1, (int) Math.round(h * mapScale)));
    }

    /**
     * Predicted gaze order: repeatedly take the strongest remaining point and suppress a
     * disc around it ("inhibition of return"), the way the eye does not re-fixate what it
     * has just looked at.
     *
     * @param count  how many fixations to predict
     * @param radius suppression radius in screenshot pixels; roughly the foveal span
     */
    public java.util.List<Fixation> scanpath(int count, double radius) {
        return scanpath(count, radius, (x, y) -> null);
    }

    /**
     * Gaze order with region-aware inhibition.
     *
     * <p>A disc alone is not enough: a 1400px headline is wider than any plausible foveal
     * span, so a disc-only walk fixates the same headline four times and reports nothing.
     * Given a lookup that names the element under a point, the whole element is suppressed
     * once it has been looked at -- which is what "the eye has read that" means.
     *
     * @param regionAt returns the region to suppress for a point in screenshot pixels, or null
     */
    public java.util.List<Fixation> scanpath(int count, double radius, RegionAt regionAt) {
        Gray work = map.copy();
        double rMap = Math.max(2, radius * mapScale);
        java.util.List<Fixation> path = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int bx = -1, by = -1;
            double best = -1;
            for (int y = 0; y < work.height; y++) {
                for (int x = 0; x < work.width; x++) {
                    if (work.v[y][x] > best) { best = work.v[y][x]; bx = x; by = y; }
                }
            }
            if (bx < 0 || best <= 0.02) break;
            int srcX = (int) Math.round(bx / mapScale), srcY = (int) Math.round(by / mapScale);
            path.add(new Fixation(i + 1, srcX, srcY, round(best)));

            com.calpano.uxlens.model.Rect region = regionAt.at(srcX, srcY);
            if (region != null) {
                int x0 = (int) Math.floor(region.x() * mapScale), y0 = (int) Math.floor(region.y() * mapScale);
                int x1 = (int) Math.ceil(region.right() * mapScale), y1 = (int) Math.ceil(region.bottom() * mapScale);
                for (int y = Math.max(0, y0); y < Math.min(work.height, y1); y++) {
                    for (int x = Math.max(0, x0); x < Math.min(work.width, x1); x++) work.v[y][x] = 0;
                }
            }
            int r = (int) Math.ceil(rMap);
            for (int y = Math.max(0, by - r); y < Math.min(work.height, by + r); y++) {
                for (int x = Math.max(0, bx - r); x < Math.min(work.width, bx + r); x++) {
                    if (Math.hypot(x - bx, y - by) <= rMap) work.v[y][x] = 0;
                }
            }
        }
        return path;
    }

    /** Wraps a frequency index, since the spectrum is periodic. */
    private static int wrap(int i) { return ((i % WORK) + WORK) % WORK; }

    /** Raised-cosine ramp from 0 at the image border to 1 at {@link #BORDER_TAPER} in. */
    private static double taper(int x, int y) {
        return ramp(Math.min(Math.min(x, y), Math.min(WORK - 1 - x, WORK - 1 - y)));
    }

    private static double ramp(int distanceToEdge) {
        if (distanceToEdge >= BORDER_TAPER) return 1;
        return 0.5 - 0.5 * Math.cos(Math.PI * (distanceToEdge + 0.5) / BORDER_TAPER);
    }

    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }

    /** Names the region to suppress once a point has been fixated. */
    @FunctionalInterface
    public interface RegionAt {
        /** @return the region in screenshot pixels, or null to suppress only the disc */
        com.calpano.uxlens.model.Rect at(int x, int y);
    }

    /** One predicted fixation, in screenshot pixels. */
    public record Fixation(int order, int x, int y, double strength) { }
}
