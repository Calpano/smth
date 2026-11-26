package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Rgba;
import com.calpano.uxlens.perception.Saliency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How loudly each element shouts.
 *
 * <p>The DOM says what the page <em>means</em> ({@code <h1>} before {@code <p>}); this says
 * what the page <em>does</em> to a visitor's eye. The two disagree more often than anyone
 * expects: a cookie banner out-shouting the headline, a decorative image taking rank one,
 * a primary CTA that is quieter than the nav. Ranking elements by measured visual weight
 * and comparing that ranking against the intended one is the single most useful thing an
 * agent can be handed about a page.
 *
 * <p>Each component is normalized across the page before weighting, because prominence is
 * relative: 18px bold is loud on a page of 14px text and quiet on a page of 60px headlines.
 */
public final class Prominence {

    // Weights sum to 1.0 with saliency present; without it, the rest are rescaled.
    private static final double W_TYPE       = 0.22;
    private static final double W_AREA       = 0.14;
    private static final double W_CONTRAST   = 0.15;
    private static final double W_SATURATION = 0.09;
    private static final double W_ISOLATION  = 0.13;
    private static final double W_POSITION   = 0.12;
    private static final double W_SALIENCY   = 0.15;

    private final List<Scored> ranked;

    private Prominence(List<Scored> ranked) { this.ranked = ranked; }

    /** One element's visual weight, with the inputs that produced it. */
    public static final class Scored {
        public final Elem elem;
        public final double score;
        public final Map<String, Double> components;
        /** Smallest edge-to-edge distance to any other content element, in px. */
        public final double isolationPx;
        public final double contrastRatio;
        public final boolean aboveFold;
        public int rank;

        Scored(Elem elem, double score, Map<String, Double> components,
               double isolationPx, double contrastRatio, boolean aboveFold) {
            this.elem = elem;
            this.score = score;
            this.components = components;
            this.isolationPx = isolationPx;
            this.contrastRatio = contrastRatio;
            this.aboveFold = aboveFold;
        }
    }

    /** Scores without pixel saliency -- DOM geometry and computed style only. */
    public static Prominence of(PageSnapshot snapshot) { return of(snapshot, null, 1); }

    /**
     * Scores including measured pixel saliency.
     *
     * @param saliency map over the full-page screenshot, or null
     * @param scale    screenshot pixels per CSS pixel
     */
    public static Prominence of(PageSnapshot snapshot, Saliency saliency, double scale) {
        List<Elem> content = VisualGroups.content(snapshot);
        if (content.isEmpty()) return new Prominence(List.of());

        double body = snapshot.bodyFontSize();
        Rgba pageBg = Rgba.parse(snapshot.documentBackground);
        if (pageBg == null) pageBg = Rgba.WHITE;
        double viewportArea = Math.max(1.0, snapshot.viewportWidth() * (double) snapshot.viewportHeight());
        Rect fold = snapshot.viewportRect();

        // raw component values, normalized after
        List<double[]> raw = new ArrayList<>();      // type, area, contrast, saturation, isolation, position, saliency
        List<Double> isolations = new ArrayList<>();
        List<Double> contrasts = new ArrayList<>();

        for (Elem e : content) {
            double type = e.hasOwnText() ? e.typographicWeight() / Math.max(1, body) : 0;

            double area = Math.sqrt(e.area() / viewportArea);

            double contrast = contrastOf(e, pageBg);

            Rgba paint = e.hasOwnText() ? e.fg() : Rgba.parse(e.backgroundColor);
            double saturation = paint == null ? 0 : paint.saturation() * (paint.a());

            double isolation = isolationOf(e, content, snapshot);

            // Above the fold counts for a great deal; below it, higher is still louder.
            double position = e.rect().intersects(fold)
                    ? 1.0 - Math.min(1, Math.max(0, e.y) / Math.max(1.0, snapshot.viewportHeight())) * 0.35
                    : Math.max(0, 0.45 - 0.45 * Math.min(1, e.y / Math.max(1.0, snapshot.scrollHeight)));

            double sal = 0;
            if (saliency != null) {
                sal = saliency.meanIn(e.x * scale, e.y * scale, e.w * scale, e.h * scale);
            }

            raw.add(new double[]{type, area, contrast, saturation, isolation, position, sal});
            isolations.add(isolation);
            contrasts.add(contrast);
        }

        double[] max = new double[7];
        for (double[] r : raw) for (int i = 0; i < 7; i++) max[i] = Math.max(max[i], r[i]);

        boolean haveSaliency = saliency != null && max[6] > 0;
        double wType = W_TYPE, wArea = W_AREA, wContrast = W_CONTRAST,
                wSat = W_SATURATION, wIso = W_ISOLATION, wPos = W_POSITION, wSal = W_SALIENCY;
        if (!haveSaliency) {
            double rescale = 1 / (1 - W_SALIENCY);
            wType *= rescale; wArea *= rescale; wContrast *= rescale;
            wSat *= rescale; wIso *= rescale; wPos *= rescale; wSal = 0;
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            Elem e = content.get(i);
            double[] r = raw.get(i);
            Map<String, Double> comps = new LinkedHashMap<>();
            comps.put("type", round(norm(r[0], max[0]) * wType));
            comps.put("area", round(norm(r[1], max[1]) * wArea));
            comps.put("contrast", round(norm(r[2], max[2]) * wContrast));
            comps.put("saturation", round(norm(r[3], max[3]) * wSat));
            comps.put("isolation", round(norm(r[4], max[4]) * wIso));
            comps.put("position", round(norm(r[5], max[5]) * wPos));
            if (haveSaliency) comps.put("saliency", round(norm(r[6], max[6]) * wSal));

            double score = comps.values().stream().mapToDouble(Double::doubleValue).sum();
            // An interactive element reads louder than inert content of the same weight.
            if (e.interactive) score *= 1.08;
            scored.add(new Scored(e, round(score), comps, isolations.get(i), contrasts.get(i),
                    e.rect().intersects(fold)));
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        for (int i = 0; i < scored.size(); i++) scored.get(i).rank = i + 1;
        return new Prominence(List.copyOf(scored));
    }

    private static double contrastOf(Elem e, Rgba pageBg) {
        double c = e.textContrast();
        if (c > 0) return c - 1;
        Rgba own = Rgba.parse(e.backgroundColor);
        if (own != null && own.a() > 0.05) return own.over(e.bg()).contrastRatio(pageBg) - 1;
        return 0;
    }

    /**
     * Distance to the nearest other content element -- the whitespace a designer puts
     * around something to make it matter. Ancestors and descendants are skipped: a button
     * is not isolated from the div wrapping it.
     */
    private static double isolationOf(Elem e, List<Elem> content, PageSnapshot snapshot) {
        double min = Double.MAX_VALUE;
        for (Elem o : content) {
            if (o.id == e.id) continue;
            if (isRelated(e, o, snapshot)) continue;
            min = Math.min(min, e.rect().gapTo(o.rect()));
            if (min == 0) return 0;
        }
        return min == Double.MAX_VALUE ? snapshot.viewportWidth() / 2.0 : min;
    }

    private static boolean isRelated(Elem a, Elem b, PageSnapshot snapshot) {
        for (Elem cur = a; cur != null && cur.parentId >= 0; cur = snapshot.byId(cur.parentId)) {
            if (cur.parentId == b.id) return true;
        }
        for (Elem cur = b; cur != null && cur.parentId >= 0; cur = snapshot.byId(cur.parentId)) {
            if (cur.parentId == a.id) return true;
        }
        return false;
    }

    private static double norm(double v, double max) { return max <= 0 ? 0 : Math.min(1, v / max); }

    private static double round(double v) { return Math.round(v * 10000) / 10000.0; }

    /** All content elements, loudest first. */
    public List<Scored> ranked() { return ranked; }

    /** The {@code n} loudest. */
    public List<Scored> top(int n) { return ranked.subList(0, Math.min(n, ranked.size())); }

    /** The {@code n} loudest above the fold -- what a visitor's eye meets first. */
    public List<Scored> topAboveFold(int n) {
        return ranked.stream().filter(s -> s.aboveFold).limit(n).toList();
    }

    public java.util.Optional<Scored> forElement(int elementId) {
        return ranked.stream().filter(s -> s.elem.id == elementId).findFirst();
    }

    /** Rank of an element, or -1 when it carries no content. */
    public int rankOf(int elementId) {
        return forElement(elementId).map(s -> s.rank).orElse(-1);
    }
}
