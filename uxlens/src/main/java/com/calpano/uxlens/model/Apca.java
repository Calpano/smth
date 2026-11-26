package com.calpano.uxlens.model;

/**
 * Perceptual contrast by APCA, as a second opinion on the WCAG 2 ratio.
 *
 * <p>The WCAG 2 contrast ratio is a simple ratio of relative luminances, and the research
 * literature has been clear for some years that it mispredicts perceived contrast in
 * specific, systematic ways: it is too lenient on light grey text on white, too strict on
 * light text on dark backgrounds, and it takes no account of font size or weight beyond a
 * single "large text" cutoff. APCA (Accessible Perceptual Contrast Algorithm), the
 * candidate method developed for WCAG 3, models the asymmetry between dark-on-light and
 * light-on-dark and soft-clamps near black, where the ratio formula misbehaves most.
 *
 * <p>This is deliberately <em>not</em> used to pass or fail anything. WCAG 2 is what teams
 * are audited against and what the law refers to, so it stays the rule. APCA is reported
 * alongside it, and the useful signal is where the two <em>disagree</em> -- most often dark
 * mode, where a pair that clears 4.5:1 can still be genuinely hard to read.
 *
 * <p>Implements APCA 0.1.9. The constants are verified against the two published anchors
 * (black on white and white on black) by {@code ApcaTest}; an implementation that cannot
 * reproduce those is not worth reporting.
 */
public final class Apca {

    // APCA 0.1.9 constants.
    private static final double S_TRC = 2.4;
    private static final double R_CO = 0.2126729, G_CO = 0.7151522, B_CO = 0.0721750;

    private static final double NORM_BG = 0.56, NORM_TXT = 0.57;
    private static final double REV_TXT = 0.62, REV_BG = 0.65;

    private static final double BLACK_THRESHOLD = 0.022, BLACK_CLAMP = 1.414;
    private static final double SCALE_BOW = 1.14, SCALE_WOB = 1.14;
    private static final double LO_BOW_OFFSET = 0.027, LO_WOB_OFFSET = 0.027;
    private static final double DELTA_Y_MIN = 0.0005, LO_CLIP = 0.1;

    private Apca() { }

    /**
     * Lightness contrast of text against its background.
     *
     * <p>Roughly -108 to +106. Positive means dark text on a light background, negative the
     * reverse; the sign carries meaning, so compare {@link Math#abs}. As a rough guide the
     * method's own guidance puts body text at |Lc| 75 and large text at 60, but those are
     * not thresholds this project enforces.
     *
     * @param text       the composited text colour, opaque
     * @param background the colour painted behind it, opaque
     */
    public static double lightnessContrast(Rgba text, Rgba background) {
        double yText = screenLuminance(text);
        double yBackground = screenLuminance(background);

        yText = softClampBlack(yText);
        yBackground = softClampBlack(yBackground);

        if (Math.abs(yBackground - yText) < DELTA_Y_MIN) return 0;

        double contrast;
        if (yBackground > yText) {                              // dark text on a light ground
            contrast = (Math.pow(yBackground, NORM_BG) - Math.pow(yText, NORM_TXT)) * SCALE_BOW;
            contrast = contrast < LO_CLIP ? 0 : contrast - LO_BOW_OFFSET;
        } else {                                                 // light text on a dark ground
            contrast = (Math.pow(yBackground, REV_BG) - Math.pow(yText, REV_TXT)) * SCALE_WOB;
            contrast = contrast > -LO_CLIP ? 0 : contrast + LO_WOB_OFFSET;
        }
        return contrast * 100;
    }

    /**
     * Screen luminance: the sRGB coefficients applied to a simple 2.4 power curve.
     *
     * <p>Not the piecewise sRGB transfer function that {@link Rgba#relativeLuminance()}
     * uses. APCA specifies this simpler curve, and mixing the two would silently change
     * every result.
     */
    private static double screenLuminance(Rgba c) {
        return R_CO * Math.pow(c.r() / 255.0, S_TRC)
                + G_CO * Math.pow(c.g() / 255.0, S_TRC)
                + B_CO * Math.pow(c.b() / 255.0, S_TRC);
    }

    /** Lifts near-black values, where small luminance differences stop being perceptible. */
    private static double softClampBlack(double y) {
        return y >= BLACK_THRESHOLD ? y : y + Math.pow(BLACK_THRESHOLD - y, BLACK_CLAMP);
    }

    /**
     * True when APCA and the WCAG 2 ratio disagree about whether a pair is readable.
     *
     * <p>The disagreements are the interesting part. A pair clearing 4.5:1 with an |Lc|
     * well under 60 is the classic light-text-on-dark case the ratio formula flatters.
     *
     * @param wcagRatio   the WCAG 2 contrast ratio for the same pair
     * @param largeText   whether WCAG's large-text allowance applies
     */
    public static boolean disagreesWithWcag(double lc, double wcagRatio, boolean largeText) {
        boolean wcagPasses = wcagRatio >= (largeText ? 3.0 : 4.5);
        boolean apcaPasses = Math.abs(lc) >= (largeText ? 60 : 75);
        return wcagPasses != apcaPasses;
    }
}
