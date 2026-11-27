package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.perception.Gray;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The two Gestalt principles the other families leave unfinished.
 *
 * <p>Proximity, similarity and continuity are each covered several times over by rules that
 * exist for other reasons. Figure and ground, and closure, are not -- and they are the two
 * that fail hardest when they fail. Text that will not separate from what it is printed on
 * cannot be read at all, and a container that does not contain its contents stops being a
 * container.
 */
public final class GestaltRules {

    private GestaltRules() { }

    public static List<Rule> all() {
        return List.of(new TextOverTexture(), new ContentEscapesContainer());
    }

    /** Text set over a busy background, where figure and ground will not separate. */
    public static final class TextOverTexture implements Rule {

        /** Luminance spread around the text, above which the ground is not a flat colour. */
        private static final double BUSY = 0.16;
        /** Spread above which it is not merely patterned but photographic. */
        private static final double VERY_BUSY = 0.26;
        /** Band sampled around the text, in CSS pixels. */
        private static final double BAND_PX = 10;
        /** Enough text to be a reading problem rather than a decorative flourish. */
        private static final int MIN_CHARS = 12;

        @Override public String id() { return "text-over-texture"; }

        @Override public String title() { return "Text sits on a background it cannot separate from"; }

        @Override public String rationale() {
            return "Figure and ground is the first thing vision does. Text over a photograph or "
                    + "a gradient has no single background to contrast against: it is legible "
                    + "over the dark part of the image and gone over the light part, and which "
                    + "part falls where depends on the viewport, the crop and whether the image "
                    + "loaded at all. A contrast ratio measured against the colour layer says "
                    + "nothing useful here, which is exactly why this needs the pixels.";
        }

        @Override public boolean needsPixels() { return true; }

        @Override public List<Finding> check(Analysis a) {
            Optional<BufferedImage> foldOpt = a.foldImage();
            if (foldOpt.isEmpty()) return List.of();
            Gray luminance = Gray.luminanceOf(foldOpt.get());
            double scale = a.scale();
            Rect viewport = a.snapshot().viewportRect();

            List<Elem> images = a.snapshot().elements.stream()
                    .filter(e -> e.img != null && e.img.loaded && e.area() > 2000)
                    .toList();

            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().textElements()) {
                if (e.textLength < MIN_CHARS) continue;
                // Two ways to end up printed on a picture: a CSS background behind an
                // ancestor, or text positioned over an <img>. The second is the commoner hero
                // pattern and sets no background flag at all, so it has to be found
                // geometrically.
                if (!e.backgroundUncertain && !sitsOverAnImage(e, images)) continue;
                if (!viewport.contains(e.rect())) continue;

                double spread = groundSpread(luminance, e, scale);
                if (spread < BUSY) continue;

                out.add(finding(spread >= VERY_BUSY ? Severity.HIGH : Severity.MEDIUM, String.format(
                        "%s is set over a background whose luminance varies by %.2f around it, so "
                                + "there is no one colour for it to contrast against.",
                        e.label(), spread))
                        .about(e)
                        .with("groundSpread", Math.round(spread * 100) / 100.0)
                        .with("declaredContrast", Math.round(e.textContrast() * 100) / 100.0)
                        .suggest("Put the text on a solid panel, add a scrim between it and the "
                                + "image, or move it off the image. A text shadow helps least "
                                + "where the ground is lightest, which is where the trouble is."));
            }
            return out;
        }

        /** True when most of the text's box falls within a loaded image's box. */
        private static boolean sitsOverAnImage(Elem text, List<Elem> images) {
            double area = text.area();
            if (area <= 0) return false;
            return images.stream().anyMatch(img ->
                    img.id != text.id && img.rect().intersectionArea(text.rect()) > area * 0.6);
        }

        /**
         * Luminance spread of the band immediately around the text, which stands in for the
         * ground the text is printed on. Measured beside the glyphs rather than under them,
         * since what is under them cannot be seen.
         */
        private static double groundSpread(Gray luminance, Elem e, double scale) {
            Rect box = e.rect();
            Rect outer = box.inflate(BAND_PX);
            int x0 = (int) Math.max(0, Math.round(outer.x() * scale));
            int y0 = (int) Math.max(0, Math.round(outer.y() * scale));
            int x1 = (int) Math.min(luminance.width, Math.round(outer.right() * scale));
            int y1 = (int) Math.min(luminance.height, Math.round(outer.bottom() * scale));

            int ix0 = (int) Math.round(box.x() * scale), iy0 = (int) Math.round(box.y() * scale);
            int ix1 = (int) Math.round(box.right() * scale), iy1 = (int) Math.round(box.bottom() * scale);

            List<Double> samples = new ArrayList<>();
            int step = Math.max(1, (x1 - x0) / 120);
            for (int y = y0; y < y1; y += step) {
                for (int x = x0; x < x1; x += step) {
                    if (x >= ix0 && x < ix1 && y >= iy0 && y < iy1) continue;   // skip the glyphs
                    samples.add(luminance.v[y][x]);
                }
            }
            if (samples.size() < 20) return 0;

            double mean = 0;
            for (double v : samples) mean += v;
            mean /= samples.size();
            double variance = 0;
            for (double v : samples) variance += (v - mean) * (v - mean);
            return Math.sqrt(variance / samples.size());
        }
    }

    /** A container whose contents will not stay inside it. */
    public static final class ContentEscapesContainer implements Rule {

        /** Overshoot below this is a shadow, a focus ring or a rounding, not an escape. */
        private static final double TOLERANCE_PX = 2;
        /** Big enough to be a panel rather than a highlight around a word. */
        private static final double MIN_AREA = 3000;

        @Override public String id() { return "content-escapes-container"; }

        @Override public String title() { return "Content breaks out of the surface holding it"; }

        @Override public String rationale() {
            return "Closure: a drawn boundary is read as enclosing everything inside it, and "
                    + "anything crossing that boundary stops being part of the group. A card "
                    + "whose text runs past its own edge reads as broken rather than as "
                    + "deliberate, and it is the first thing to appear when content is longer "
                    + "than the design assumed -- a translation, a long name, a wrapped price.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Finding> out = new ArrayList<>();

            for (Elem container : s.elements) {
                if (!container.paintsSurface() || container.area() < MIN_AREA) continue;
                if (container.fixedLayer) continue;
                // A container that scrolls or clips is doing its job; nothing escapes it.
                if (clips(container)) continue;

                Rect bounds = container.rect();
                for (Elem child : s.elements) {
                    if (child.parentId != container.id) continue;
                    if (child.fixedLayer || child.rectIsUnionOfLineBoxes()) continue;
                    if (!child.hasOwnText() && child.img == null) continue;

                    double overRight = child.rect().right() - bounds.right();
                    double overBottom = child.rect().bottom() - bounds.bottom();
                    double overLeft = bounds.x() - child.x;
                    double overTop = bounds.y() - child.y;
                    double worst = Math.max(Math.max(overRight, overBottom),
                            Math.max(overLeft, overTop));
                    if (worst <= TOLERANCE_PX) continue;

                    out.add(finding(worst > 12 ? Severity.HIGH : Severity.MEDIUM, String.format(
                            "%s extends %.0fpx past the edge of the surface that holds it (%s).",
                            child.label(), worst, container.label()))
                            .about(child, container)
                            .with("overshootPx", Math.round(worst))
                            .suggest("Let the container grow with its contents, or constrain the "
                                    + "content. If the overflow is deliberate, make it look "
                                    + "deliberate -- a deliberate overlap reads differently from "
                                    + "a box that could not hold what was put in it."));
                }
            }
            return out;
        }

        private static boolean clips(Elem e) {
            return isClipping(e.overflowX) || isClipping(e.overflowY);
        }

        private static boolean isClipping(String overflow) {
            return overflow != null && !overflow.equals("visible");
        }
    }
}
