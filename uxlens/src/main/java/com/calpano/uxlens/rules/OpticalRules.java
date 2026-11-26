package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Rgba;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.perception.Gray;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Optical versus geometric correctness.
 *
 * <p>This is the oldest distinction in graphic design and the one no layout engine knows
 * about: a shape centred by arithmetic is not centred to the eye. A play triangle in a
 * round button has to sit right of centre; a capital O has to overshoot the baseline; a
 * chevron pointing right needs its optical mass, not its bounding box, on the axis.
 *
 * <p>CSS centres bounding boxes, so it gets this wrong by default and no amount of DOM
 * inspection will reveal it. The only way to measure where a glyph's weight actually sits
 * is to look at the pixels it painted, which is what this does.
 */
public final class OpticalRules {

    private OpticalRules() { }

    public static List<Rule> all() { return List.of(new OpticalCentering()); }

    /** An icon that sits off the optical centre of the control holding it. */
    public static final class OpticalCentering implements Rule {

        /** Offset below this reads as centred; above it the eye starts to notice. */
        private static final double TOLERANCE_PX = 1.5;
        /** Offsets larger than this are a layout bug, not an optical one -- another rule's job. */
        private static final double MAX_PLAUSIBLE_PX = 12;
        /** Controls bigger than this are not icon buttons. */
        private static final double MAX_SIDE_PX = 80;
        /** Enough painted pixels to have a meaningful centre of mass. */
        private static final double MIN_INK = 8;
        /**
         * Fraction of the corner radius to stay clear of. A rectangle inset by
         * {@code r * (1 - sqrt(2)/2)} on each side is entirely inside a corner of radius r.
         */
        private static final double CORNER_INSET_RATIO = 1 - Math.sqrt(2) / 2;

        @Override public String id() { return "optical-centering"; }

        @Override public String title() { return "An icon is not optically centred in its control"; }

        @Override public String rationale() {
            return "CSS centres boxes; eyes centre mass. A triangle, chevron or any asymmetric "
                    + "glyph centred by its bounding box looks pushed to one side, and on a round "
                    + "button the error is obvious to everyone and explicable by nobody. The "
                    + "measurement is the centre of painted mass against the geometric centre, "
                    + "which can only be taken from the render.";
        }

        @Override public boolean needsPixels() { return true; }

        @Override public List<Finding> check(Analysis a) {
            Optional<BufferedImage> foldOpt = a.foldImage();
            if (foldOpt.isEmpty()) return List.of();
            BufferedImage fold = foldOpt.get();
            Gray luminance = Gray.luminanceOf(fold);
            double scale = a.scale();
            Rect viewport = a.snapshot().viewportRect();

            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (!isIconControl(a, e)) continue;
                if (!viewport.contains(e.rect())) continue;        // must be fully rendered in the fold

                double[] offset = inkOffset(luminance, e, scale);
                if (offset == null) continue;

                double worst = Math.max(Math.abs(offset[0]), Math.abs(offset[1]));
                if (worst <= TOLERANCE_PX || worst > MAX_PLAUSIBLE_PX) continue;

                out.add(finding(worst > 3 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "The painted content of %s sits %s of the centre of its %.0fx%.0fpx box.",
                        e.label(), describe(offset, " and "), e.w, e.h))
                        .about(e)
                        .with("offsetXPx", Math.round(offset[0] * 10) / 10.0)
                        .with("offsetYPx", Math.round(offset[1] * 10) / 10.0)
                        .suggest(String.format("Nudge it %s with padding rather than moving the "
                                        + "glyph; an asymmetric shape needs an asymmetric inset to "
                                        + "look centred.",
                                describe(new double[]{-offset[0], -offset[1]}, " and "))));
            }
            return out;
        }

        /** "2.3px right", or "2.3px right and 1.0px down" -- never an axis that is already true. */
        private static String describe(double[] offset, String join) {
            List<String> parts = new ArrayList<>();
            if (Math.abs(offset[0]) > TOLERANCE_PX / 2) {
                parts.add(String.format("%.1fpx %s", Math.abs(offset[0]),
                        offset[0] > 0 ? "right" : "left"));
            }
            if (Math.abs(offset[1]) > TOLERANCE_PX / 2) {
                parts.add(String.format("%.1fpx %s", Math.abs(offset[1]),
                        offset[1] > 0 ? "down" : "up"));
            }
            return parts.isEmpty() ? "off centre" : String.join(join, parts);
        }

        /** A small control whose content is a single icon or glyph, not a run of text. */
        private static boolean isIconControl(Analysis a, Elem e) {
            if (!e.interactive || e.disabled) return false;
            if (e.w > MAX_SIDE_PX || e.h > MAX_SIDE_PX || e.w < 8 || e.h < 8) return false;
            if (!e.paintsSurface()) return false;                  // needs a box to be centred in
            boolean glyph = e.hasOwnText() && e.text.codePointCount(0, e.text.length()) <= 2;
            boolean icon = !e.hasOwnText() && e.childCount <= 1;
            return glyph || icon;
        }

        /**
         * Centre of painted mass minus geometric centre, in CSS pixels.
         *
         * <p>Measured inside the border, so the frame's own symmetric ink does not drown out
         * the content it surrounds.
         */
        private static double[] inkOffset(Gray luminance, Elem e, double scale) {
            // Luma, not relative luminance: the pixels are measured in luma, and the two
            // scales differ enough that mixing them makes a whole surface register as ink.
            double reference = e.bg().luma();

            // Stay inside the border *and* inside any rounded corner. A pill button is a
            // square box painting a round surface, and the page showing through the corners
            // is ink that belongs to the page, not to the control: on a 48px circle it
            // outweighs the glyph three to one and drags every offset towards zero.
            double cornerInset = e.borderRadius * CORNER_INSET_RATIO;
            double left = e.x + e.borderLeft + 1 + cornerInset;
            double top = e.y + e.borderTop + 1 + cornerInset;
            double right = e.rect().right() - e.borderRight - 1 - cornerInset;
            double bottom = e.rect().bottom() - e.borderBottom - 1 - cornerInset;

            int x0 = (int) Math.round(left * scale);
            int y0 = (int) Math.round(top * scale);
            int x1 = (int) Math.round(right * scale);
            int y1 = (int) Math.round(bottom * scale);
            if (x1 - x0 < 4 || y1 - y0 < 4) return null;
            x0 = Math.max(0, x0); y0 = Math.max(0, y0);
            x1 = Math.min(luminance.width, x1); y1 = Math.min(luminance.height, y1);

            double total = 0, sumX = 0, sumY = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    double ink = Math.abs(luminance.v[y][x] - reference);
                    if (ink < 0.04) continue;                      // background, or its antialiasing
                    total += ink;
                    sumX += ink * (x + 0.5);
                    sumY += ink * (y + 0.5);
                }
            }
            if (total < MIN_INK) return null;

            double centroidX = sumX / total, centroidY = sumY / total;
            double geometricX = (x0 + x1) / 2.0, geometricY = (y0 + y1) / 2.0;
            return new double[]{(centroidX - geometricX) / scale, (centroidY - geometricY) / scale};
        }
    }
}
