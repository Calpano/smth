package com.calpano.uxlens.model;

/** An axis-aligned box in CSS pixels, document coordinates. */
public record Rect(double x, double y, double w, double h) {

    public double right()   { return x + w; }
    public double bottom()  { return y + h; }
    public double centerX() { return x + w / 2; }
    public double centerY() { return y + h / 2; }
    public double area()    { return w * h; }

    public boolean intersects(Rect o) {
        return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
    }

    /** Area of the overlap, 0 when disjoint. */
    public double intersectionArea(Rect o) {
        double iw = Math.min(right(), o.right()) - Math.max(x, o.x);
        double ih = Math.min(bottom(), o.bottom()) - Math.max(y, o.y);
        return iw <= 0 || ih <= 0 ? 0 : iw * ih;
    }

    /** True when {@code o} is fully inside this box (a wrapper containing its child). */
    public boolean contains(Rect o) {
        return x <= o.x + 0.5 && y <= o.y + 0.5 && right() >= o.right() - 0.5 && bottom() >= o.bottom() - 0.5;
    }

    /** Vertical overlap as a fraction of the shorter box; how much two boxes share a row. */
    public double rowOverlap(Rect o) {
        double ih = Math.min(bottom(), o.bottom()) - Math.max(y, o.y);
        double min = Math.min(h, o.h);
        return min <= 0 ? 0 : Math.max(0, ih) / min;
    }

    /** Horizontal overlap as a fraction of the narrower box; how much two boxes share a column. */
    public double columnOverlap(Rect o) {
        double iw = Math.min(right(), o.right()) - Math.max(x, o.x);
        double min = Math.min(w, o.w);
        return min <= 0 ? 0 : Math.max(0, iw) / min;
    }

    /** Edge-to-edge distance; 0 when the boxes touch or overlap. */
    public double gapTo(Rect o) {
        double dx = Math.max(0, Math.max(x - o.right(), o.x - right()));
        double dy = Math.max(0, Math.max(y - o.bottom(), o.y - bottom()));
        return Math.hypot(dx, dy);
    }

    public Rect inflate(double by) { return new Rect(x - by, y - by, w + 2 * by, h + 2 * by); }

    public Rect union(Rect o) {
        double nx = Math.min(x, o.x), ny = Math.min(y, o.y);
        return new Rect(nx, ny, Math.max(right(), o.right()) - nx, Math.max(bottom(), o.bottom()) - ny);
    }

    @Override public String toString() {
        return String.format("[%.0f,%.0f %.0fx%.0f]", x, y, w, h);
    }
}
