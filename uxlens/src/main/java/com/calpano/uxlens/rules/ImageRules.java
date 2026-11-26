package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;

/** Images: sharp, undistorted, loaded, and described. */
public final class ImageRules {

    private ImageRules() { }

    public static List<Rule> all() {
        return List.of(new UpscaledImage(), new DistortedImage(), new BrokenImage(), new MissingAlt());
    }

    /** A raster image displayed larger than it is. */
    public static final class UpscaledImage implements Rule {
        @Override public String id() { return "upscaled-image"; }

        @Override public String title() { return "An image is displayed larger than its source"; }

        @Override public String rationale() {
            return "An upscaled raster is soft, and soft imagery drags the perceived quality of "
                    + "everything around it down with it. On a 2x display the threshold is twice "
                    + "as demanding, which is why this shows up on retina screens first.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            double dpr = Math.max(1, a.viewport().deviceScaleFactor());
            for (Elem e : a.snapshot().elements) {
                if (e.img == null || e.img.naturalWidth <= 0 || !e.img.loaded) continue;
                if ("inline-svg".equals(e.img.src)) continue;      // vector, scales freely
                double factor = (e.w * dpr) / e.img.naturalWidth;
                if (factor <= a.config().maxImageUpscale) continue;
                out.add(finding(factor > 2 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s is %.0fpx wide (%.0f device px) from a %dpx source -- %.2fx upscaled.",
                        e.label(), e.w, e.w * dpr, e.img.naturalWidth, factor))
                        .about(e)
                        .with("displayedWidth", e.w)
                        .with("naturalWidth", e.img.naturalWidth)
                        .with("devicePixelRatio", dpr)
                        .with("upscaleFactor", Math.round(factor * 100) / 100.0)
                        .suggest(String.format("Serve the image at least %.0fpx wide, or add a "
                                        + "srcset entry for this density.", e.w * dpr)));
            }
            return out;
        }
    }

    /** An image squashed out of its own aspect ratio. */
    public static final class DistortedImage implements Rule {
        /** Aspect-ratio error that becomes visible, as a fraction. */
        private static final double TOLERANCE = 0.02;

        @Override public String id() { return "distorted-image"; }

        @Override public String title() { return "An image is stretched out of its aspect ratio"; }

        @Override public String rationale() {
            return "Distortion is immediately obvious on anything with a known shape -- a face, "
                    + "a logo, a product -- and it is almost always an accident of setting both "
                    + "width and height without object-fit.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (e.img == null || !e.img.loaded) continue;
                if (e.img.naturalWidth <= 0 || e.img.naturalHeight <= 0) continue;
                if (e.img.objectFit != null && !e.img.objectFit.equals("fill")) continue;

                double natural = e.img.naturalWidth / (double) e.img.naturalHeight;
                double shown = e.w / Math.max(1, e.h);
                double error = Math.abs(shown - natural) / natural;
                if (error <= TOLERANCE) continue;

                out.add(finding(error > 0.1 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s is shown at %.3f:1 but its source is %.3f:1, a %.0f%% distortion.",
                        e.label(), shown, natural, error * 100))
                        .about(e)
                        .with("displayedAspect", Math.round(shown * 1000) / 1000.0)
                        .with("naturalAspect", Math.round(natural * 1000) / 1000.0)
                        .with("errorPercent", Math.round(error * 100))
                        .suggest("Set only one dimension, or add object-fit:cover with a deliberate "
                                + "aspect-ratio on the box."));
            }
            return out;
        }
    }

    /** An image that did not load. */
    public static final class BrokenImage implements Rule {
        @Override public String id() { return "broken-image"; }

        @Override public String title() { return "An image failed to load"; }

        @Override public String rationale() {
            return "A broken image icon undoes more perceived quality than any amount of "
                    + "polish elsewhere adds. It is also the one defect a screenshot review "
                    + "catches reliably and a DOM review misses entirely.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (e.img == null || e.img.loaded || "inline-svg".equals(e.img.src)) continue;
                out.add(finding(Severity.HIGH, String.format("%s did not load (%s).",
                        e.label(), e.img.src == null || e.img.src.isBlank() ? "no src" : e.img.src))
                        .about(e)
                        .with("src", e.img.src)
                        .suggest("Fix the source, or remove the element so it does not reserve "
                                + "space for nothing."));
            }
            return out;
        }
    }

    /** An image with nothing to read in its place. */
    public static final class MissingAlt implements Rule {
        @Override public String id() { return "missing-alt"; }

        @Override public String title() { return "A content image has no text alternative"; }

        @Override public String rationale() {
            return "An image carrying meaning needs text carrying the same meaning. Decorative "
                    + "images need alt=\"\" so they are skipped -- a missing attribute is "
                    + "different from an empty one, and only the empty one is a decision.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (e.img == null || !"img".equals(e.tag)) continue;
                if (e.img.alt != null) continue;                            // "" is a valid decision
                if ("presentation".equals(e.role) || "none".equals(e.role)) continue;
                if (e.ariaLabel != null) continue;
                if (e.area() < 400) continue;                               // tracking pixels and spacers

                out.add(finding(Severity.MEDIUM, String.format(
                        "%s has no alt attribute (%.0fx%.0fpx).", e.label(), e.w, e.h))
                        .about(e)
                        .with("src", e.img.src)
                        .suggest("Add alt text describing what the image conveys, or alt=\"\" if it "
                                + "is decorative."));
            }
            return out;
        }
    }
}
