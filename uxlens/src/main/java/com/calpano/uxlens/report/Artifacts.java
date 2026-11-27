package com.calpano.uxlens.report;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.perception.Overlays;
import com.calpano.uxlens.perception.Saliency;
import com.calpano.uxlens.perception.SquintTest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The images that let a human -- or an agent that cannot see -- check what was measured.
 *
 * <p>Every overlay here is evidence rather than decoration. A finding that says "this
 * heading is not the loudest thing on the page" is an assertion; the saliency overlay
 * beside it is the reason, and a reader can disagree with it. Kept separate from the
 * {@code Auditor} so the renderers can be driven from a stored capture with no browser
 * running, which is how the census exercises them.
 */
public final class Artifacts {

    /** Elements to label in the Set-of-Mark overlay. More than this is unreadable. */
    private static final int MARKS = 60;

    private Artifacts() { }

    /**
     * Writes the screenshot and every overlay for one capture.
     *
     * @param dir        where the images go
     * @param relativeTo the directory returned paths are relative to -- usually {@code dir}'s
     *                   parent, so a report one level up can link them
     * @return kind -> relative path, in the order the images were written
     */
    public static Map<String, String> write(Analysis analysis, List<Finding> findings,
                                            List<Saliency.Fixation> fixations, Path dir,
                                            Path relativeTo, double gridStep) {
        Map<String, String> paths = new LinkedHashMap<>();
        try {
            Files.createDirectories(dir);
            double scale = analysis.scale();
            BufferedImage full = analysis.fullPageImage().orElseThrow(
                    () -> new IllegalStateException("No whole-page render in this capture"));
            BufferedImage fold = analysis.foldImage().orElseThrow(
                    () -> new IllegalStateException("No first-viewport render in this capture"));

            put(paths, dir, relativeTo, "screenshot", full);
            put(paths, dir, relativeTo, "fold", fold);
            put(paths, dir, relativeTo, "squint", SquintTest.squint(fold));
            analysis.foldSaliency().ifPresent(sal ->
                    put(paths, dir, relativeTo, "saliency", Overlays.saliencyOver(fold, sal, 0.55f)));
            if (!fixations.isEmpty()) {
                put(paths, dir, relativeTo, "scanpath", Overlays.scanpath(fold, fixations, scale));
            }
            put(paths, dir, relativeTo, "grid", Overlays.grid(fold, gridStep, scale));

            List<Elem> marks = VisualGroups.content(analysis.snapshot()).stream()
                    .filter(e -> e.rect().intersects(analysis.snapshot().viewportRect()))
                    .limit(MARKS).toList();
            put(paths, dir, relativeTo, "set-of-mark", Overlays.setOfMark(fold, marks, scale));

            if (!findings.isEmpty()) {
                put(paths, dir, relativeTo, "findings", Overlays.findings(full, findings, scale));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write artifacts to " + dir, e);
        }
        return paths;
    }

    private static void put(Map<String, String> paths, Path dir, Path relativeTo, String kind,
                            BufferedImage img) {
        Path file = dir.resolve(kind + ".png");
        try {
            ImageIO.write(img, "png", file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
        paths.put(kind, relativeTo.relativize(file).toString());
    }
}
