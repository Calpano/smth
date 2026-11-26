package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.model.Rect;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;

/** Things that are plainly broken: content cut off, pushed off-screen, or on top of itself. */
public final class LayoutRules {

    private LayoutRules() { }

    public static List<Rule> all() {
        return List.of(new HorizontalOverflow(), new ClippedText(), new OffViewport(),
                new TextOverlap(), new TouchTarget());
    }

    /** The page scrolls sideways. On mobile this is the single most common layout defect. */
    public static final class HorizontalOverflow implements Rule {
        @Override public String id() { return "horizontal-overflow"; }

        @Override public String title() { return "Page scrolls horizontally"; }

        @Override public String rationale() {
            return "A horizontal scrollbar on a page that was not designed to scroll sideways "
                    + "means something is wider than its container. It reads as broken and, on "
                    + "touch devices, it fights the vertical scroll gesture.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            if (!s.hasHorizontalOverflow()) return List.of();
            double over = s.scrollWidth - s.viewportWidth();

            // Name the culprits: the widest elements crossing the right edge.
            List<Elem> culprits = s.elements.stream()
                    .filter(e -> e.rect().right() > s.viewportWidth() + 1)
                    .sorted((x, y) -> Double.compare(y.rect().right(), x.rect().right()))
                    .limit(5).toList();

            Finding f = finding(Severity.HIGH, String.format(
                    "The document is %.0fpx wider than the %dpx viewport, so the page scrolls "
                            + "horizontally.", over, s.viewportWidth()))
                    .with("scrollWidth", s.scrollWidth)
                    .with("viewportWidth", s.viewportWidth())
                    .with("overflowPx", Math.round(over))
                    .at(new Rect(0, 0, s.scrollWidth, Math.min(s.scrollHeight, s.viewportHeight())))
                    .suggest(culprits.isEmpty()
                            ? "Find the widest element and constrain it with max-width:100% or min-width:0."
                            : "Constrain " + culprits.get(0).selector + " (right edge at "
                              + Math.round(culprits.get(0).rect().right()) + "px); a fixed width, a "
                              + "long unbroken string, or a flex child without min-width:0 is the usual cause.");
            if (!culprits.isEmpty()) f.about(culprits.toArray(new Elem[0]));
            return List.of(f);
        }
    }

    /** Text taller than the box that holds it, with the overflow hidden. */
    public static final class ClippedText implements Rule {
        @Override public String id() { return "clipped-text"; }

        @Override public String title() { return "Text is clipped by its container"; }

        @Override public String rationale() {
            return "Content that is painted outside a hidden-overflow box is invisible to the "
                    + "reader while still being in the DOM, so it passes content tests and fails "
                    + "the user. It usually appears only at one viewport, or in one language.";
        }

        @Override public List<Finding> check(Analysis a) {
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().elements) {
                if (!e.textClipped) continue;
                int hidden = e.scrollHeight - e.clientHeight;
                boolean deliberate = e.lines != null && e.lines.lineCount <= 2 && hidden < e.fontSize * 1.2;
                out.add(finding(deliberate ? Severity.LOW : Severity.HIGH, String.format(
                        "%s hides %dpx of its content (%dpx of text in a %dpx box).",
                        e.label(), hidden, e.scrollHeight, e.clientHeight))
                        .about(e)
                        .with("hiddenPx", hidden)
                        .with("contentHeight", e.scrollHeight)
                        .with("boxHeight", e.clientHeight)
                        .suggest("Let the box grow (remove the fixed height), or make the truncation "
                                + "explicit with line-clamp plus a tooltip or 'more' affordance."));
            }
            return out;
        }
    }

    /** Content sitting outside the viewport, where nobody can reach it. */
    public static final class OffViewport implements Rule {
        @Override public String id() { return "off-viewport"; }

        @Override public String title() { return "Content sits outside the viewport"; }

        @Override public String rationale() {
            return "An element whose box starts left of zero or ends past the viewport is "
                    + "unreachable without horizontal scrolling, which most users never attempt.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Finding> out = new ArrayList<>();
            for (Elem e : VisualGroups.content(s)) {
                Rect r = e.rect();
                double offRight = r.right() - s.viewportWidth();
                double offLeft = -r.x();
                if (offRight > 1) {
                    out.add(finding(r.x() >= s.viewportWidth() ? Severity.HIGH : Severity.MEDIUM,
                            String.format("%s extends %.0fpx past the right edge of the %dpx viewport.",
                                    e.label(), offRight, s.viewportWidth()))
                            .about(e).with("overflowRightPx", Math.round(offRight))
                            .suggest("Constrain the element or its container; at this viewport it needs "
                                    + "to wrap, shrink, or scroll inside its own box."));
                } else if (offLeft > 1) {
                    out.add(finding(Severity.MEDIUM, String.format(
                            "%s starts %.0fpx left of the viewport.", e.label(), offLeft))
                            .about(e).with("overflowLeftPx", Math.round(offLeft))
                            .suggest("Remove the negative offset, or clip it deliberately with a wrapper."));
                }
            }
            return out;
        }
    }

    /** Two pieces of text painted on top of each other. */
    public static final class TextOverlap implements Rule {
        @Override public String id() { return "text-overlap"; }

        @Override public String title() { return "Text overlaps other content"; }

        @Override public String rationale() {
            return "Overlapping text is never intentional. It appears when an absolutely "
                    + "positioned element meets content it did not expect, or when a container "
                    + "stops growing at a smaller viewport.";
        }

        @Override public List<Finding> check(Analysis a) {
            PageSnapshot s = a.snapshot();
            List<Elem> texts = s.textElements().stream().filter(e -> e.opacity > 0.1).toList();
            List<Finding> out = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                for (int j = i + 1; j < texts.size(); j++) {
                    Elem x = texts.get(i), y = texts.get(j);
                    if (related(s, x, y)) continue;
                    double inter = x.rect().intersectionArea(y.rect());
                    if (inter <= 0) continue;
                    double share = inter / Math.min(x.area(), y.area());
                    if (share < a.config().overlapTolerance) continue;
                    out.add(finding(share > 0.4 ? Severity.HIGH : Severity.MEDIUM, String.format(
                            "%s and %s overlap over %.0f%% of the smaller box.",
                            x.label(), y.label(), share * 100))
                            .about(x, y)
                            .with("overlapShare", Math.round(share * 100) / 100.0)
                            .suggest("Give the two elements their own space in the flow, or raise one "
                                    + "out of the other's way; at this viewport they collide."));
                }
            }
            return out;
        }

        private static boolean related(PageSnapshot s, Elem a, Elem b) {
            for (Elem c = a; c != null && c.parentId >= 0; c = s.byId(c.parentId)) if (c.parentId == b.id) return true;
            for (Elem c = b; c != null && c.parentId >= 0; c = s.byId(c.parentId)) if (c.parentId == a.id) return true;
            return false;
        }
    }

    /** Targets too small to hit. */
    public static final class TouchTarget implements Rule {
        @Override public String id() { return "touch-target"; }

        @Override public String title() { return "Interactive target is too small"; }

        @Override public String rationale() {
            return "A control smaller than the pad of a finger is missed, and a miss on a "
                    + "destructive control is worse than a miss on a benign one. 44px is the "
                    + "figure both platform guidelines and WCAG 2.5.5 settle on.";
        }

        @Override public List<Finding> check(Analysis a) {
            double min = a.viewport().mobile() ? a.config().minTouchTargetPx : a.config().minPointerTargetPx;
            List<Finding> out = new ArrayList<>();
            for (Elem e : a.snapshot().interactiveElements()) {
                if (e.disabled || e.childCount > 2) continue;
                if ("label".equals(e.tag)) continue;              // focuses its field; not a target itself
                if (isInlineLink(a.snapshot(), e)) continue;      // WCAG exempts links inside prose
                double shortest = Math.min(e.w, e.h);
                if (shortest >= min - 0.5) continue;
                out.add(finding(shortest < min * 0.6 ? Severity.HIGH : Severity.MEDIUM, String.format(
                        "%s is %.0fx%.0fpx; its shortest side is %.0fpx against a %.0fpx minimum.",
                        e.label(), e.w, e.h, shortest, min))
                        .about(e)
                        .with("width", e.w).with("height", e.h).with("minimum", min)
                        .suggest(String.format("Grow the control, or add padding / a larger hit area so "
                                + "both sides reach %.0fpx. Padding counts; margin does not.", min)));
            }
            return out;
        }

        private static boolean isInlineLink(PageSnapshot s, Elem e) {
            if (!"a".equals(e.tag)) return false;
            Elem parent = s.byId(e.parentId);
            return parent != null && parent.hasOwnText();
        }
    }
}
