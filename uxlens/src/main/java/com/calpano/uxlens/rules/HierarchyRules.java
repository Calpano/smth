package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Prominence;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.perception.Saliency;
import com.calpano.uxlens.perception.SquintTest;

import java.util.List;
import java.util.Optional;

/**
 * Visual hierarchy: whether what the page <em>means</em> matches what the page <em>does</em>.
 *
 * <p>These are the rules that need the perceptual layer. A model reading the DOM sees a
 * clean document outline; the measured prominence ranking and the predicted gaze path
 * routinely disagree with it, and the disagreement is the finding.
 */
public final class HierarchyRules {

    private HierarchyRules() { }

    public static List<Rule> all() {
        return List.of(new CompetingFocalPoints(), new InvertedHierarchy(),
                new PrimaryActionNotFound(), new SquintCohesion());
    }

    /** Two elements fighting for rank one. */
    public static final class CompetingFocalPoints implements Rule {
        @Override public String id() { return "competing-focal-points"; }

        @Override public String title() { return "Two elements compete to be looked at first"; }

        @Override public String rationale() {
            return "A page needs one entry point. When the two loudest things above the fold are "
                    + "within a few percent of each other, the eye has nowhere to start and the "
                    + "reader's first act is a decision rather than a glance.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Prominence.Scored> top = a.prominence().topAboveFold(2);
            if (top.size() < 2 || top.get(0).score <= 0) return List.of();

            double ratio = top.get(1).score / top.get(0).score;
            if (ratio < a.config().focalCompetitionRatio) return List.of();

            Elem first = top.get(0).elem, second = top.get(1).elem;
            return List.of(finding(Severity.MEDIUM, String.format(
                    "%s and %s score %.3f and %.3f for visual weight (%.0f%% of each other), so "
                            + "neither wins the first glance.",
                    first.label(), second.label(), top.get(0).score, top.get(1).score, ratio * 100))
                    .about(first, second)
                    .with("firstScore", top.get(0).score)
                    .with("secondScore", top.get(1).score)
                    .with("ratio", Math.round(ratio * 1000) / 1000.0)
                    .with("firstComponents", top.get(0).components)
                    .with("secondComponents", top.get(1).components)
                    .suggest("Decide which of the two leads, then widen the gap: change its size, "
                            + "weight, colour or the whitespace around it. Quieting the loser is "
                            + "usually easier than amplifying the winner."));
        }
    }

    /** Something unimportant out-shouting the page's own headline. */
    public static final class InvertedHierarchy implements Rule {
        @Override public String id() { return "inverted-hierarchy"; }

        @Override public String title() { return "Secondary content outranks the page's heading"; }

        @Override public String rationale() {
            return "The DOM outline and the visual order disagree. A promo badge, a cookie "
                    + "banner or a nav item with more measured weight than the h1 means the page "
                    + "says one thing and shows another -- and what it shows is what wins.";
        }

        @Override public List<Finding> check(Analysis a) {
            Optional<Elem> headingOpt = a.snapshot().dominantHeading();
            if (headingOpt.isEmpty()) return List.of();
            Elem heading = headingOpt.get();
            if (!heading.rect().intersects(a.snapshot().viewportRect())) return List.of();

            int headingRank = a.prominence().rankOf(heading.id);
            if (headingRank <= 1) return List.of();

            Optional<Elem> primary = a.primaryAction();
            for (Prominence.Scored s : a.prominence().topAboveFold(headingRank)) {
                if (s.elem.id == heading.id) continue;
                // The primary call to action is allowed to out-shout the heading; that is a design.
                if (primary.isPresent() && s.elem.id == primary.get().id) continue;
                if (s.elem.heading > 0 && s.elem.heading < heading.heading) continue;

                return List.of(finding(Severity.MEDIUM, String.format(
                        "%s ranks %d for visual weight while the page's leading heading %s ranks %d.",
                        s.elem.label(), s.rank, heading.label(), headingRank))
                        .about(s.elem, heading)
                        .with("louderElement", s.elem.selector)
                        .with("louderRank", s.rank)
                        .with("louderScore", s.score)
                        .with("headingRank", headingRank)
                        .with("headingScore", a.prominence().forElement(heading.id).map(x -> x.score).orElse(0.0))
                        .with("louderComponents", s.components)
                        .suggest("Either quiet this element (smaller, lighter, lower contrast, less "
                                + "isolation) or raise the heading. Compare the component breakdowns "
                                + "to see which input is doing the shouting."));
            }
            return List.of();
        }
    }

    /** The primary action nowhere near where the eye goes. */
    public static final class PrimaryActionNotFound implements Rule {
        /** Peak saliency inside the action, on the 0..1 normalized map, that counts as found. */
        private static final double SALIENT_ENOUGH = 0.5;
        /** Prominence rank above the fold within which an action is findable by weight alone. */
        private static final int PROMINENT_ENOUGH_RANK = 5;

        @Override public String id() { return "primary-action-not-salient"; }

        @Override public String title() { return "The primary action is not where the eye lands"; }

        @Override public String rationale() {
            return "Saliency is computed from the pixels, not the markup: it predicts the first "
                    + "few places a visitor's eye actually goes. If the page's main action is not "
                    + "among them, it will be found by reading rather than by seeing -- which most "
                    + "visitors will not do.";
        }

        @Override public boolean needsPixels() { return true; }

        @Override public List<Finding> check(Analysis a) {
            Optional<Elem> primaryOpt = a.primaryAction();
            if (primaryOpt.isEmpty()) return List.of();
            Elem primary = primaryOpt.get();

            int within = a.config().primaryActionWithinFixations;
            List<Saliency.Fixation> path = a.fixations(Math.max(within, 5));
            if (path.isEmpty()) return List.of();

            double scale = a.scale();
            // A strongly salient action is found even if the headline wins the first glance.
            Optional<Saliency> foldSal = a.foldSaliency();
            if (foldSal.isPresent()) {
                Rect r = primary.rect();
                double peak = foldSal.get().peakIn(r.x() * scale, r.y() * scale,
                        r.w() * scale, r.h() * scale);
                if (peak >= SALIENT_ENOUGH) return List.of();
            }
            Rect box = primary.rect().inflate(12);
            for (Saliency.Fixation f : path.subList(0, Math.min(within, path.size()))) {
                double cssX = f.x() / scale, cssY = f.y() / scale;
                if (cssX >= box.x() && cssX <= box.right() && cssY >= box.y() && cssY <= box.bottom()) {
                    return List.of();
                }
            }
            // Second signal: an action among the loudest things above the fold is findable,
            // even when a large headline legitimately wins the literal first glance.
            List<Prominence.Scored> loudest = a.prominence().topAboveFold(PROMINENT_ENOUGH_RANK);
            if (loudest.stream().anyMatch(s -> s.elem.id == primary.id)) return List.of();

            int rank = a.prominence().rankOf(primary.id);
            Saliency.Fixation first = path.get(0);
            return List.of(finding(Severity.MEDIUM, String.format(
                    "%s is not among the first %d predicted fixations -- the eye is drawn to "
                            + "(%.0f, %.0f) first -- and it ranks only %d for measured visual weight.",
                    primary.label(), within, first.x() / scale, first.y() / scale, rank))
                    .about(primary)
                    .with("fixationsChecked", within)
                    .with("prominenceRank", rank)
                    .with("firstFixation", List.of(Math.round(first.x() / scale), Math.round(first.y() / scale)))
                    .with("scanpath", path.stream().map(f -> List.of(
                            f.order(), Math.round(f.x() / scale), Math.round(f.y() / scale))).toList())
                    .suggest("Raise the action's visual weight: a saturated fill against a muted "
                            + "field, more whitespace around it, or a position nearer the "
                            + "predicted path. Check the scanpath overlay to see what is winning "
                            + "attention instead."));
        }
    }

    /** What is left after you squint: one blob, or forty. */
    public static final class SquintCohesion implements Rule {
        /** Luminance distance from the page mean that counts as a blob of visual weight. */
        private static final double BLOB_THRESHOLD = 0.06;
        /** Above this many distinct blobs, the layout groups nothing. */
        private static final int MAX_BLOBS = 40;

        @Override public String id() { return "squint-cohesion"; }

        @Override public String title() { return "The page has no structure when blurred"; }

        @Override public String rationale() {
            return "Designers squint at a layout to strip away the text and see the blocks. A "
                    + "page that blurs into one undifferentiated mass has no hierarchy; one that "
                    + "blurs into dozens of separate blobs has no grouping. Either way the reader "
                    + "gets no map of the page before reading it.";
        }

        @Override public boolean needsPixels() { return true; }

        @Override public List<Finding> check(Analysis a) {
            Optional<java.awt.image.BufferedImage> fold = a.foldImage();
            if (fold.isEmpty()) return List.of();
            var squinted = SquintTest.squint(fold.get());
            int blobs = SquintTest.countWeightBlobs(squinted, BLOB_THRESHOLD);

            if (blobs <= 1) {
                return List.of(finding(Severity.MEDIUM, String.format(
                        "Blurred, the first viewport resolves into %d region%s of visual weight: "
                                + "nothing stands out as a focal point.", blobs, blobs == 1 ? "" : "s"))
                        .at(new Rect(0, 0, a.snapshot().viewportWidth(), a.snapshot().viewportHeight()))
                        .with("blobs", blobs)
                        .suggest("Introduce contrast of size or colour so that at least one region "
                                + "survives the squint test. Look at the squint artifact: whatever "
                                + "is invisible there is invisible in the first half-second."));
            }
            if (blobs > MAX_BLOBS) {
                return List.of(finding(Severity.LOW, String.format(
                        "Blurred, the first viewport breaks into %d separate regions of visual "
                                + "weight, so nothing groups.", blobs))
                        .at(new Rect(0, 0, a.snapshot().viewportWidth(), a.snapshot().viewportHeight()))
                        .with("blobs", blobs)
                        .suggest("Group related content with shared surfaces and whitespace so that "
                                + "neighbouring items merge rather than compete."));
            }
            return List.of();
        }
    }
}
