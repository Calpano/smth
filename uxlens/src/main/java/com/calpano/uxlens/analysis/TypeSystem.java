package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The type scale the page actually uses: which sizes, how much text at each, and whether
 * the steps form a scale a designer would recognise.
 *
 * <p>A healthy page uses few sizes with clear steps between them. Fifteen sizes within
 * 4px of each other is the signature of ad-hoc CSS, and it reads as muddled hierarchy
 * even when no single value is wrong.
 */
public final class TypeSystem {

    private final double bodySize;
    private final Map<Double, Integer> sizeUsage;   // size px -> characters set at it
    private final List<Double> scale;               // distinct sizes, ascending
    private final Map<String, Integer> families;    // family -> characters
    private final double largestToBodyRatio;

    private TypeSystem(double bodySize, Map<Double, Integer> sizeUsage, List<Double> scale,
                       Map<String, Integer> families, double largestToBodyRatio) {
        this.bodySize = bodySize;
        this.sizeUsage = sizeUsage;
        this.scale = scale;
        this.families = families;
        this.largestToBodyRatio = largestToBodyRatio;
    }

    public static TypeSystem infer(PageSnapshot snapshot) {
        Map<Double, Integer> usage = new TreeMap<>();
        Map<String, Integer> families = new LinkedHashMap<>();
        for (Elem e : snapshot.textElements()) {
            usage.merge(Math.round(e.fontSize * 2) / 2.0, Math.max(1, e.textLength), Integer::sum);
            if (e.fontFamily != null && !e.fontFamily.isBlank()) {
                families.merge(e.fontFamily, Math.max(1, e.textLength), Integer::sum);
            }
        }
        double body = snapshot.bodyFontSize();
        List<Double> scale = usage.keySet().stream().sorted().toList();
        double largest = scale.isEmpty() ? body : scale.get(scale.size() - 1);
        return new TypeSystem(body, usage, scale, families, body > 0 ? largest / body : 1);
    }

    /** The page's body size: the size most of its text is set at. */
    public double bodySize() { return bodySize; }

    /** Distinct sizes in use, ascending. */
    public List<Double> scale() { return scale; }

    /** Characters set at each size. */
    public Map<Double, Integer> sizeUsage() { return sizeUsage; }

    /** Font families in use, by how much text each sets. */
    public Map<String, Integer> families() { return families; }

    /** Largest size divided by body size. Below ~1.5 the page reads as a flat wall of text. */
    public double largestToBodyRatio() { return largestToBodyRatio; }

    /**
     * Sizes that sit within {@code minStep} px of another size and are used for little
     * text -- near-duplicates that add no hierarchy, only noise.
     */
    public List<Double> nearDuplicateSizes(double minStep) {
        return scale.stream()
                .filter(s -> scale.stream().anyMatch(o -> !o.equals(s) && Math.abs(o - s) < minStep
                        && sizeUsage.getOrDefault(o, 0) > sizeUsage.getOrDefault(s, 0)))
                .sorted(Comparator.comparingInt(s -> sizeUsage.getOrDefault(s, 0)))
                .toList();
    }

    /** Families setting less than {@code shareThreshold} of all text -- one-off font choices. */
    public List<String> marginalFamilies(double shareThreshold) {
        int total = families.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return List.of();
        return families.entrySet().stream()
                .filter(en -> en.getValue() / (double) total < shareThreshold)
                .map(Map.Entry::getKey).toList();
    }

    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bodySize", bodySize);
        m.put("distinctSizes", scale.size());
        m.put("sizes", scale);
        m.put("largestToBodyRatio", Math.round(largestToBodyRatio * 100) / 100.0);
        m.put("families", families.keySet());
        return m;
    }
}
