package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.perception.Gray;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures of a page rather than faults in it.
 *
 * <p>Everything else in this project answers "what is wrong here". This answers "what kind
 * of page is this" -- how dense, how colourful, how busy, how evenly weighted. The
 * quantitative literature on interface aesthetics is built on measures of exactly this
 * shape, and it is worth being straight about what that literature actually found: these
 * correlate <em>modestly</em> with judged quality. They are useful for comparing a page
 * against its own siblings, against its own previous version, or against a house norm. They
 * are not a score, and nothing here passes or fails on them.
 *
 * <p>Two of them come from the pixels and two from the geometry, which is deliberate: a
 * page can look busy while its boxes are tidy, and the gap between those two readings is
 * usually where the interesting conversation is.
 */
public final class PageProfile {

    private PageProfile() { }

    /**
     * @param colourfulness  Hasler and Suesstrunk's measure over the opponent channels.
     *                       Roughly: under 15 is near-monochrome, over 80 is vivid.
     * @param visualComplexity quadtree leaves needed to describe the image, normalised 0..1.
     *                       Busyness as an image, independent of how tidy the boxes are. It
     *                       saturates: a wall-to-wall text page has detail at every scale and
     *                       reaches the ceiling, after which the measure cannot rank anything
     *                       higher. {@code complexitySaturated} says when that has happened.
     * @param inkCoverage    share of the viewport covered by content boxes, 0..1. Its
     *                       complement is whitespace.
     * @param balance        how evenly visual weight sits about the centre, 0..1, 1 being even.
     * @param alignmentPoints distinct left and top edges content is aligned to. Fewer means a
     *                       stricter grid; a page with as many alignment points as elements
     *                       has no grid at all.
     * @param gridTightness  alignment points relative to element count, 0..1, 1 being tightest.
     */
    public record Profile(double colourfulness, double visualComplexity, double inkCoverage,
                          double balance, int alignmentPoints, double gridTightness) {

        /** True when the page has detail at every scale and the complexity figure is a floor. */
        public boolean complexitySaturated() { return visualComplexity >= SATURATED; }

        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("colourfulness", round(colourfulness, 1));
            m.put("visualComplexity", round(visualComplexity, 3));
            if (complexitySaturated()) m.put("complexitySaturated", true);
            m.put("inkCoverage", round(inkCoverage, 3));
            m.put("whitespace", round(1 - inkCoverage, 3));
            m.put("balance", round(balance, 3));
            m.put("alignmentPoints", alignmentPoints);
            m.put("gridTightness", round(gridTightness, 3));
            return m;
        }

        private static double round(double v, int places) {
            double scale = Math.pow(10, places);
            return Math.round(v * scale) / scale;
        }
    }

    /** Measures the page. Image-derived figures are zero when no pixels were captured. */
    public static Profile of(Analysis analysis) {
        PageSnapshot snapshot = analysis.snapshot();
        BufferedImage fold = analysis.foldImage().orElse(null);

        double colourfulness = fold == null ? 0 : colourfulness(fold);
        double complexity = fold == null ? 0 : visualComplexity(fold);

        List<Elem> content = VisualGroups.content(snapshot).stream()
                .filter(e -> e.rect().intersects(snapshot.viewportRect()))
                .toList();

        return new Profile(colourfulness, complexity, inkCoverage(snapshot, content),
                balance(snapshot, content), alignmentPoints(content),
                gridTightness(content));
    }

    // ------------------------------------------------------------ from the pixels

    /**
     * Colourfulness over the opponent channels: the spread of the colours present, plus a
     * fraction of how far from neutral they sit on average.
     */
    static double colourfulness(BufferedImage img) {
        int step = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 400);
        List<Double> rg = new ArrayList<>(), yb = new ArrayList<>();
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                int p = img.getRGB(x, y);
                double r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                rg.add(r - g);
                yb.add(0.5 * (r + g) - b);
            }
        }
        if (rg.isEmpty()) return 0;
        double meanRg = mean(rg), meanYb = mean(yb);
        double sdRg = standardDeviation(rg, meanRg), sdYb = standardDeviation(yb, meanYb);
        return Math.hypot(sdRg, sdYb) + 0.3 * Math.hypot(meanRg, meanYb);
    }

    /**
     * Visual complexity by quadtree decomposition: how many uniform blocks the image has to
     * be cut into before each is flat. A plain page needs few; a dense one needs many.
     */
    static double visualComplexity(BufferedImage img) {
        Gray luminance = Gray.luminanceOf(img);
        int leaves = countLeaves(luminance, 0, 0, luminance.width, luminance.height, 0);
        // Normalised against the leaf count a fully subdivided image would need.
        double maxLeaves = Math.pow(4, MAX_DEPTH);
        return Math.min(1, leaves / maxLeaves);
    }

    /** Deepest subdivision; beyond this the measure describes antialiasing rather than layout. */
    private static final int MAX_DEPTH = 7;
    /** Luminance spread below which a block counts as uniform. Loose enough to ignore
     *  antialiased glyph edges, tight enough to notice a real change of surface. */
    private static final double UNIFORM = 0.05;
    /** Above this the measure has hit its ceiling and cannot rank anything higher. */
    static final double SATURATED = 0.95;

    private static int countLeaves(Gray g, int x, int y, int w, int h, int depth) {
        if (w < 2 || h < 2 || depth >= MAX_DEPTH) return 1;
        if (isUniform(g, x, y, w, h)) return 1;
        int hw = w / 2, hh = h / 2;
        return countLeaves(g, x, y, hw, hh, depth + 1)
                + countLeaves(g, x + hw, y, w - hw, hh, depth + 1)
                + countLeaves(g, x, y + hh, hw, h - hh, depth + 1)
                + countLeaves(g, x + hw, y + hh, w - hw, h - hh, depth + 1);
    }

    private static boolean isUniform(Gray g, int x, int y, int w, int h) {
        int step = Math.max(1, Math.min(w, h) / 8);
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int j = y; j < y + h; j += step) {
            for (int i = x; i < x + w; i += step) {
                double v = g.at(i, j);
                min = Math.min(min, v);
                max = Math.max(max, v);
                if (max - min > UNIFORM) return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------- from the geometry

    /** Share of the first viewport covered by content, counting overlaps once. */
    static double inkCoverage(PageSnapshot snapshot, List<Elem> content) {
        Rect viewport = snapshot.viewportRect();
        if (viewport.area() <= 0 || content.isEmpty()) return 0;

        // A coarse occupancy grid: exact union of hundreds of rectangles is not worth it.
        final int cells = 100;
        boolean[][] filled = new boolean[cells][cells];
        double cellW = viewport.w() / cells, cellH = viewport.h() / cells;
        for (Elem e : content) {
            Rect r = e.rect();
            int x0 = (int) Math.max(0, Math.floor(r.x() / cellW));
            int x1 = (int) Math.min(cells - 1, Math.floor((r.right() - 0.01) / cellW));
            int y0 = (int) Math.max(0, Math.floor(r.y() / cellH));
            int y1 = (int) Math.min(cells - 1, Math.floor((r.bottom() - 0.01) / cellH));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) filled[y][x] = true;
            }
        }
        int count = 0;
        for (boolean[] row : filled) for (boolean cell : row) if (cell) count++;
        return count / (double) (cells * cells);
    }

    /**
     * How evenly visual weight sits about the centre lines, where an element's weight is its
     * area. 1 is perfectly even; a page with everything down the left is near 0.
     */
    static double balance(PageSnapshot snapshot, List<Elem> content) {
        if (content.isEmpty()) return 1;
        Rect viewport = snapshot.viewportRect();
        double left = 0, right = 0, top = 0, bottom = 0;
        for (Elem e : content) {
            Rect r = e.rect();
            double weight = r.area();
            if (r.centerX() < viewport.centerX()) left += weight; else right += weight;
            if (r.centerY() < viewport.centerY()) top += weight; else bottom += weight;
        }
        double horizontal = asymmetry(left, right);
        double vertical = asymmetry(top, bottom);
        return 1 - (horizontal + vertical) / 2;
    }

    private static double asymmetry(double a, double b) {
        double total = a + b;
        return total <= 0 ? 0 : Math.abs(a - b) / total;
    }

    /** Distinct left and top edges content is aligned to, at whole-pixel resolution. */
    static int alignmentPoints(List<Elem> content) {
        java.util.Set<Long> edges = new java.util.HashSet<>();
        for (Elem e : content) {
            edges.add(Math.round(e.x));
            edges.add(0x1_0000_0000L + Math.round(e.y));
        }
        return edges.size();
    }

    /**
     * Alignment points against element count. A page where every element introduces two new
     * edges has no grid; one where they all share a few has a strict one.
     */
    static double gridTightness(List<Elem> content) {
        if (content.isEmpty()) return 1;
        double perElement = alignmentPoints(content) / (double) (content.size() * 2);
        return Math.max(0, 1 - perElement);
    }

    private static double mean(List<Double> values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private static double standardDeviation(List<Double> values, double mean) {
        double sum = 0;
        for (double v : values) sum += (v - mean) * (v - mean);
        return Math.sqrt(sum / values.size());
    }
}
