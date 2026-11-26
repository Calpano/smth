package com.calpano.uxlens.model;

/** A color with straight alpha, channels 0-255, alpha 0-1. */
public record Rgba(double r, double g, double b, double a) {

    public static final Rgba WHITE = new Rgba(255, 255, 255, 1);
    public static final Rgba BLACK = new Rgba(0, 0, 0, 1);

    /** Parses the {@code rgba(r,g,b,a)} form the probe emits, plus {@code #rrggbb}. */
    public static Rgba parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty() || s.equals("transparent") || s.equals("none")) return null;
        if (s.startsWith("#")) {
            String h = s.substring(1);
            if (h.length() == 3 || h.length() == 4) {
                StringBuilder sb = new StringBuilder();
                for (char c : h.toCharArray()) sb.append(c).append(c);
                h = sb.toString();
            }
            if (h.length() != 6 && h.length() != 8) return null;
            return new Rgba(
                    Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16),
                    h.length() == 8 ? Integer.parseInt(h.substring(6, 8), 16) / 255.0 : 1);
        }
        int open = s.indexOf('('), close = s.lastIndexOf(')');
        if (open < 0 || close < open) return null;
        String[] parts = s.substring(open + 1, close).split("[,/\\s]+");
        if (parts.length < 3) return null;
        try {
            return new Rgba(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    parts.length > 3 ? Double.parseDouble(parts[3].replace("%", "")) : 1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Alpha-composites this color over an opaque background. */
    public Rgba over(Rgba bg) {
        if (a >= 0.999) return this;
        return new Rgba(r * a + bg.r * (1 - a), g * a + bg.g * (1 - a), b * a + bg.b * (1 - a), 1);
    }

    /** WCAG 2.2 relative luminance. */
    public double relativeLuminance() {
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
    }

    private static double lin(double c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** WCAG 2.2 contrast ratio, 1.0 to 21.0. Both colors must be opaque. */
    public double contrastRatio(Rgba other) {
        double l1 = relativeLuminance(), l2 = other.relativeLuminance();
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    /** HSL saturation, 0-1. Saturated color commands attention out of a muted field. */
    public double saturation() {
        double mx = Math.max(r, Math.max(g, b)) / 255.0, mn = Math.min(r, Math.min(g, b)) / 255.0;
        double l = (mx + mn) / 2;
        if (mx == mn) return 0;
        return l < 0.5 ? (mx - mn) / (mx + mn) : (mx - mn) / (2 - mx - mn);
    }

    /** Perceived lightness, 0-1 (luminance, gamma-corrected for display). */
    public double lightness() { return Math.pow(relativeLuminance(), 1 / 2.2); }

    /** Euclidean distance in RGB, a cheap "is this the same color" test. */
    public double distanceTo(Rgba o) { return Math.sqrt(sq(r - o.r) + sq(g - o.g) + sq(b - o.b)); }

    private static double sq(double v) { return v * v; }

    public String hex() {
        return String.format("#%02x%02x%02x", clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(double v) { return Math.max(0, Math.min(255, (int) Math.round(v))); }

    public int argb() {
        return ((int) Math.round(a * 255) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    @Override public String toString() { return a >= 0.999 ? hex() : hex() + String.format("@%.2f", a); }
}
