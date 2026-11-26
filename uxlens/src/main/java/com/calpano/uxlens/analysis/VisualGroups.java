package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the page into the sets a designer compares against each other: the items in a
 * navbar, the cards in a grid, the rows of a form, the sections down a page.
 *
 * <p>Alignment and rhythm are only meaningful <em>within</em> such a group. Comparing the
 * left edge of a nav link to the left edge of a footer heading produces noise; comparing
 * it to the other nav links produces findings.
 */
public final class VisualGroups {

    /** How much two boxes must overlap on an axis to count as sharing a row or column. */
    private static final double SHARE_THRESHOLD = 0.6;

    private VisualGroups() { }

    /** A set of siblings laid out along one axis. */
    public record Group(Elem parent, List<Elem> members, Axis axis) {

        public int size() { return members.size(); }

        /** Edge-to-edge gaps between consecutive members, in layout order. */
        public List<Double> gaps() {
            List<Double> gaps = new ArrayList<>();
            for (int i = 1; i < members.size(); i++) {
                Rect a = members.get(i - 1).rect(), b = members.get(i).rect();
                gaps.add(axis == Axis.ROW ? b.x() - a.right() : b.y() - a.bottom());
            }
            return gaps;
        }

        public Rect bounds() {
            Rect r = members.get(0).rect();
            for (Elem e : members) r = r.union(e.rect());
            return r;
        }
    }

    public enum Axis { ROW, COLUMN }

    /**
     * Every sibling group of two or more content-bearing elements, laid out along a
     * detected axis.
     */
    public static List<Group> groups(PageSnapshot snapshot) {
        Map<Integer, List<Elem>> byParent = new HashMap<>();
        for (Elem e : snapshot.elements) {
            if (e.parentId < 0) continue;
            if (e.fixedLayer) continue;
            if (e.position != null && (e.position.equals("absolute") || e.position.equals("fixed"))) continue;
            byParent.computeIfAbsent(e.parentId, k -> new ArrayList<>()).add(e);
        }

        List<Group> out = new ArrayList<>();
        for (Map.Entry<Integer, List<Elem>> en : byParent.entrySet()) {
            Elem parent = snapshot.byId(en.getKey());
            if (parent == null) continue;
            List<Elem> kids = en.getValue();
            if (kids.size() < 2) continue;

            // Split into rows first: anything sharing a row belongs to the same row group.
            for (List<Elem> row : cluster(kids, Axis.ROW)) {
                if (row.size() >= 2) {
                    row.sort(Comparator.comparingDouble(e -> e.x));
                    out.add(new Group(parent, List.copyOf(row), Axis.ROW));
                }
            }
            // Then stacks: elements in a column, each on its own row.
            List<Elem> stackable = new ArrayList<>(kids);
            stackable.sort(Comparator.comparingDouble(e -> e.y));
            List<Elem> stack = new ArrayList<>();
            for (Elem e : stackable) {
                if (stack.isEmpty()) { stack.add(e); continue; }
                Elem last = stack.get(stack.size() - 1);
                boolean sharesColumn = last.rect().columnOverlap(e.rect()) >= SHARE_THRESHOLD;
                boolean below = e.y >= last.rect().bottom() - 1;
                if (sharesColumn && below) {
                    stack.add(e);
                } else {
                    if (stack.size() >= 2) out.add(new Group(parent, List.copyOf(stack), Axis.COLUMN));
                    stack = new ArrayList<>(List.of(e));
                }
            }
            if (stack.size() >= 2) out.add(new Group(parent, List.copyOf(stack), Axis.COLUMN));
        }
        return out;
    }

    /** Only the row groups. */
    public static List<Group> rows(PageSnapshot snapshot) {
        return groups(snapshot).stream().filter(g -> g.axis() == Axis.ROW).toList();
    }

    /** Only the vertical stacks. */
    public static List<Group> stacks(PageSnapshot snapshot) {
        return groups(snapshot).stream().filter(g -> g.axis() == Axis.COLUMN).toList();
    }

    /**
     * The page's major blocks: elements shallow in the tree that span most of the page
     * width. Their left edges are what the eye reads as the page's margin.
     */
    public static List<Elem> majorBlocks(PageSnapshot snapshot) {
        double minWidth = snapshot.viewportWidth() * 0.5;
        List<Elem> blocks = new ArrayList<>();
        for (Elem e : snapshot.elements) {
            if (e.fixedLayer) continue;
            if (e.w < minWidth || e.h < 24) continue;
            if (depth(snapshot, e) > 3) continue;
            blocks.add(e);
        }
        blocks.sort(Comparator.comparingDouble(e -> e.y));
        return blocks;
    }

    private static boolean paintsItsOwnSurface(Elem e) {
        com.calpano.uxlens.model.Rgba bg = com.calpano.uxlens.model.Rgba.parse(e.backgroundColor);
        return bg != null && bg.a() > 0.05;
    }

    /** Depth below {@code body}. */
    public static int depth(PageSnapshot snapshot, Elem e) {
        int d = 0;
        Elem cur = e;
        while (cur != null && cur.parentId >= 0 && d < 50) {
            cur = snapshot.byId(cur.parentId);
            d++;
        }
        return d;
    }

    /** Groups elements that mutually overlap on the given axis. */
    private static List<List<Elem>> cluster(List<Elem> elements, Axis axis) {
        List<List<Elem>> clusters = new ArrayList<>();
        for (Elem e : elements) {
            List<Elem> target = null;
            for (List<Elem> c : clusters) {
                boolean shares = c.stream().allMatch(o -> axis == Axis.ROW
                        ? o.rect().rowOverlap(e.rect()) >= SHARE_THRESHOLD
                        : o.rect().columnOverlap(e.rect()) >= SHARE_THRESHOLD);
                if (shares) { target = c; break; }
            }
            if (target == null) clusters.add(new ArrayList<>(List.of(e)));
            else target.add(e);
        }
        return clusters;
    }

    /**
     * Content-bearing elements: what a designer actually looks at, as opposed to the
     * wrapper divs that hold them.
     */
    public static List<Elem> content(PageSnapshot snapshot) {
        return snapshot.elements.stream()
                // The page's own surface is a backdrop, not something to look at.
                .filter(e -> !"body".equals(e.tag) && !"html".equals(e.tag) && !"main".equals(e.tag))
                .filter(e -> e.hasOwnText() || e.img != null
                        || (e.interactive && e.childCount == 0)
                        || (e.hasBackgroundImage && e.childCount == 0 && e.area() > 400)
                        // a painted leaf block: a rule, a swatch, a divider, a coloured band
                        || (e.childCount == 0 && e.area() > 400 && paintsItsOwnSurface(e)))
                .toList();
    }
}
