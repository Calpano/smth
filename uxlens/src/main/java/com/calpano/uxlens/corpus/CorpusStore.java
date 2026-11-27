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
                       Boolean redact, Boolean images, Boolean fullImage, Boolean sparse,
                       String kind, String note) {

        /**
         * Whether to strip the words from this site's captures. A site may override the
         * run-wide setting: a page you own can keep its text, and one you do not should not
         * be de-redacted by a careless command line.
         */
        public boolean redactWith(boolean runDefault) {
            return redact == null ? runDefault : redact;
        }

        /**
         * Whether to keep this site's pixels. Rules that read the render are skipped on a
         * capture without them, so without this the whole pixel-reading half of the tool has
         * no evidence in the corpus at all. Only the first viewport is kept, which is what
         * those rules look at, and only for the sites worth spending the space on.
         */
        public boolean keepsImages(boolean runDefault) {
            return images == null ? runDefault : images;
        }

        /**
         * Whether to keep the whole-page render as well as the first viewport.
         *
         * <p>Several times the size, and needed by one thing only: per-element saliency,
         * which weighs each element by how much the eye is drawn to it and therefore needs
         * the pixels of the whole scroll, not just the top. Without it anywhere in the
         * corpus that weighting, the findings overlay and the artifact writer have no
         * evidence at all -- which is exactly where they were.
         */
        public boolean keepsFullImage(boolean runDefault) {
            return fullImage == null ? runDefault : fullImage;
        }

        /**
         * True when this page really is as sparse as it looks, so the capture check should
         * not reject it.
         *
         * <p>An empty to-do app is ten elements and does not scroll, which is the same shape
         * as a rate-limit error page. Only the person adding the site knows which it is.
         */
        public boolean isSparse() { return sparse != null && sparse; }

        /**
         * What population this site belongs to: {@code content} or {@code app}.
         *
         * <p>Only the convention mining uses it, and it needs it badly. Jakob's law says
         * people expect a site to work like the others they know -- but which others? A
         * dashboard's user has other dashboards in mind, not Wikipedia. Pooling the two
         * populations hid every convention in the corpus: a link into the site's own pages
         * from the top-left corner is on every content site here and on almost no
         * application, so together they vote fourteen to eight and agree on nothing.
         *
         * <p>Assigning this is editorial, which is why it sits in the committed sites.json
         * where it can be argued with rather than in a heuristic that guesses.
         */
        public String population() { return kind == null ? "content" : kind; }

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

        /**
         * Whether this capture carries pixels at all.
         *
         * <p>The first viewport is what every pixel-reading rule looks at; the whole-page
         * render is needed only for per-element saliency and is optional. Demanding both
         * meant a capture that kept its fold was treated as having no pixels, and the entire
         * pixel half of the tool stayed switched off.
         */
        public boolean hasPixels() { return fold != null; }

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
    public static CaptureReport capture(PageProbe probe, Path dir, boolean withImages) {
        return capture(probe, dir, withImages, false, false);
    }

    public static CaptureReport capture(PageProbe probe, Path dir, boolean withImages,
                                        boolean redact) {
        return capture(probe, dir, withImages, redact, false);
    }

    /** What a capture run managed, and what it did not. */
    public record CaptureReport(int written, int skipped, List<Failure> failures) {

        public boolean isComplete() { return failures.isEmpty(); }
    }

    /** One capture that could not be taken, and why. */
    public record Failure(String siteId, String captureName, String reason) { }

    /**
     * Visits every site and pins what it measures.
     *
     * @param withImages also store screenshots, so pixel-reading rules can be scored too
     * @param redact     strip the words out, keeping everything the rules measure. Use this
     *                   for pages you do not own: a corpus is something you commit and share,
     *                   and it does not need anybody else's copy to do its job
     * @return how many captures were written
     */
    public static CaptureReport capture(PageProbe probe, Path dir, boolean withImages,
                                        boolean redact, boolean skipExisting) {
        int written = 0;
        int skipped = 0;
        List<Failure> failures = new ArrayList<>();
        for (Site site : sites(dir)) {
            for (Viewport viewport : site.resolvedViewports()) {
                for (ColorScheme scheme : site.resolvedSchemes()) {
                    String name = captureName(viewport, scheme);
                    Path target = dir.resolve(site.id()).resolve(name);
                    if (skipExisting && hasSnapshot(target)) {
                        skipped++;
                        continue;
                    }
                    try {
                        PageProbe.Capture capture = probe.capture(site.url(), viewport, scheme);
                        PageSnapshot snapshot = site.redactWith(redact)
                                ? Redaction.redact(capture.snapshot())
                                : capture.snapshot();
                        // Check before pinning. Two captures in this corpus were rate-limit
                        // error pages pinned under the names of real pages and counted as
                        // coverage for months; a snapshot of an error page is a perfectly
                        // valid snapshot, so nothing downstream could ever have noticed.
                        Census.CaptureRow health = Census.inspect(
                                site.id() + "|" + name, site.url(), snapshot, false, false);
                        if (health.isSuspect() && !site.isSparse()) {
                            failures.add(new Failure(site.id(), name,
                                    "does not look like a page (" + String.join(", ",
                                            health.flags()) + "); not pinned. Add "
                                            + "\"sparse\": true to the site if it really is "
                                            + "this small."));
                            continue;
                        }
                        Files.createDirectories(target);
                        writeSnapshot(target, snapshot);
                        if (site.keepsImages(withImages)) {
                            Files.write(target.resolve("fold.png"), capture.foldPng());
                            if (site.keepsFullImage(withImages)) {
                                Files.write(target.resolve("full.png"), capture.fullPagePng());
                            }
                        }
                        written++;
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not write " + target, e);
                    } catch (RuntimeException e) {
                        // One site that times out, refuses automation or changes under us
                        // must not cost the whole run. Record it and carry on.
                        failures.add(new Failure(site.id(), name, shortReason(e)));
                    }
                }
            }
        }
        return new CaptureReport(written, skipped, List.copyOf(failures));
    }

    private static String shortReason(RuntimeException e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        int cut = message.indexOf('\n');
        return cut > 0 ? message.substring(0, cut) : message;
    }

    /** Loads every pinned capture. Captures stored without images come back without pixels. */
    public static List<Entry> load(Path dir) {
        List<Entry> out = new ArrayList<>();
        for (Site site : sites(dir)) {
            for (Viewport viewport : site.resolvedViewports()) {
                for (ColorScheme scheme : site.resolvedSchemes()) {
                    Path target = dir.resolve(site.id()).resolve(captureName(viewport, scheme));
                    if (!hasSnapshot(target)) continue;
                    try {
                        out.add(new Entry(site.id(), site.url(), viewport, scheme,
                                readSnapshot(target),
                                readImage(target.resolve("full.png")).orElse(null),
                                readImage(target.resolve("fold.png")).orElse(null)));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Could not read " + target, e);
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

    /** Where a capture's measurements live. Plain JSON is still read, for older corpora. */
    private static final String GZIPPED = "snapshot.json.gz";
    private static final String PLAIN = "snapshot.json";

    static boolean hasSnapshot(Path target) {
        return Files.exists(target.resolve(GZIPPED)) || Files.exists(target.resolve(PLAIN));
    }

    private static void writeSnapshot(Path target, PageSnapshot snapshot) throws IOException {
        try (java.io.OutputStream out = Files.newOutputStream(target.resolve(GZIPPED));
             java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(out);
             java.io.Writer writer = new java.io.OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            GSON.toJson(snapshot, writer);
        }
        Files.deleteIfExists(target.resolve(PLAIN));
    }

    private static PageSnapshot readSnapshot(Path target) throws IOException {
        Path gzipped = target.resolve(GZIPPED);
        if (Files.exists(gzipped)) {
            try (java.io.InputStream in = Files.newInputStream(gzipped);
                 java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(in);
                 java.io.Reader reader = new java.io.InputStreamReader(gzip, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, PageSnapshot.class);
            }
        }
        return GSON.fromJson(Files.readString(target.resolve(PLAIN), StandardCharsets.UTF_8),
                PageSnapshot.class);
    }

    private static Optional<BufferedImage> readImage(Path file) throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        return Optional.ofNullable(ImageIO.read(file.toFile()));
    }
}
