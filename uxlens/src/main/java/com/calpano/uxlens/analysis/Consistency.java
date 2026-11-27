package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rgba;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The page as its own specification.
 *
 * <p>This is the highest-yield kind of design check, because it needs no outside
 * knowledge at all. Nobody has to decide what a card's corner radius ought to be: if seven
 * cards use 8px and the eighth uses 6px, the eighth is wrong, and the page said so itself.
 *
 * <p>The discrimination that keeps it quiet is the same one the alignment rules use: a
 * defect is a <em>near miss</em>. A nav link in a different colour from its siblings is the
 * active one; a nav link two shades off is a mistake. So every property declares how close
 * counts as "meant to be the same", and a clean difference is left alone.
 */
public final class Consistency {

    /** A peer group needs this many members before a minority means anything. */
    private static final int MIN_PEERS = 3;
    /** The majority value must be at least this common to count as the intended one. */
    private static final double MIN_MAJORITY = 0.6;
    /** Above this share, the "outliers" are not outliers but a second legitimate variant. */
    private static final double MAX_OUTLIER_SHARE = 0.34;

    private Consistency() { }

    // ---------------------------------------------------------------- peers

    /**
     * Elements that play the same part and should therefore look alike.
     *
     * @param kind       how the group was formed, for the message ("the 4 cards in this row")
     * @param signature  what they have in common
     */
    public record Peers(String kind, String signature, List<Elem> members) {
        public int size() { return members.size(); }
    }

    /**
     * Two kinds of peer group, both defensible.
     *
     * <p><b>Siblings</b> -- the items of one row or stack, which are visibly a set: the cards
     * in a grid, the links in a nav. <b>Cohorts</b> -- every element on the page playing the
     * same part: all the buttons, all the h2s, all the inputs. The first catches a card that
     * drifted from its neighbours; the second catches a button on page three that drifted
     * from every other button in the product.
     */
    public static List<Peers> peerGroups(PageSnapshot snapshot) {
        List<Peers> out = new ArrayList<>();

        for (VisualGroups.Group g : VisualGroups.groups(snapshot)) {
            if (g.size() < MIN_PEERS) continue;
            if (g.members().stream().map(e -> e.tag).distinct().count() != 1) continue;
            // All null counts as "one distinct tag", and a group reported as
            // "4 sibling <null> elements" is not a sentence anybody can act on.
            if (g.members().get(0).tag == null) continue;
            out.add(new Peers(g.axis() == VisualGroups.Axis.ROW ? "row" : "stack",
                    g.size() + " sibling <" + g.members().get(0).tag + "> elements", g.members()));
        }

        Map<String, List<Elem>> cohorts = new LinkedHashMap<>();
        for (Elem e : snapshot.elements) {
            String signature = cohortSignature(e);
            if (signature == null) continue;
            cohorts.computeIfAbsent(signature, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<Elem>> en : cohorts.entrySet()) {
            if (en.getValue().size() < MIN_PEERS) continue;
            out.add(new Peers("cohort", en.getValue().size() + " " + en.getKey(), en.getValue()));
        }
        return out;
    }

    /** What part this element plays, or null when it plays no recognisable one. */
    private static String cohortSignature(Elem e) {
        if (e.fixedLayer) return null;
        if (e.heading > 0) return "<h" + e.heading + "> headings";
        if (e.tag == null) return null;         // a string switch on null throws
        return switch (e.tag) {
            case "button" -> "buttons";
            case "input" -> "inputs";
            case "select" -> "selects";
            case "textarea" -> "textareas";
            case "label" -> "labels";
            case "img" -> e.area() < 3600 ? "icon-sized images" : "images";
            case "a" -> e.paintsSurface() ? "button-like links" : null;    // text links vary by design
            default -> null;
        };
    }

    // ------------------------------------------------------------ properties

    /**
     * One visual property, and the rule under which an inconsistency in it is reported.
     *
     * <p>{@link #isNearMiss} is what stops the whole idea from being noise. It answers: is
     * this difference small enough to read as a slip rather than a decision?
     */
    public interface Property {
        String name();

        String ruleId();

        /** This element's value, or null when the property does not apply to it. */
        String value(Elem e);

        /** True when {@code outlier} is near enough to {@code majority} to be a mistake. */
        boolean isNearMiss(String outlier, String majority);

        /** What to write instead. */
        default String suggest(String majority) { return "Use " + majority + "."; }
    }

    /** Every property worth mining, grouped by the rule that reports it. */
    public static List<Property> properties() {
        return List.of(
                numeric("border-radius", "consistency-shape", e -> e.borderRadius, 6),
                numeric("border-width", "consistency-shape", e -> e.borderTop, 1.5),
                numeric("font-size", "consistency-typography", e -> e.fontSize, 3),
                numeric("font-weight", "consistency-typography", e -> (double) e.fontWeight, 150),
                numeric("letter-spacing", "consistency-typography", e -> e.letterSpacing, 0.8),
                exact("font-family", "consistency-typography", e -> e.fontFamily),
                exact("text-transform", "consistency-typography", e -> e.textTransform),
                color("color", "consistency-color", e -> e.hasOwnText() ? e.fg() : null),
                color("background-color", "consistency-color", e -> Rgba.parse(e.backgroundColor)),
                color("border-color", "consistency-color", e -> e.hasVisibleBorder()
                        ? Rgba.parse(e.borderColor) : null),
                padding(),
                numeric("width", "consistency-size", e -> e.img != null ? e.w : null, 8),
                numeric("height", "consistency-size", e -> e.img != null ? e.h : null, 8),
                exact("transition-duration", "consistency-motion", e ->
                        e.showsPointerCursor() ? e.transitionDuration : null));
    }

    /** A measured value: near-miss when the two are within {@code tolerance}. */
    private static Property numeric(String name, String ruleId,
                                    Function<Elem, Double> read, double tolerance) {
        return new Property() {
            @Override public String name() { return name; }

            @Override public String ruleId() { return ruleId; }

            @Override public String value(Elem e) {
                Double v = read.apply(e);
                return v == null ? null : trim(v);
            }

            @Override public boolean isNearMiss(String outlier, String majority) {
                try {
                    double a = Double.parseDouble(outlier), b = Double.parseDouble(majority);
                    // Zero against non-zero is a different decision, not a slip.
                    if (a == 0 || b == 0) return false;
                    return Math.abs(a - b) <= tolerance;
                } catch (NumberFormatException ex) {
                    return false;
                }
            }

            @Override public String suggest(String majority) { return "Use " + majority + "px."; }
        };
    }

    /** A colour: near-miss when the two are indistinguishable to a reader. */
    private static Property color(String name, String ruleId, Function<Elem, Rgba> read) {
        return new Property() {
            /** RGB distance below which two colours are the same colour, typed twice. */
            static final double SAME = 28;

            @Override public String name() { return name; }

            @Override public String ruleId() { return ruleId; }

            @Override public String value(Elem e) {
                Rgba c = read.apply(e);
                return c == null || c.a() < 0.05 ? null : c.hex();
            }

            @Override public boolean isNearMiss(String outlier, String majority) {
                Rgba a = Rgba.parse(outlier), b = Rgba.parse(majority);
                // A clearly different colour is a state or a rank, not a mistake.
                return a != null && b != null && a.distanceTo(b) <= SAME;
            }
        };
    }

    /** A keyword: any difference within a cohort is a slip, there being no near-ness. */
    private static Property exact(String name, String ruleId, Function<Elem, String> read) {
        return new Property() {
            @Override public String name() { return name; }

            @Override public String ruleId() { return ruleId; }

            @Override public String value(Elem e) {
                String v = read.apply(e);
                return v == null || v.isBlank() || v.equals("none") || v.equals("normal") ? null : v;
            }

            @Override public boolean isNearMiss(String outlier, String majority) { return true; }
        };
    }

    /** All four paddings as one value, since they are set together and read together. */
    private static Property padding() {
        return new Property() {
            @Override public String name() { return "padding"; }

            @Override public String ruleId() { return "consistency-spacing"; }

            @Override public String value(Elem e) {
                if (e.padTop + e.padRight + e.padBottom + e.padLeft <= 0) return null;
                return trim(e.padTop) + " " + trim(e.padRight) + " "
                        + trim(e.padBottom) + " " + trim(e.padLeft);
            }

            @Override public boolean isNearMiss(String outlier, String majority) {
                String[] a = outlier.split(" "), b = majority.split(" ");
                if (a.length != 4 || b.length != 4) return false;
                double worst = 0;
                for (int i = 0; i < 4; i++) {
                    double x = Double.parseDouble(a[i]), y = Double.parseDouble(b[i]);
                    if ((x == 0) != (y == 0)) return false;     // padding on one side only is a choice
                    worst = Math.max(worst, Math.abs(x - y));
                }
                return worst <= 6;
            }

            @Override public String suggest(String majority) {
                return "Use padding:" + majority.replace(" ", "px ") + "px.";
            }
        };
    }

    // ------------------------------------------------------------- analysis

    /** The odd one out, with what the rest of its group does. */
    public record Outlier(Peers peers, Property property, Elem elem,
                          String value, String majority, int majorityCount) { }

    /**
     * Every near-miss inconsistency on the page.
     *
     * <p>Each element is reported at most once per property, by the tightest peer group it
     * belongs to -- being the odd card in a row is more informative than being one of the
     * odd buttons on the page.
     */
    public static List<Outlier> outliers(PageSnapshot snapshot) {
        List<Peers> groups = new ArrayList<>(peerGroups(snapshot));
        groups.sort(Comparator.comparingInt(Peers::size));      // tightest group first
        List<Property> properties = properties();

        Map<String, Outlier> best = new LinkedHashMap<>();
        for (Peers peers : groups) {
            for (Property property : properties) {
                Map<String, List<Elem>> byValue = new LinkedHashMap<>();
                for (Elem e : peers.members()) {
                    String v = property.value(e);
                    if (v != null) byValue.computeIfAbsent(v, k -> new ArrayList<>()).add(e);
                }
                int measured = byValue.values().stream().mapToInt(List::size).sum();
                if (measured < MIN_PEERS || byValue.size() < 2) continue;

                Map.Entry<String, List<Elem>> majority = byValue.entrySet().stream()
                        .max(Comparator.comparingInt(en -> en.getValue().size())).orElseThrow();
                if (majority.getValue().size() / (double) measured < MIN_MAJORITY) continue;

                // A clearly different value is a legitimate variant -- a danger button, an
                // active tab -- so it must not count against the outlier budget. Only the
                // near-misses do; otherwise one red button in a row of blue ones suppresses
                // the real finding sitting beside it.
                List<Map.Entry<String, List<Elem>>> nearMisses = byValue.entrySet().stream()
                        .filter(en -> !en.getKey().equals(majority.getKey()))
                        .filter(en -> property.isNearMiss(en.getKey(), majority.getKey()))
                        .toList();
                int nearMissCount = nearMisses.stream().mapToInt(en -> en.getValue().size()).sum();
                if (nearMissCount == 0) continue;
                if (nearMissCount / (double) measured > MAX_OUTLIER_SHARE) continue;

                for (Map.Entry<String, List<Elem>> en : nearMisses) {
                    for (Elem e : en.getValue()) {
                        best.putIfAbsent(e.id + "|" + property.name(),
                                new Outlier(peers, property, e, en.getKey(),
                                        majority.getKey(), majority.getValue().size()));
                    }
                }
            }
        }
        return List.copyOf(best.values());
    }

    private static String trim(double v) {
        return Math.abs(v - Math.round(v)) < 0.01
                ? String.valueOf(Math.round(v))
                : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
