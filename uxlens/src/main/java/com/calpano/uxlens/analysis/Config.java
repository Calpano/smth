package com.calpano.uxlens.analysis;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The thresholds that turn measurements into findings.
 *
 * <p>Every number here is a judgement call that a design team may legitimately disagree
 * with, so every number here is configurable. The defaults follow common practice:
 * WCAG 2.2 AA for contrast, the 44px touch target from both platform guidelines, and a
 * 1.5x heading-to-body ratio below which pages read as flat.
 */
public final class Config {

    /**
     * Edge offsets up to this many px are reported as near-misses. A 2px offset is a bug;
     * a 40px offset is a layout decision. Default 4.
     */
    public double alignmentTolerancePx = 4;

    /** Offsets below this are sub-pixel rendering, not misalignment. Default 0.5. */
    public double alignmentIgnoreBelowPx = 0.5;

    /** Minimum touch target, px. Default 44 (Apple HIG and WCAG 2.5.5 agree). */
    public double minTouchTargetPx = 44;

    /** Minimum target size on a non-touch viewport, px. Default 24 (WCAG 2.5.8 AA). */
    public double minPointerTargetPx = 24;

    /** WCAG AA contrast for normal text. Default 4.5. */
    public double contrastNormal = 4.5;

    /** WCAG AA contrast for large text. Default 3.0. */
    public double contrastLarge = 3.0;

    /** Contrast for non-text UI (borders, icons, focus rings). Default 3.0 (WCAG 1.4.11). */
    public double contrastNonText = 3.0;

    /** Hold text to AAA (7.0 / 4.5) instead of AA. Default false. */
    public boolean requireAaa = false;

    /**
     * How unequal sibling gaps may be before it reads as a rhythm error, as a fraction of
     * the median gap. Default 0.12 -- a 12% wobble is visible in a card grid.
     */
    public double gapToleranceRatio = 0.12;

    /** Smallest absolute gap difference worth reporting, px. Default 2. */
    public double gapToleranceMinPx = 2;

    /** Largest-heading to body-size ratio below which the page reads as flat. Default 1.5. */
    public double minHeadingToBodyRatio = 1.5;

    /** Distinct font sizes above which the type scale is noise. Default 8. */
    public int maxDistinctFontSizes = 8;

    /** Distinct font families above which the page looks unowned. Default 3. */
    public int maxDistinctFontFamilies = 3;

    /** Font sizes closer than this to another size add no hierarchy. Default 2px. */
    public double minTypeStepPx = 2;

    /** Comfortable line length, in characters. Defaults 45..85, the classic measure. */
    public int minMeasureChars = 45;
    public int maxMeasureChars = 85;

    /** Line height below this multiple of font size is cramped. Default 1.3 for body text. */
    public double minBodyLineHeightRatio = 1.3;

    /**
     * When the second-ranked element above the fold scores at least this fraction of the
     * first, they are competing for the eye rather than forming a hierarchy. Default 0.92.
     */
    public double focalCompetitionRatio = 0.92;

    /** How many predicted fixations the primary action must appear within. Default 3. */
    public int primaryActionWithinFixations = 3;

    /** Raster images displayed above this multiple of their natural width look soft. Default 1.15. */
    public double maxImageUpscale = 1.15;

    /** Overlap area, as a fraction of the smaller element, that counts as a collision. Default 0.08. */
    public double overlapTolerance = 0.08;

    /**
     * Most findings any single rule may report, per viewport.
     *
     * <p>Volume is a correctness property of its own. A dense application page can produce
     * hundreds of individually defensible findings from one rule, and a report like that is
     * unreadable -- the forty-ninth undersized tap target buries the one clipped heading.
     * Beyond this cap the rule reports its worst findings and says how many more there were.
     */
    public int maxFindingsPerRule = 25;

    /** Rule ids to skip. */
    public final Set<String> disabledRules = new LinkedHashSet<>();

    /** Rule ids to run exclusively; empty means all enabled rules. */
    public final Set<String> onlyRules = new LinkedHashSet<>();

    public boolean runs(String ruleId) {
        if (!onlyRules.isEmpty()) return onlyRules.contains(ruleId);
        return !disabledRules.contains(ruleId);
    }

    public Config disable(String... ruleIds) {
        disabledRules.addAll(Set.of(ruleIds));
        return this;
    }

    public Config only(String... ruleIds) {
        onlyRules.addAll(Set.of(ruleIds));
        return this;
    }

    public Config aaa() { this.requireAaa = true; return this; }

    /** Required contrast for text of this size and weight, under the configured level. */
    public double requiredContrast(boolean largeText) {
        if (requireAaa) return largeText ? 4.5 : 7.0;
        return largeText ? contrastLarge : contrastNormal;
    }

    public static Config defaults() { return new Config(); }
}
