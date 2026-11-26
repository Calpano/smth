package com.calpano.uxlens.browser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A viewport to audit at. A layout that is immaculate at 1440px is routinely broken
 * at 390px, so every rule runs once per viewport.
 */
public record Viewport(String name, int width, int height, double deviceScaleFactor, boolean mobile) {

    public static final Viewport DESKTOP = new Viewport("desktop", 1440, 900, 1, false);
    public static final Viewport LAPTOP  = new Viewport("laptop", 1280, 800, 1, false);
    public static final Viewport TABLET  = new Viewport("tablet", 834, 1112, 1, true);
    public static final Viewport MOBILE  = new Viewport("mobile", 390, 844, 1, true);
    public static final Viewport WIDE    = new Viewport("wide", 1920, 1080, 1, false);

    private static final Map<String, Viewport> PRESETS = new LinkedHashMap<>();

    static {
        for (Viewport v : new Viewport[]{DESKTOP, LAPTOP, TABLET, MOBILE, WIDE}) PRESETS.put(v.name, v);
    }

    /** The pair most layout bugs show up in. */
    public static java.util.List<Viewport> defaults() { return java.util.List.of(DESKTOP, MOBILE); }

    public static Map<String, Viewport> presets() { return java.util.Collections.unmodifiableMap(PRESETS); }

    /**
     * Resolves a preset name, or {@code WIDTHxHEIGHT} (optionally {@code @2x}).
     *
     * @throws IllegalArgumentException if the spec is neither
     */
    public static Viewport parse(String spec) {
        Viewport preset = PRESETS.get(spec.trim().toLowerCase());
        if (preset != null) return preset;
        var m = java.util.regex.Pattern.compile("^(\\d+)x(\\d+)(?:@([\\d.]+)x)?$")
                .matcher(spec.trim().toLowerCase());
        if (!m.matches()) {
            throw new IllegalArgumentException("Not a viewport: '" + spec + "'. Use a preset ("
                    + String.join(", ", PRESETS.keySet()) + ") or WIDTHxHEIGHT[@2x].");
        }
        int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
        double dpr = m.group(3) == null ? 1 : Double.parseDouble(m.group(3));
        return new Viewport(w + "x" + h, w, h, dpr, w < 600);
    }

    /** Screenshot pixels for this many CSS pixels. */
    public int devicePx(double cssPx) { return (int) Math.round(cssPx * deviceScaleFactor); }

    @Override public String toString() { return name + " (" + width + "x" + height + ")"; }
}
