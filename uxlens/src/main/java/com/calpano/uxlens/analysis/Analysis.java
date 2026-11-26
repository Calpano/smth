package com.calpano.uxlens.analysis;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.perception.Saliency;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * Everything derived from one capture, computed once and shared by every rule.
 *
 * <p>Rules are cheap; the analysis is not (an FFT, a blur, an O(n²) isolation pass), so it
 * is built once per viewport and handed to all of them.
 */
public final class Analysis {

    /** How far a fixation may sit from a content element and still be attributed to it. */
    private static final double FIXATION_SNAP_PX = 48;

    private final PageSnapshot snapshot;
    private final Viewport viewport;
    private final Config config;
    private final BufferedImage fullPage;
    private final BufferedImage fold;
    private final Saliency foldSaliency;
    private final Saliency pageSaliency;
    private final Prominence prominence;
    private final List<VisualGroups.Group> groups;
    private final SpacingSystem spacing;
    private final TypeSystem type;
    private List<Consistency.Outlier> consistencyOutliers;

    private Analysis(PageSnapshot snapshot, Viewport viewport, Config config,
                     BufferedImage fullPage, BufferedImage fold,
                     Saliency foldSaliency, Saliency pageSaliency,
                     Prominence prominence, List<VisualGroups.Group> groups,
                     SpacingSystem spacing, TypeSystem type) {
        this.snapshot = snapshot;
        this.viewport = viewport;
        this.config = config;
        this.fullPage = fullPage;
        this.fold = fold;
        this.foldSaliency = foldSaliency;
        this.pageSaliency = pageSaliency;
        this.prominence = prominence;
        this.groups = groups;
        this.spacing = spacing;
        this.type = type;
    }

    /** Builds the full analysis, pixels included. */
    public static Analysis of(PageProbe.Capture capture, Config config) {
        BufferedImage full = capture.fullPageImage();
        BufferedImage fold = capture.foldImage();
        Saliency pageSal = Saliency.of(full);
        Saliency foldSal = Saliency.of(fold);
        double scale = capture.viewport().deviceScaleFactor();
        return new Analysis(capture.snapshot(), capture.viewport(), config, full, fold,
                foldSal, pageSal, Prominence.of(capture.snapshot(), pageSal, scale),
                VisualGroups.groups(capture.snapshot()),
                SpacingSystem.infer(capture.snapshot()),
                TypeSystem.infer(capture.snapshot()));
    }

    /**
     * Rebuilds an analysis from a capture stored on disk: a pinned snapshot plus the
     * screenshots it was taken with. Used by the calibration corpus, where the whole point
     * is that the page does not move between runs.
     */
    public static Analysis ofStoredCapture(PageSnapshot snapshot, Viewport viewport, Config config,
                                           BufferedImage fullPage, BufferedImage fold) {
        Saliency pageSal = fullPage == null ? null : Saliency.of(fullPage);
        Saliency foldSal = fold == null ? null : Saliency.of(fold);
        double scale = viewport.deviceScaleFactor();
        return new Analysis(snapshot, viewport, config, fullPage, fold, foldSal, pageSal,
                Prominence.of(snapshot, pageSal, scale), VisualGroups.groups(snapshot),
                SpacingSystem.infer(snapshot), TypeSystem.infer(snapshot));
    }

    /**
     * Builds an analysis from measurements alone, with no pixels. Rules that need the
     * rendered image are skipped; everything geometric still runs. Useful for fast
     * unit tests and for auditing a snapshot captured elsewhere.
     */
    public static Analysis ofSnapshotOnly(PageSnapshot snapshot, Viewport viewport, Config config) {
        return new Analysis(snapshot, viewport, config, null, null, null, null,
                Prominence.of(snapshot), VisualGroups.groups(snapshot),
                SpacingSystem.infer(snapshot), TypeSystem.infer(snapshot));
    }

    public PageSnapshot snapshot() { return snapshot; }

    public Viewport viewport() { return viewport; }

    public Config config() { return config; }

    /** Screenshot pixels per CSS pixel. */
    public double scale() { return viewport.deviceScaleFactor(); }

    public Optional<BufferedImage> fullPageImage() { return Optional.ofNullable(fullPage); }

    public Optional<BufferedImage> foldImage() { return Optional.ofNullable(fold); }

    /** Saliency of the first viewport -- use this for hierarchy and gaze questions. */
    public Optional<Saliency> foldSaliency() { return Optional.ofNullable(foldSaliency); }

    /** Saliency of the whole scrollable page -- use this for per-element weight. */
    public Optional<Saliency> pageSaliency() { return Optional.ofNullable(pageSaliency); }

    public Prominence prominence() { return prominence; }

    public List<VisualGroups.Group> groups() { return groups; }

    public List<VisualGroups.Group> rows() {
        return groups.stream().filter(g -> g.axis() == VisualGroups.Axis.ROW).toList();
    }

    public List<VisualGroups.Group> stacks() {
        return groups.stream().filter(g -> g.axis() == VisualGroups.Axis.COLUMN).toList();
    }

    public SpacingSystem spacing() { return spacing; }

    /**
     * Near-miss inconsistencies against the page's own vocabulary, mined once and shared
     * by every consistency rule.
     */
    public List<Consistency.Outlier> consistencyOutliers() {
        if (consistencyOutliers == null) consistencyOutliers = Consistency.outliers(snapshot);
        return consistencyOutliers;
    }

    public TypeSystem type() { return type; }

    /**
     * Predicted gaze order over the first viewport.
     *
     * <p>Each fixation suppresses the whole content element it landed on, so the path visits
     * distinct things rather than re-reading one wide headline.
     */
    public List<Saliency.Fixation> fixations(int count) {
        if (foldSaliency == null) return List.of();
        double radius = Math.max(40, viewport.width() * scale() * 0.08);
        double scale = scale();
        // Suppress the element *and* the halo a later peak could snap back from, so the
        // attribution and the inhibition agree on what "already looked at" means.
        return foldSaliency.scanpath(count, radius, (x, y) -> contentNear(x / scale, y / scale)
                .map(e -> scaleRect(e.rect().inflate(FIXATION_SNAP_PX), scale))
                .orElse(null));
    }

    /** The smallest content element containing a point in CSS pixels. */
    public Optional<Elem> contentAt(double cssX, double cssY) {
        return VisualGroups.content(snapshot).stream()
                .filter(e -> cssX >= e.x && cssX <= e.rect().right()
                        && cssY >= e.y && cssY <= e.rect().bottom())
                .min(java.util.Comparator.comparingDouble(Elem::area));
    }

    /**
     * The content element a fixation belongs to: the one under the point, or failing that
     * the nearest one within {@link #FIXATION_SNAP_PX}.
     *
     * <p>A peak often lands in the whitespace just outside the thing that caused it --
     * strong edges put energy on both sides of themselves. Reporting "no element here" for
     * such a point wastes the fixation, and leaving it unsuppressed makes the next
     * fixation land on the same thing again.
     */
    public Optional<Elem> contentNear(double cssX, double cssY) {
        Optional<Elem> exact = contentAt(cssX, cssY);
        if (exact.isPresent()) return exact;
        com.calpano.uxlens.model.Rect point =
                new com.calpano.uxlens.model.Rect(cssX, cssY, 0.01, 0.01);
        return VisualGroups.content(snapshot).stream()
                .filter(e -> e.rect().gapTo(point) <= FIXATION_SNAP_PX)
                .min(java.util.Comparator.comparingDouble(e -> e.rect().gapTo(point)));
    }

    private static com.calpano.uxlens.model.Rect scaleRect(com.calpano.uxlens.model.Rect r, double s) {
        return new com.calpano.uxlens.model.Rect(r.x() * s, r.y() * s, r.w() * s, r.h() * s);
    }

    /**
     * The element most likely to be the page's primary action: the loudest interactive
     * element above the fold.
     */
    public Optional<Elem> primaryAction() {
        return prominence.ranked().stream()
                .filter(s -> s.aboveFold && isActionable(s.elem))
                .findFirst()
                .map(s -> s.elem);
    }

    /** Buttons and links only. A label focuses a field; it is not what the page is for. */
    private static boolean isActionable(Elem e) {
        if (!e.interactive || e.disabled) return false;
        return switch (e.tag) {
            case "button", "a", "summary" -> true;
            case "input" -> true;
            default -> "button".equals(e.role) || "link".equals(e.role);
        };
    }
}
