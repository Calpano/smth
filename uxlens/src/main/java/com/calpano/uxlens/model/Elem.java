package com.calpano.uxlens.model;

/**
 * One visually relevant element as measured in the live browser.
 *
 * <p>Field names mirror {@code probe.js} one-to-one so Gson maps the probe output
 * straight onto this class. Nothing here is inferred; everything is measured.
 */
public class Elem {

    public int id;
    /** Nearest ancestor that also survived the probe's visibility filter, or -1. */
    public int parentId = -1;

    public String tag;
    /** A CSS selector that resolves to this element, for an agent to act on. */
    public String selector;
    public String role;
    public String ariaLabel;
    public String title;
    /** What a screen reader would announce. Empty when the element has no name at all. */
    public String accessibleName;
    public boolean ariaHidden;
    /** The {@code type} attribute, which is what separates a text field from a button. */
    public String type;
    /** Visible text of this element and its descendants -- what a sighted user reads off it. */
    public String visibleText;
    /** The element's own text nodes only -- not its descendants'. */
    public String text = "";
    public int textLength;

    public double x, y, w, h;

    public double marginTop, marginRight, marginBottom, marginLeft;
    /** Which margins were authored as {@code auto}; their px values are used values, not choices. */
    public boolean marginAutoTop, marginAutoRight, marginAutoBottom, marginAutoLeft;
    public double padTop, padRight, padBottom, padLeft;
    public double borderTop, borderLeft, borderBottom, borderRight, borderRadius;
    public String borderColor;
    public String borderStyle;
    public double gap, columnGap;

    public String display, position, flexDirection;
    /**
     * True when this element, or an ancestor, is {@code position:fixed} or {@code sticky}.
     *
     * <p>Such an element paints in a layer pinned to the viewport, so its document
     * coordinates do not describe what it sits above or beside. A fixed footer bar
     * "overlaps" every paragraph it scrolls past, and means nothing by it.
     */
    public boolean fixedLayer;
    public int zIndex;
    public double opacity = 1;
    public String overflowX, overflowY, textAlign;
    /** The pointer shown over this element -- the strongest hint that it is clickable. */
    public String cursor;
    public String transitionDuration;

    public String fontFamily;
    public double fontSize;
    public int fontWeight = 400;
    public String fontStyle;
    /** Resolved line height in px, or -1 for {@code normal}. */
    public double lineHeight = -1;
    public double letterSpacing;
    public String textTransform;
    public String textDecorationLine;

    public String color;
    public String backgroundColor;
    /** Background actually painted behind this element, composited through ancestors. */
    public String effectiveBackground;
    /** True when a background image or gradient makes the effective background a guess. */
    public boolean backgroundUncertain;
    public boolean hasBackgroundImage;
    public String boxShadow;

    /** Looks clickable: actionable, or merely wearing a pointer cursor. */
    public boolean interactive;
    /**
     * Actually does something: a link with an href, a form control, an explicit ARIA role,
     * an inline handler, or a focusable tabindex. A styled div is not actionable however
     * much it looks like a button.
     */
    public boolean actionable;
    /** 1-6 for h1-h6, 0 otherwise. */
    public int heading;
    public boolean focusable;
    public boolean disabled;

    public int scrollWidth, scrollHeight, clientWidth, clientHeight;
    public boolean textClipped;
    public int childCount;
    /**
     * How many boxes this element paints. Greater than one for an inline element that
     * wraps across lines, whose bounding rect is then the union of its line boxes and
     * describes a shape it never paints.
     */
    public int clientRectCount = 1;

    public Lines lines;
    public Img img;

    /** Line boxes of the element's own text, measured with Range rects. */
    public static class Lines {
        public int lineCount;
        public double firstLineWidth, lastLineWidth, maxLineWidth, firstBaselineY, firstLineLeft;
    }

    /** Raster or vector image facts. */
    public static class Img {
        public int naturalWidth, naturalHeight;
        public String alt;
        public boolean loaded;
        public String objectFit;
        public String src;
    }

    // ---- derived accessors (no state) ----

    public Rect rect() { return new Rect(x, y, w, h); }

    public double area() { return w * h; }

    public Rgba fg() { return Rgba.parse(color); }

    /** The opaque color behind this element's text. */
    public Rgba bg() {
        Rgba b = Rgba.parse(effectiveBackground);
        return b == null ? Rgba.WHITE : b.over(Rgba.WHITE);
    }

    /** Contrast of this element's own text against what is painted behind it, or -1. */
    public double textContrast() {
        Rgba f = fg();
        if (f == null || !hasOwnText()) return -1;
        return f.over(bg()).contrastRatio(bg());
    }

    public boolean hasOwnText() { return text != null && !text.isBlank(); }

    /** True when a screen reader would announce nothing for this element. */
    public boolean hasNoAccessibleName() {
        return accessibleName == null || accessibleName.isBlank();
    }

    /** True when the control shows no words at all -- an icon, a glyph, or nothing. */
    public boolean isIconOnly() {
        return visibleText == null || visibleText.isBlank()
                || visibleText.codePointCount(0, visibleText.length()) <= 2;
    }

    /** True when {@link #rect()} is the union of several line boxes rather than a real shape. */
    public boolean rectIsUnionOfLineBoxes() { return clientRectCount > 1; }

    /** True when the text is underlined -- on the web, the oldest signal that it is a link. */
    public boolean isUnderlined() {
        return textDecorationLine != null && textDecorationLine.contains("underline");
    }

    /** True when the pointer turns into a hand over this element. */
    public boolean showsPointerCursor() { return "pointer".equals(cursor); }

    /**
     * True when the element paints a surface of its own -- a fill, a border or a shadow.
     *
     * <p>A corner radius does not count. A radius with nothing to shape paints no pixels at
     * all, and treating it as a surface made this method answer yes for elements that are
     * visually not there.
     */
    public boolean paintsSurface() {
        Rgba bg = Rgba.parse(backgroundColor);
        if (bg != null && bg.a() > 0.05) return true;
        if (hasVisibleBorder()) return true;
        return boxShadow != null && !boxShadow.isBlank() && !"none".equals(boxShadow);
    }

    /** True when the element paints a visible border on all sides. */
    public boolean hasVisibleBorder() {
        if (borderStyle == null || borderStyle.equals("none") || borderStyle.equals("hidden")) return false;
        Rgba c = Rgba.parse(borderColor);
        return c != null && c.a() > 0.05 && borderTop > 0 && borderBottom > 0;
    }

    /** The y coordinate of the first text baseline, or the box's vertical centre. */
    public double baselineY() {
        return lines != null && lines.firstBaselineY > 0 ? lines.firstBaselineY : y + h / 2;
    }

    /** Approximate characters per line, for measure checks. */
    public double charsPerLine() {
        if (lines == null || lines.lineCount <= 0) return textLength;
        return textLength / (double) lines.lineCount;
    }

    /** WCAG "large text": >=24px, or >=18.66px when bold. */
    public boolean isLargeText() { return fontSize >= 24 || (fontSize >= 18.66 && fontWeight >= 700); }

    /** Resolved line height, falling back to the browser's typical {@code normal}. */
    public double effectiveLineHeight() { return lineHeight > 0 ? lineHeight : fontSize * 1.2; }

    /** Type weight as a designer reads it: size scaled by boldness. */
    public double typographicWeight() { return fontSize * (fontWeight / 400.0); }

    /** True for a container whose job is layout, not content. */
    public boolean isLayoutBox() { return !hasOwnText() && childCount > 0 && img == null; }

    /** Upscale factor of a raster image, 1.0 when served at its natural size. */
    public double upscaleFactor() {
        if (img == null || img.naturalWidth <= 0 || w <= 0) return 1;
        return w / (double) img.naturalWidth;
    }

    /** A short human label used in findings. */
    public String label() {
        StringBuilder sb = new StringBuilder(tag);
        if (role != null) sb.append("[role=").append(role).append(']');
        String t = hasOwnText() ? text : (ariaLabel != null ? ariaLabel : (img != null && img.alt != null ? img.alt : null));
        if (t != null && !t.isBlank()) {
            String trimmed = t.length() > 40 ? t.substring(0, 40) + "…" : t;
            sb.append(" \"").append(trimmed).append('"');
        }
        return sb.toString();
    }

    @Override public String toString() { return "#" + id + " " + label() + " " + rect(); }
}
