package com.calpano.uxlens.corpus;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.PageSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pinned captures of real pages, for measuring what the rules do rather than guessing.
 *
 * <p>Every threshold in {@code Config} starts life as somebody's taste. The only way to
 * find out whether a rule earns its place is to run it over pages nobody wrote for it and
 * have a human say, finding by finding, whether it was right. That needs the pages to stop
 * moving -- a live site rewrites the answer every deploy -- so the corpus stores the
 * measurement, not the URL.
 *
 * <p>Snapshots are stored as JSON, which is small, diffable and enough for every rule that
 * works from geometry and computed style. Screenshots are optional and off by default: they
 * are large, and the handful of rules that read pixels can be measured separately.
 */
public final class CorpusStore {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private CorpusStore() { }

    /** One page to capture, at one or more viewports. */
    public record Site(String id, String url, List<String> viewports, String note) {

        public List<Viewport> resolvedViewports() {
            if (viewports == null || viewports.isEmpty()) {
                return Viewport.defaults();
            }
            return viewports.stream().map(Viewport::parse).toList();
        }
    }

    /** One pinned capture: a page at a viewport, with pixels when they were kept. */
    public record Entry(String siteId, String url, Viewport viewport, PageSnapshot snapshot,
                        BufferedImage fullPage, BufferedImage fold) {

        /** Stable identifier for this capture, used in labels. */
        public String key() { return siteId + "|" + viewport.name(); }

        public boolean hasPixels() { return fullPage != null && fold != null; }
    }

    /** Reads {@code sites.json} from the corpus directory. */
    public static List<Site> sites(Path dir) {
        Path file = dir.resolve("sites.json");
        if (!Files.exists(file)) {
            throw new IllegalStateException("No corpus at " + file.toAbsolutePath()
                    + ". Create it with a list of {\"id\", \"url\", \"viewports\"} entries.");
        }
        try {
            Site[] sites = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Site[].class);
            return sites == null ? List.of() : List.of(sites);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Visits every site and pins what it measures.
     *
     * @param withImages also store screenshots, so pixel-reading rules can be scored too
     * @return how many captures were written
     */
    public static int capture(PageProbe probe, Path dir, boolean withImages) {
        int written = 0;
        for (Site site : sites(dir)) {
            for (Viewport viewport : site.resolvedViewports()) {
                PageProbe.Capture capture = probe.capture(site.url(), viewport);
                Path target = dir.resolve(site.id()).resolve(viewport.name());
                try {
                    Files.createDirectories(target);
                    Files.writeString(target.resolve("snapshot.json"),
                            GSON.toJson(capture.snapshot()), StandardCharsets.UTF_8);
                    if (withImages) {
                        Files.write(target.resolve("fold.png"), capture.foldPng());
                        Files.write(target.resolve("full.png"), capture.fullPagePng());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not write " + target, e);
                }
                written++;
            }
        }
        return written;
    }

    /** Loads every pinned capture. Captures stored without images come back without pixels. */
    public static List<Entry> load(Path dir) {
        List<Entry> out = new ArrayList<>();
        for (Site site : sites(dir)) {
            for (Viewport viewport : site.resolvedViewports()) {
                Path target = dir.resolve(site.id()).resolve(viewport.name());
                Path snapshotFile = target.resolve("snapshot.json");
                if (!Files.exists(snapshotFile)) continue;
                try {
                    PageSnapshot snapshot = GSON.fromJson(
                            Files.readString(snapshotFile, StandardCharsets.UTF_8), PageSnapshot.class);
                    out.add(new Entry(site.id(), site.url(), viewport, snapshot,
                            readImage(target.resolve("full.png")).orElse(null),
                            readImage(target.resolve("fold.png")).orElse(null)));
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read " + snapshotFile, e);
                }
            }
        }
        return out;
    }

    /** Writes {@code sites.json}, for building a corpus programmatically. */
    public static void writeSites(Path dir, List<Site> sites) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("sites.json"), PRETTY.toJson(sites), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<BufferedImage> readImage(Path file) throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        return Optional.ofNullable(ImageIO.read(file.toFile()));
    }
}
