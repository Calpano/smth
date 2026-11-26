package com.calpano.uxlens.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The result of auditing one page at one or more viewports. */
public class Report {

    public String url;
    public String generatedAt;
    public List<ViewportResult> viewports = new ArrayList<>();

    /** One viewport's audit: what was measured, what was found, what was rendered. */
    public static class ViewportResult {
        public String viewportName;
        /** The viewport alone, without the colour scheme suffix. */
        public String viewportBase;
        /** {@code light} or {@code dark}. */
        public String colorScheme = "light";
        public int width, height;
        public int elementCount;
        public boolean truncated;
        public List<Finding> findings = new ArrayList<>();
        /** Top elements by visual prominence, highest first. */
        public List<Prominence> prominence = new ArrayList<>();
        /** Predicted gaze order over the rendered page. */
        public List<FocalPoint> focalPoints = new ArrayList<>();
        /** The spacing / type / color values the page actually uses, with outlier counts. */
        public Map<String, Object> inferredTokens = new LinkedHashMap<>();
        /** Written artifacts, by kind: screenshot, squint, saliency, grid, setOfMark, findings. */
        public Map<String, String> artifacts = new LinkedHashMap<>();
    }

    /** One element's computed visual weight. */
    public static class Prominence {
        public int rank;
        public int elementId;
        public String selector;
        public String tag;
        public String text;
        public double score;
        public double areaPx;
        public double fontSizePx;
        public int fontWeight;
        public double contrastRatio;
        public double whitespaceMarginPx;
        public double viewportYPercent;
        public boolean interactive;
        public boolean aboveFold;
        /** Which inputs drove the score, for explainability. */
        public Map<String, Double> components = new LinkedHashMap<>();
    }

    /** A predicted point of visual attention, in gaze order. */
    public static class FocalPoint {
        public int order;
        public double x, y;
        public double strength;
        /** The most prominent element under this point, if any. */
        public Integer elementId;
        public String selector;
        public String text;
    }

    /** Rules that fire in one colour scheme and not the other, at one viewport. */
    public static class SchemeParity {
        public String viewport;
        public List<String> onlyInDark = new ArrayList<>();
        public List<String> onlyInLight = new ArrayList<>();

        public boolean isClean() { return onlyInDark.isEmpty() && onlyInLight.isEmpty(); }
    }

    /**
     * Where the two colour schemes disagree.
     *
     * <p>Dark mode is usually an afterthought bolted onto a light design, and the defects it
     * introduces -- a border that vanishes, text that drops below contrast, a shadow that
     * stops separating anything -- are invisible to anyone reviewing the light version. The
     * check is cheap: audit both and compare which rules fired.
     */
    public List<SchemeParity> schemeParity() {
        Map<String, List<ViewportResult>> byViewport = new LinkedHashMap<>();
        for (ViewportResult v : viewports) {
            byViewport.computeIfAbsent(v.viewportBase == null ? v.viewportName : v.viewportBase,
                    k -> new ArrayList<>()).add(v);
        }
        List<SchemeParity> out = new ArrayList<>();
        for (Map.Entry<String, List<ViewportResult>> en : byViewport.entrySet()) {
            ViewportResult light = en.getValue().stream()
                    .filter(v -> "light".equals(v.colorScheme)).findFirst().orElse(null);
            ViewportResult dark = en.getValue().stream()
                    .filter(v -> "dark".equals(v.colorScheme)).findFirst().orElse(null);
            if (light == null || dark == null) continue;

            SchemeParity parity = new SchemeParity();
            parity.viewport = en.getKey();
            parity.onlyInDark = ruleIds(dark).stream().filter(r -> !ruleIds(light).contains(r)).toList();
            parity.onlyInLight = ruleIds(light).stream().filter(r -> !ruleIds(dark).contains(r)).toList();
            out.add(parity);
        }
        return out;
    }

    private static java.util.Set<String> ruleIds(ViewportResult v) {
        return v.findings.stream().map(f -> f.ruleId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public List<Finding> allFindings() {
        List<Finding> all = new ArrayList<>();
        for (ViewportResult v : viewports) all.addAll(v.findings);
        return all;
    }

    public List<Finding> findings(Severity min) {
        return allFindings().stream().filter(f -> f.severity.atLeast(min))
                .sorted(Comparator.comparing((Finding f) -> f.severity).reversed()
                        .thenComparing(f -> f.ruleId))
                .toList();
    }

    public Map<Severity, Long> countsBySeverity() {
        Map<Severity, Long> m = new LinkedHashMap<>();
        for (Severity s : Severity.values()) m.put(s, 0L);
        for (Finding f : allFindings()) m.merge(f.severity, 1L, Long::sum);
        return m;
    }

    /** True when nothing at or above {@code min} was found. */
    public boolean isClean(Severity min) { return findings(min).isEmpty(); }
}
