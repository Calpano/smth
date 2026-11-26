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
