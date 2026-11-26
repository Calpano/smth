package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Judgments about the design system itself, rather than departures from it.
 *
 * <p>This family exists because consistency mining has a ceiling. {@link
 * com.calpano.uxlens.analysis.Consistency} can only find deviations from the page's own
 * vocabulary -- it can never find a <em>bad</em> vocabulary. A page can be flawlessly
 * self-consistent and still be the one a designer looks at and says: nothing leads, the
 * sections do not read as sections, there is no black on this page, stop centring the body
 * copy. Every one of those is advice about the system, and every one is invisible to a
 * check that measures variance.
 *
 * <p>So these rules measure <em>level</em> and <em>range</em> where the consistency family
 * measures variance. They are necessarily more opinionated, which is why each one states
 * the number it used and is easy to switch off.
 */
public final class SystemRules {

    private SystemRules() { }

    public static List<Rule> all() {
        return List.of(new FlatGroupingHierarchy(), new NoTonalAnchor(),
                new CentredBodyCopy(), new CrampedProse());
    }

    /** Sections that do not read as sections, because every gap is the same size. */
    public static final class FlatGroupingHierarchy implements Rule {

        /** How much bigger a gap between groups must be than a gap inside one. */
        private static final double REQUIRED_RATIO = 1.5;
        /** Enough groups on both levels to be talking about a pattern rather than a pair. */
        private static final int MIN_GROUPS = 2;

        @Override public String id() { return "flat-grouping-hierarchy"; }

        @Override public String title() { return "Grouping levels are not separated by spacing"; }

        @Override public String rationale() {
            return "Readers group by distance before they read a word, so the space between "
                    + "sections has to be clearly larger than the space inside one. When the two "
                    + "are the same the page is perfectly consistent and completely flat: every "
                    + "block floats at the same level and the reader gets no outline. This is the "
                    + "most common defect on a page where nothing is individually wrong.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Double> within = new ArrayList<>();
            List<Double> between = new ArrayList<>();

            for (VisualGroups.Group g : a.stacks()) {
                if (g.size() < 2) continue;
                List<Double> gaps = g.gaps().stream().filter(v -> v >= 0 && v < 400).toList();
                if (gaps.isEmpty()) continue;
                double median = median(gaps);
                // A stack of content is a group; a stack of containers holds groups.
                boolean holdsGroups = g.members().stream().allMatch(e -> e.childCount > 0 && !e.hasOwnText());
                (holdsGroups ? between : within).add(median);
            }
            // Several groups to establish the inner rhythm, but one outer stack is the
            // normal shape of a page -- demanding two would exempt most pages from the rule.
            if (within.size() < MIN_GROUPS || between.isEmpty()) return List.of();

            double insideGroups = median(within);
            double betweenGroups = median(between);
            if (insideGroups <= 0) return List.of();
            double ratio = betweenGroups / insideGroups;
            if (ratio >= REQUIRED_RATIO) return List.of();

            return List.of(finding(ratio < 1.1 ? Severity.MEDIUM : Severity.LOW, String.format(
                    "Space between groups (%.0fpx) is only %.2fx the space inside them (%.0fpx), "
                            + "so the page reads as one flat list rather than as sections.",
                    betweenGroups, ratio, insideGroups))
                    .with("gapInsideGroups", Math.round(insideGroups))
                    .with("gapBetweenGroups", Math.round(betweenGroups))
                    .with("ratio", Math.round(ratio * 100) / 100.0)
                    .with("requiredRatio", REQUIRED_RATIO)
                    .suggest(String.format("Open the space between sections to about %.0fpx, or "
                                    + "close the space inside them. The page's spacing scale is "
                                    + "fine; what is missing is using two different steps of it "
                                    + "for two different levels of grouping.",
                            insideGroups * 2)));
        }
    }

    /** A page with no black and no white: everything mid-tone, nothing anchors. */
    public static final class NoTonalAnchor implements Rule {

        /** The best contrast anywhere on the page must reach this, or nothing anchors. */
        private static final double REQUIRED_PEAK = 7.0;
        /** Enough text to be judging a page rather than a fragment. */
        private static final int MIN_TEXT_ELEMENTS = 6;
        /** Share of the page's text whose colour must be resolvable before judging the range. */
        private static final double MIN_RESOLVED_SHARE = 0.5;

        @Override public String id() { return "no-tonal-anchor"; }

        @Override public String title() { return "Nothing on the page anchors the tonal range"; }

        @Override public String rationale() {
            return "Every element clearing the contrast minimum is not the same as the page "
                    + "having a tonal range. When the strongest thing on the page is mid-grey, "
                    + "the eye has nothing to hold and the whole surface recedes -- consistently, "
                    + "and consistently weakly. A page needs something near the dark end.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Elem> all = a.snapshot().textElements();
            List<Elem> texts = all.stream().filter(e -> e.textContrast() > 0).toList();
            if (texts.size() < MIN_TEXT_ELEMENTS) return List.of();
            // If most of the page's colours could not be resolved, the strongest contrast
            // found says nothing about the page -- only about the parser. Staying silent is
            // the honest answer; this rule once reported 1.00:1 on a page whose colours it
            // simply could not read.
            if (texts.size() < all.size() * MIN_RESOLVED_SHARE) return List.of();

            Elem strongest = texts.stream()
                    .max(Comparator.comparingDouble(Elem::textContrast)).orElseThrow();
            double peak = strongest.textContrast();
            if (peak >= REQUIRED_PEAK) return List.of();

            return List.of(finding(peak < 5 ? Severity.MEDIUM : Severity.LOW, String.format(
                    "The strongest contrast anywhere on the page is %.2f:1 (%s). Nothing reaches "
                            + "the %.1f:1 that gives a page an anchor.",
                    peak, strongest.label(), REQUIRED_PEAK))
                    .about(strongest)
                    .with("peakContrast", Math.round(peak * 100) / 100.0)
                    .with("required", REQUIRED_PEAK)
                    .with("textElements", texts.size())
                    .suggest("Darken the primary text, or introduce one element that sits near "
                            + "the dark end of the range. Raising everything equally will not "
                            + "help -- the page needs a top end, not a higher floor."));
        }
    }

    /** Centred running text, which has no left edge for the eye to return to. */
    public static final class CentredBodyCopy implements Rule {

        /** Share of prose blocks that may be centred before scanning suffers. */
        private static final double MAX_CENTRED_SHARE = 0.5;
        private static final int MIN_BLOCKS = 3;
        /** Characters below which a block is a label or a caption, not running text. */
        private static final int PROSE_CHARS = 80;

        @Override public String id() { return "centred-body-copy"; }

        @Override public String title() { return "Running text is centred"; }

        @Override public String rationale() {
            return "Centred text has no left edge, so every return sweep has to search for the "
                    + "start of the next line. One centred block is a decision; a page of them is "
                    + "a page nobody will read past the first paragraph. It is also entirely "
                    + "self-consistent, which is why no amount of consistency checking finds it.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Elem> prose = a.snapshot().textElements().stream()
                    .filter(e -> e.textLength >= PROSE_CHARS)
                    .filter(e -> e.lines != null && e.lines.lineCount >= 2)
                    .filter(e -> e.heading == 0)
                    .toList();
            if (prose.size() < MIN_BLOCKS) return List.of();

            List<Elem> centred = prose.stream()
                    .filter(e -> "center".equals(e.textAlign)).toList();
            double share = centred.size() / (double) prose.size();
            if (share <= MAX_CENTRED_SHARE) return List.of();

            Finding f = finding(share > 0.8 ? Severity.MEDIUM : Severity.LOW, String.format(
                    "%d of %d running-text blocks are centred, so the text has no left edge to "
                            + "scan down.", centred.size(), prose.size()))
                    .with("centredBlocks", centred.size())
                    .with("proseBlocks", prose.size())
                    .with("share", Math.round(share * 100) / 100.0)
                    .suggest("Left-align running text. Keep centring for short display lines -- "
                            + "a heading, a caption, a single-line call to action -- where there "
                            + "is no return sweep to lose.");
            centred.stream().limit(3).forEach(f::about);
            return List.of(f);
        }
    }

    /** Prose set without enough room to breathe, uniformly. */
    public static final class CrampedProse implements Rule {

        /** Space between prose blocks, as a multiple of the body size, below which it is cramped. */
        private static final double MIN_GAP_RATIO = 0.5;
        private static final int MIN_BLOCKS = 3;

        @Override public String id() { return "cramped-prose"; }

        @Override public String title() { return "Prose blocks are set too close together"; }

        @Override public String rationale() {
            return "Whitespace has to scale with type: paragraphs set 4px apart at a 16px body "
                    + "size read as one undifferentiated block however correct each paragraph is. "
                    + "Measured against prose only, so the dense lists and tables an application "
                    + "is made of are left alone.";
        }

        @Override public List<Finding> check(Analysis a) {
            double body = a.snapshot().bodyFontSize();
            if (body <= 0) return List.of();

            List<Double> gaps = new ArrayList<>();
            for (VisualGroups.Group g : a.stacks()) {
                // Only runs of actual prose; a list of links is supposed to be tight.
                List<Elem> members = g.members();
                boolean prose = members.size() >= MIN_BLOCKS && members.stream()
                        .allMatch(e -> e.hasOwnText() && e.textLength >= 80 && e.heading == 0);
                if (!prose) continue;
                g.gaps().stream().filter(v -> v >= 0 && v < 200).forEach(gaps::add);
            }
            if (gaps.size() < MIN_BLOCKS - 1) return List.of();

            double median = median(gaps);
            double ratio = median / body;
            if (ratio >= MIN_GAP_RATIO) return List.of();

            return List.of(finding(ratio < 0.25 ? Severity.MEDIUM : Severity.LOW, String.format(
                    "Prose blocks sit %.0fpx apart at a %.0fpx body size (%.2f of the type size); "
                            + "below %.2f they read as one block.",
                    median, body, ratio, MIN_GAP_RATIO))
                    .with("medianGapPx", Math.round(median))
                    .with("bodySizePx", body)
                    .with("ratio", Math.round(ratio * 100) / 100.0)
                    .with("requiredRatio", MIN_GAP_RATIO)
                    .suggest(String.format("Set the gap between paragraphs to about %.0fpx -- "
                            + "roughly one line of body text. Space between blocks should scale "
                            + "with the type, not be a fixed number chosen once.", body)));
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        return sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
    }
}
