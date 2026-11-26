package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The spacing scale the page <em>actually</em> uses, inferred from what was rendered.
 *
 * <p>Designers do not check spacing against an abstract ideal; they check it against the
 * system the page itself establishes. A page on a 4px scale with two stray 15px gaps has
 * two bugs. A page on a 5px scale has none. So the base unit is inferred, not assumed,
 * and only the outliers are reported.
 */
public final class SpacingSystem {

    /** Candidate base units, largest first, so an 8px page is not reported as a 4px one. */
    private static final int[] CANDIDATES = {16, 12, 8, 6, 5, 4, 3, 2};
    /** A base unit must explain at least this share of spacing values to count as the system. */
    private static final double MIN_ADHERENCE = 0.75;
    /** Sub-pixel rounding is not an offence. */
    private static final double EPSILON = 0.51;

    private final int baseUnit;
    private final double adherence;
    private final Map<Double, Integer> histogram;
    private final List<Outlier> outliers;
    private final int sampleCount;

    private SpacingSystem(int baseUnit, double adherence, Map<Double, Integer> histogram,
                          List<Outlier> outliers, int sampleCount) {
        this.baseUnit = baseUnit;
        this.adherence = adherence;
        this.histogram = histogram;
        this.outliers = outliers;
        this.sampleCount = sampleCount;
    }

    /** One measured spacing value on one element. */
    private record Sample(Elem elem, String property, double value) { }

    /** A spacing value that does not sit on the page's own scale. */
    public record Outlier(int elementId, String selector, String property,
                          double value, double nearestOnScale) {
        public double drift() { return Math.abs(value - nearestOnScale); }
    }

    public static SpacingSystem infer(PageSnapshot snapshot) {
        List<Sample> samples = new ArrayList<>();
        for (Elem e : snapshot.elements) {
            // `auto` margins centre a box; the px they resolve to is arithmetic, not a token.
            if (!e.marginAutoTop) collect(samples, e, "margin-top", e.marginTop);
            if (!e.marginAutoBottom) collect(samples, e, "margin-bottom", e.marginBottom);
            if (!e.marginAutoLeft) collect(samples, e, "margin-left", e.marginLeft);
            if (!e.marginAutoRight) collect(samples, e, "margin-right", e.marginRight);
            collect(samples, e, "padding-top", e.padTop);
            collect(samples, e, "padding-bottom", e.padBottom);
            collect(samples, e, "padding-left", e.padLeft);
            collect(samples, e, "padding-right", e.padRight);
            collect(samples, e, "gap", e.gap);
            collect(samples, e, "column-gap", e.columnGap);
        }

        Map<Double, Integer> hist = new TreeMap<>();
        for (Sample s : samples) hist.merge(round(s.value()), 1, Integer::sum);

        int best = 0;
        double bestAdherence = 0;
        for (int unit : CANDIDATES) {
            final int u = unit;
            long onScale = samples.stream().filter(s -> isMultiple(s.value(), u)).count();
            double ratio = samples.isEmpty() ? 0 : onScale / (double) samples.size();
            if (ratio > bestAdherence) { best = unit; bestAdherence = ratio; }
            if (ratio >= MIN_ADHERENCE) { best = unit; bestAdherence = ratio; break; }
        }

        final int unit = best;
        List<Outlier> outliers = samples.stream()
                .filter(s -> unit > 0 && !isMultiple(s.value(), unit))
                .map(s -> new Outlier(s.elem().id, s.elem().selector, s.property(), round(s.value()),
                        Math.round(s.value() / unit) * (double) unit))
                // a value that is nearly on the scale is a slip; one far off may be a deliberate choice
                .sorted(Comparator.comparingDouble(Outlier::drift))
                .toList();

        return new SpacingSystem(unit, bestAdherence, hist, outliers, samples.size());
    }

    private static void collect(List<Sample> into, Elem e, String property, double value) {
        if (value > 0.5 && value < 400) into.add(new Sample(e, property, value));
    }

    private static boolean isMultiple(double value, int unit) {
        if (unit <= 0) return false;
        double mod = Math.abs(value) % unit;
        return mod < EPSILON || unit - mod < EPSILON;
    }

    private static double round(double v) { return Math.round(v * 2) / 2.0; }

    /** The inferred base unit in px, or 0 when the page has no discernible scale. */
    public int baseUnit() { return baseUnit; }

    /** Share of spacing values that sit on the base unit, 0..1. */
    public double adherence() { return adherence; }

    /** Every distinct spacing value used, with how often. */
    public Map<Double, Integer> histogram() { return histogram; }

    /** Spacing values off the page's own scale, most nearly-correct first. */
    public List<Outlier> outliers() { return outliers; }

    public int sampleCount() { return sampleCount; }

    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUnit", baseUnit);
        m.put("adherence", Math.round(adherence * 1000) / 1000.0);
        m.put("sampleCount", sampleCount);
        m.put("outlierCount", outliers.size());
        m.put("distinctValues", histogram.size());
        return m;
    }
}
