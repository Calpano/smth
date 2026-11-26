package com.calpano.uxlens.corpus;

import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.PageSnapshot;
import com.microsoft.playwright.options.ColorScheme;
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

    /** One page to capture, at one or more viewports and colour schemes. */
    public record Site(String id, String url, List<String> viewports, List<String> schemes,
                       Boolean redact, String note) {

        /**
         * Whether to strip the words from this site's captures. A site may override the
         * run-wide setting: a page you own can keep its text, and one you do not should not
         * be de-redacted by a careless command line.
         */
        public boolean redactWith(boolean runDefault) {
            return redact == null ? runDefault : redact;
        }

        public List<Viewport> resolvedViewports() {
            if (viewports == null || viewports.isEmpty()) {
                return Viewport.defaults();
            }
            return viewports.stream().map(Viewport::parse).toList();
        }

        /**
         * Colour schemes to capture. Defaults to light alone -- capturing dark costs another
         * page load, and is only worth it for a page that declares dark styles at all.
         */
        public List<ColorScheme> resolvedSchemes() {
            if (schemes == null || schemes.isEmpty()) return List.of(ColorScheme.LIGHT);
            return schemes.stream().map(s -> switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "dark" -> ColorScheme.DARK;
                case "light" -> ColorScheme.LIGHT;
                default -> throw new IllegalArgumentException(
                        "Unknown colour scheme '" + s + "' for site " + id);
            }).toList();
        }
    }

    /** One pinned capture: a page at a viewport and scheme, with pixels when they were kept. */
    public record Entry(String siteId, String url, Viewport viewport, ColorScheme scheme,
                        PageSnapshot snapshot, BufferedImage fullPage, BufferedImage fold) {

        /** Stable identifier for this capture, used in labels. */
        public String key() { return siteId + "|" + captureName(viewport, scheme); }

        public boolean hasPixels() { return fullPage != null && fold != null; }

        public boolean isDark() { return scheme == ColorScheme.DARK; }
    }

    /** Directory name for one capture: {@code desktop}, or {@code desktop-dark}. */
    static String captureName(Viewport viewport, ColorScheme scheme) {
        return scheme == ColorScheme.DARK ? viewport.name() + "-dark" : viewport.name();
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
        return capture(probe, dir, withImages, false);
    }

    /**
     * Visits every site and pins what it measures.
     *
     * @param withImages also store screenshots, so pixel-reading rules can be scored too
     * @param redact     strip the words out, keeping everything the rules measure. Use this
     *                   for pages you do not own: a corpus is something you commit and share,
     *                   and it does not need anybody else's copy to do its job
     * @return how many captures were written
     */
    public static int capture(PageProbe probe, Path dir, boolean withImages, boolean redact) {
        int written = 0;
        for (Site site : sites(dir)) {
            for (Viewport viewport : site.resolvedViewports()) {
                for (ColorScheme scheme : site.resolvedSchemes()) {
                    PageProbe.Capture capture = probe.capture(site.url(), viewport, scheme);
                    PageSnapshot snapshot = site.redactWith(redact)
                            ? Redaction.redact(capture.snapshot())
                            : capture.snapshot();
                    Path target = dir.resolve(site.id()).resolve(captureName(viewport, scheme));
                    try {
                        Files.createDirectories(target);
                        Files.writeString(target.resolve("snapshot.json"),
                                GSON.toJson(snapshot), StandardCharsets.UTF_8);
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
        }
        return written;
    }

    /** Loads every pinned capture. Captures stored without images come back without pixels. */
    public static List<Entry> load(Path dir) {
        List<Entry> out = new ArrayList<>();
        for (Site site : sites(dir)) {
            for (Viewport viewport : site.resolvedViewports()) {
                for (ColorScheme scheme : site.resolvedSchemes()) {
                    Path target = dir.resolve(site.id()).resolve(captureName(viewport, scheme));
                    Path snapshotFile = target.resolve("snapshot.json");
                    if (!Files.exists(snapshotFile)) continue;
                    try {
                        PageSnapshot snapshot = GSON.fromJson(
                                Files.readString(snapshotFile, StandardCharsets.UTF_8),
                                PageSnapshot.class);
                        out.add(new Entry(site.id(), site.url(), viewport, scheme, snapshot,
                                readImage(target.resolve("full.png")).orElse(null),
                                readImage(target.resolve("fold.png")).orElse(null)));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not read " + snapshotFile, e);
                    }
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
