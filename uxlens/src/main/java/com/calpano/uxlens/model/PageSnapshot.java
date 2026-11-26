package com.calpano.uxlens.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Everything measured from one page at one viewport, at one moment. */
public class PageSnapshot {

    public String url;
    public String title;
    public int innerWidth, innerHeight;
    public double devicePixelRatio = 1;
    public int scrollWidth, scrollHeight;
    /**
     * How far the page can actually be scrolled sideways, measured by trying it.
     *
     * <p>{@code scrollWidth} does not answer this: a fixed element wider than the viewport
     * inflates it, and under mobile emulation {@code window.innerWidth} too, while leaving
     * the page unscrollable. Only moving the viewport settles it.
     */
    public int maxScrollX;
    public String documentBackground;
    public String prefersColorScheme;
    /** True when the page had more elements than the probe's cap. */
    public boolean truncated;
    /**
     * True when the words have been stripped out of this capture.
     *
     * <p>Geometry, computed style, text length and line boxes are all intact; only the
     * characters were replaced. Rules that match literal words are skipped rather than
     * being allowed to return nothing and look like a pass.
     */
    public boolean redacted;
    public List<Elem> elements = new ArrayList<>();

    /** Name of the viewport preset this was captured at, e.g. {@code desktop}. */
    public String viewportName;
    /**
     * The viewport the capture asked for, which is not always what the page reports.
     *
     * <p>In mobile emulation Chrome widens {@code window.innerWidth} to the content width
     * when the content overflows -- a 420px element in a 390px viewport yields an
     * {@code innerWidth} of 421 -- so comparing against {@code innerWidth} can never detect
     * horizontal overflow. The width that was requested is the one the design has to fit.
     */
    public int requestedWidth, requestedHeight;
    public long capturedAtEpochMs;

    private transient Map<Integer, Elem> byId;

    public Elem byId(int id) {
        if (byId == null) byId = elements.stream().collect(Collectors.toMap(e -> e.id, e -> e));
        return byId.get(id);
    }

    /** The width the design must fit: what the capture asked for, not what the page reports. */
    public int viewportWidth() { return requestedWidth > 0 ? requestedWidth : innerWidth; }

    public int viewportHeight() { return requestedHeight > 0 ? requestedHeight : innerHeight; }

    public Rect viewportRect() { return new Rect(0, 0, viewportWidth(), viewportHeight()); }

    /** Elements with their own text, in document order. */
    public List<Elem> textElements() {
        return elements.stream().filter(Elem::hasOwnText).toList();
    }

    public List<Elem> interactiveElements() {
        return elements.stream().filter(e -> e.interactive).toList();
    }

    /** Elements at least partly inside the first viewport ("above the fold"). */
    public List<Elem> aboveFold() {
        Rect v = viewportRect();
        return elements.stream().filter(e -> e.rect().intersects(v)).toList();
    }

    /** The children of {@code parent} that survived the probe, in document order. */
    public List<Elem> childrenOf(Elem parent) {
        return elements.stream().filter(e -> e.parentId == parent.id).toList();
    }

    /** The page's body text size: the most common font size among text elements, weighted by text length. */
    public double bodyFontSize() {
        Map<Double, Integer> weight = new java.util.HashMap<>();
        for (Elem e : textElements()) {
            if (e.fontSize < 8 || e.fontSize > 32) continue;
            weight.merge(Math.round(e.fontSize * 2) / 2.0, e.textLength, Integer::sum);
        }
        return weight.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(16.0);
    }

    /** The most prominent heading, by type weight. */
    public Optional<Elem> dominantHeading() {
        return textElements().stream().filter(e -> e.heading > 0)
                .max(java.util.Comparator.comparingDouble(Elem::typographicWeight));
    }

    /** True when a visitor can actually scroll the page sideways. */
    public boolean hasHorizontalOverflow() { return maxScrollX > 1; }

    /**
     * How opaque an element actually renders: the product of its own opacity and every
     * ancestor's.
     *
     * <p>{@code opacity} is not an inherited property, so a child of a 30% watermark
     * reports 1.0 while painting at 0.3. Anything judging how loud an element is, or
     * whether it collides with another, has to use this rather than the raw value.
     */
    public double effectiveOpacity(Elem e) {
        double acc = 1;
        for (Elem cur = e; cur != null; cur = cur.parentId < 0 ? null : byId(cur.parentId)) {
            acc *= cur.opacity;
            if (acc < 0.001) break;
        }
        return acc;
    }
}
