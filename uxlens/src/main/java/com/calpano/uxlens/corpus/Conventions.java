package com.calpano.uxlens.corpus;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.analysis.Prominence;
import com.calpano.uxlens.analysis.VisualGroups;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Jakob's law, made measurable: people expect your site to work like the other sites they
 * already know.
 *
 * <p>Every other rule in this project judges a page against a principle. This one judges it
 * against the other pages, which is the only honest way to check a claim about what people
 * expect -- an expectation is a fact about a population of sites, not a fact about design.
 * The consistency miner already asks "is this page consistent with itself"; this asks the
 * same question of the web, using the corpus as a stand-in for it.
 *
 * <p>Each convention is a question with a small set of answers that a rendered page can be
 * made to answer: is there a link to the front page in the top left corner, does the
 * navigation sit at the top, are links in prose underlined, what size is body text. A
 * convention is held to exist when enough sites agree on an answer; a page giving a
 * different answer is reported as departing from it.
 *
 * <p><strong>Sites vote, not captures.</strong> The corpus holds twenty-three captures of
 * eleven sites -- shadcn's dashboard appears four times, at two viewports in two colour
 * schemes -- so counting captures would give that one design system four votes and
 * OpenStreetMap two. Each site answers once, by the majority of its own captures, and a
 * site whose captures disagree with each other abstains.
 *
 * <p><strong>What this cannot be.</strong> Eleven sites is not the web. A consensus mined
 * here is a claim about eleven chosen pages, and the choosing was done by one person with
 * a purpose. It is enough to catch a page that puts its logo bottom-right or sets body text
 * at 11px; it is not enough to settle a contested question, and a convention supported by
 * six sites out of eight should be read as a hint and not as a finding about the world. The
 * honest fix is more sites, and the report says how many stood behind each answer so the
 * reader can discount it themselves.
 */
public final class Conventions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Share of answering sites that must agree before an answer counts as a convention. */
    public static final double CONSENSUS_SHARE = 0.75;

    /** Sites that must answer at all before the question is worth asking. */
    public static final int MIN_SITES = 5;

    /** The top band of the page, where navigation and identity live by convention. */
    private static final double HEADER_BAND = 0.18;

    /**
     * How far down the page a header may be pushed and still count as a header.
     *
     * <p>Cookie banners, consent walls and announcement bars displace the real header on a
     * great deal of the web, so a band measured from the top of the viewport misses it.
     */
    private static final double DISPLACED_HEADER_LIMIT = 500;

    private Conventions() { }

    /** One question a rendered page can answer, with the answers it may give. */
    public record Feature(String id, String question, List<String> answers,
                          Function<Analysis, String> read) { }

    /**
     * What the corpus agreed on for one question.
     *
     * @param sitesAgreeing sites giving the winning answer
     * @param sitesAnswering sites for which the question applied at all
     */
    public record Consensus(String featureId, String question, String population,
                            String viewport, String answer, int sitesAgreeing,
                            int sitesAnswering, Map<String, Integer> allAnswers) {

        public double share() {
            return sitesAnswering == 0 ? 0 : sitesAgreeing / (double) sitesAnswering;
        }

        /** True when enough sites answered and enough of them agreed. */
        public boolean isConvention() {
            return sitesAnswering >= MIN_SITES && share() >= CONSENSUS_SHARE;
        }
    }

    /** A page answering a question differently from the sites that set the convention. */
    public record Departure(String key, String featureId, String question, String answer,
                            Consensus consensus) { }

    // ------------------------------------------------------------- the questions

    /**
     * The conventions this asks about.
     *
     * <p>Each has to be answerable from one rendered page, has to have a small number of
     * answers so that agreement means something, and has to be something a visitor could
     * plausibly have an expectation about. A question whose answer is a matter of language
     * or subject matter -- reading direction, how many words are on the page -- is not a
     * convention and is not asked.
     */
    public static List<Feature> features() {
        List<Feature> out = new ArrayList<>();

        out.add(new Feature("identity-top-left",
                "Is there a link into the site's own pages in the top-left corner?",
                List.of("yes", "no"),
                Conventions::identityTopLeft));

        out.add(new Feature("nav-position",
                "Where is the primary navigation?",
                List.of("top", "left", "elsewhere", "none"),
                Conventions::navPosition));

        out.add(new Feature("search-in-header",
                "Is there a search field in the header band?",
                List.of("yes", "no"),
                a -> hasSearchInHeader(a.snapshot()) ? "yes" : "no"));

        out.add(new Feature("footer-landmark",
                "Is there a footer landmark at all?",
                List.of("yes", "no"),
                a -> hasFooterLandmark(a.snapshot()) ? "yes" : "no"));

        out.add(new Feature("body-size",
                "What size is body text?",
                List.of("under-14", "14", "15", "16", "17-18", "over-18"),
                a -> bucketBodySize(a.snapshot().bodyFontSize())));

        out.add(new Feature("prose-links-underlined",
                "Are links inside prose underlined?",
                List.of("yes", "no", "mixed"),
                Conventions::proseLinksUnderlined));

        out.add(new Feature("button-corners",
                "How round are button corners?",
                List.of("square", "slight", "rounded", "very-rounded", "pill"),
                Conventions::buttonCorners));

        out.add(new Feature("pointer-on-buttons",
                "Do buttons show a pointer cursor?",
                List.of("yes", "no", "mixed"),
                Conventions::pointerOnButtons));

        out.add(new Feature("heading-scale",
                "How much larger is the biggest heading than body text?",
                List.of("under-1.5", "1.5-2", "2-3", "over-3"),
                Conventions::headingScale));

        out.add(new Feature("loudest-above-fold",
                "Is the loudest thing above the fold a heading, an image or a control?",
                List.of("heading", "image", "control", "other", "none"),
                Conventions::loudestKind));

        out.add(new Feature("content-starts-left",
                "Does content begin within the first fifth of the viewport width?",
                List.of("yes", "no"),
                Conventions::contentStartsLeft));

        out.add(new Feature("skip-link",
                "Is there a skip link as the first in-page link?",
                List.of("yes", "no"),
                Conventions::hasSkipLink));

        return out;
    }

    /** Every answer one page gives, feature id to answer, with unanswerable ones left out. */
    public static Map<String, String> readAll(Analysis analysis) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Feature feature : features()) {
            try {
                String answer = feature.read().apply(analysis);
                if (answer != null) out.put(feature.id(), answer);
            } catch (RuntimeException e) {
                // A question this page cannot answer is not an answer of "no".
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- the mining

    /**
     * What the corpus agrees on: one vote per site, per viewport.
     *
     * <p>Per viewport because a convention at desktop width and a convention at phone width
     * are different conventions, and because pooling them destroyed the measurement. A site
     * with one desktop and one mobile capture that answered differently -- the logo centred
     * on the phone and left on the desktop, which is itself a convention -- produced a tie
     * and abstained. Nine of twenty-three sites were abstaining that way, so the question
     * that should have had the clearest answer on the whole list was being decided by five
     * votes.
     */
    public static List<Consensus> mine(List<CorpusStore.Entry> corpus, Config config,
                                       Map<String, String> populations) {
        // population/viewport -> site -> feature -> answers
        Map<String, Map<String, Map<String, List<String>>>> byGroup = new LinkedHashMap<>();

        for (CorpusStore.Entry entry : corpus) {
            Analysis analysis = analyse(entry, config);
            String group = populations.getOrDefault(entry.siteId(), "content")
                    + "/" + entry.viewport().name();
            Map<String, Map<String, List<String>>> bySite = byGroup
                    .computeIfAbsent(group, k -> new LinkedHashMap<>());
            readAll(analysis).forEach((feature, answer) -> bySite
                    .computeIfAbsent(entry.siteId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(feature, k -> new ArrayList<>())
                    .add(answer));
        }

        List<Consensus> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<String>>>> group
                : byGroup.entrySet()) {
            String[] parts = group.getKey().split("/", 2);
            for (Feature feature : features()) {
                Map<String, Integer> votes = new LinkedHashMap<>();
                for (Map<String, List<String>> site : group.getValue().values()) {
                    siteVote(site.get(feature.id()))
                            .ifPresent(v -> votes.merge(v, 1, Integer::sum));
                }
                int answering = votes.values().stream().mapToInt(Integer::intValue).sum();
                Optional<Map.Entry<String, Integer>> winner = votes.entrySet().stream()
                        .max(Map.Entry.comparingByValue());
                out.add(new Consensus(feature.id(), feature.question(), parts[0], parts[1],
                        winner.map(Map.Entry::getKey).orElse(null),
                        winner.map(Map.Entry::getValue).orElse(0), answering, sorted(votes)));
            }
        }
        out.sort(Comparator.comparing(Consensus::population)
                .thenComparing(Consensus::viewport)
                .thenComparing(Consensus::featureId));
        return out;
    }

    /** Site id to population, read from the corpus's own {@code sites.json}. */
    public static Map<String, String> populations(Path dir) {
        Map<String, String> out = new LinkedHashMap<>();
        CorpusStore.sites(dir).forEach(s -> out.put(s.id(), s.population()));
        return out;
    }

    /**
     * One site's answer: the majority of its own captures, or nothing.
     *
     * <p>A site whose desktop and mobile captures disagree has no settled answer to give,
     * and inventing one for it would put a coin toss into the consensus.
     */
    private static Optional<String> siteVote(List<String> answers) {
        if (answers == null || answers.isEmpty()) return Optional.empty();
        Map<String, Integer> counts = new LinkedHashMap<>();
        answers.forEach(a -> counts.merge(a, 1, Integer::sum));
        List<Map.Entry<String, Integer>> ranked = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
        if (ranked.size() > 1 && ranked.get(0).getValue().equals(ranked.get(1).getValue())) {
            return Optional.empty();        // its own captures disagree
        }
        return Optional.of(ranked.get(0).getKey());
    }

    /** Where each capture departs from a convention established at its own viewport. */
    public static List<Departure> departures(List<CorpusStore.Entry> corpus,
                                             List<Consensus> consensus, Config config,
                                             Map<String, String> populations) {
        Map<String, Consensus> byKey = new LinkedHashMap<>();
        consensus.stream().filter(Consensus::isConvention)
                .forEach(c -> byKey.put(c.population() + "/" + c.viewport() + "/"
                        + c.featureId(), c));

        List<Departure> out = new ArrayList<>();
        for (CorpusStore.Entry entry : corpus) {
            Analysis analysis = analyse(entry, config);
            String prefix = populations.getOrDefault(entry.siteId(), "content")
                    + "/" + entry.viewport().name() + "/";
            readAll(analysis).forEach((feature, answer) -> {
                Consensus c = byKey.get(prefix + feature);
                if (c == null || answer.equals(c.answer())) return;
                out.add(new Departure(entry.key(), feature, c.question(), answer, c));
            });
        }
        out.sort(Comparator.comparing(Departure::key).thenComparing(Departure::featureId));
        return out;
    }

    private static Analysis analyse(CorpusStore.Entry entry, Config config) {
        return entry.hasPixels()
                ? Analysis.ofStoredCapture(entry.snapshot(), entry.viewport(), config,
                        entry.fullPage(), entry.fold())
                : Analysis.ofSnapshotOnly(entry.snapshot(), entry.viewport(), config);
    }

    // ------------------------------------------------------------- reading a page

    /**
     * Whether something links into the site's own pages from the top-left corner.
     *
     * <p>Three attempts, and the first two are worth recording because both failed in ways
     * that looked like answers.
     *
     * <p>Asking for a link whose path is exactly "/" was the obvious test. MDN's logo points
     * at /en-US/, Wikipedia's at /wiki/Main_Page, and a localised front page is still the
     * front page, so it answered "no identity link" on all twenty-three sites -- the kind of
     * unanimity that should be suspected rather than believed.
     *
     * <p>Asking for the <em>first</em> same-site link by position, and reporting which third
     * of the width it sat in, was fragile in a way that is obvious in hindsight. On the
     * Tailwind docs the logo is at y=18 and the "Docs" nav link at y=16, so a two-pixel
     * difference in a header where everything is visually on one row decided the answer, and
     * that site was recorded as putting its identity on the right.
     *
     * <p>What a visitor expects is simply that the corner is clickable and leads home, so
     * that is the question. The band is anchored to the page's own first header link rather
     * than to the top of the viewport, because a cookie banner displaces the header on a
     * large fraction of the web -- GOV.UK's crown sits at y=337 behind one, and a band
     * measured from zero reported that the most heavily user-tested site in the corpus had
     * no identity link at all.
     */
    private static String identityTopLeft(Analysis a) {
        PageSnapshot s = a.snapshot();
        List<Elem> ownLinks = s.elements.stream()
                .filter(e -> "root".equals(e.linkTarget) || "same-site".equals(e.linkTarget))
                .filter(e -> e.w > 0 && e.h > 0 && !e.fixedLayer)
                .toList();
        if (ownLinks.isEmpty()) return "no";

        double firstRow = ownLinks.stream().mapToDouble(e -> e.y).min().orElse(0);
        if (firstRow > DISPLACED_HEADER_LIMIT) return "no";     // no header to speak of
        double bandBottom = firstRow + s.viewportHeight() * HEADER_BAND;
        double leftThird = s.viewportWidth() * 0.33;

        return ownLinks.stream()
                .filter(e -> e.y <= bandBottom)
                .anyMatch(e -> e.rect().centerX() < leftThird) ? "yes" : "no";
    }

    private static String navPosition(Analysis a) {
        PageSnapshot s = a.snapshot();
        Optional<Elem> nav = s.elements.stream()
                .filter(e -> "nav".equals(e.tag) || "navigation".equals(e.role))
                .filter(e -> e.w > 40 && e.h > 10)
                .max(Comparator.comparingDouble(Elem::area));
        if (nav.isEmpty()) return "none";
        Elem n = nav.get();
        if (n.y <= headerBandBottom(s)) return "top";
        // A tall narrow block down the left side is a sidebar, whatever it is called.
        if (n.x < s.viewportWidth() * 0.25 && n.h > n.w) return "left";
        return "elsewhere";
    }

    private static boolean hasSearchInHeader(PageSnapshot s) {
        double band = headerBandBottom(s);
        return s.elements.stream().anyMatch(e -> e.y <= band && isSearchField(e));
    }

    /**
     * The bottom of the header band, measured from whatever the page puts at the top rather
     * than from the viewport, so a consent banner does not hide the header from every
     * question that asks about it.
     */
    private static double headerBandBottom(PageSnapshot s) {
        double firstLink = s.elements.stream()
                .filter(e -> "root".equals(e.linkTarget) || "same-site".equals(e.linkTarget))
                .filter(e -> e.w > 0 && e.h > 0 && !e.fixedLayer)
                .mapToDouble(e -> e.y)
                .min().orElse(0);
        double top = firstLink > DISPLACED_HEADER_LIMIT ? 0 : firstLink;
        return top + s.viewportHeight() * HEADER_BAND;
    }

    private static boolean isSearchField(Elem e) {
        if (!"input".equals(e.tag) && !"searchbox".equals(e.role)) return false;
        if ("search".equals(e.type) || "searchbox".equals(e.role)) return true;
        String words = ((e.placeholder == null ? "" : e.placeholder) + " "
                + (e.accessibleName == null ? "" : e.accessibleName))
                .toLowerCase(Locale.ROOT);
        return words.contains("search") || words.contains("suche") || words.contains("find");
    }

    /**
     * Whether the page has a footer landmark.
     *
     * <p>Presence only. "A footer in the last fifth of the page" sounded tighter and was
     * simply false: MDN's footer sits at 54% of its scroll height, because the page keeps
     * going afterwards, and that test called one of the best-structured pages in the corpus
     * footerless. Where the footer is turns out to be a different question from whether
     * there is one, and only the second has a conventional answer.
     */
    private static boolean hasFooterLandmark(PageSnapshot s) {
        return s.elements.stream()
                .anyMatch(e -> "footer".equals(e.tag) || "contentinfo".equals(e.role));
    }

    private static String bucketBodySize(double px) {
        if (px <= 0) return null;
        if (px < 13.5) return "under-14";
        if (px < 14.5) return "14";
        if (px < 15.5) return "15";
        if (px < 16.5) return "16";
        if (px <= 18.5) return "17-18";
        return "over-18";
    }

    private static String proseLinksUnderlined(Analysis a) {
        PageSnapshot s = a.snapshot();
        List<Elem> inProse = s.elements.stream()
                .filter(e -> "a".equals(e.tag) && e.hasOwnText())
                .filter(e -> {
                    Elem parent = s.byId(e.parentId);
                    return parent != null && parent.hasOwnText();
                })
                .toList();
        if (inProse.size() < 3) return null;
        long underlined = inProse.stream().filter(Elem::isUnderlined).count();
        if (underlined == inProse.size()) return "yes";
        if (underlined == 0) return "no";
        return "mixed";
    }

    private static String buttonCorners(Analysis a) {
        List<Double> radii = a.snapshot().elements.stream()
                .filter(e -> e.actionable && !e.disabled && e.paintsSurface())
                .filter(e -> e.w >= 40 && e.h >= 20 && e.h <= 80)
                .map(e -> e.borderRadius)
                .toList();
        if (radii.size() < 3) return null;
        double median = radii.stream().sorted().toList().get(radii.size() / 2);
        double height = a.snapshot().elements.stream()
                .filter(e -> e.actionable && e.h >= 20 && e.h <= 80)
                .mapToDouble(e -> e.h).average().orElse(40);
        if (median < 1) return "square";
        if (median <= 3) return "slight";
        if (median >= height / 2 - 1) return "pill";
        if (median <= 8) return "rounded";
        return "very-rounded";
    }

    private static String pointerOnButtons(Analysis a) {
        List<Elem> buttons = a.snapshot().elements.stream()
                .filter(e -> e.actionable && !e.disabled)
                .filter(e -> "button".equals(e.tag) || "button".equals(e.role))
                .toList();
        if (buttons.size() < 3) return null;
        long pointing = buttons.stream().filter(Elem::showsPointerCursor).count();
        if (pointing == buttons.size()) return "yes";
        if (pointing == 0) return "no";
        return "mixed";
    }

    private static String headingScale(Analysis a) {
        double ratio = a.type().largestToBodyRatio();
        if (ratio <= 0) return null;
        if (ratio < 1.5) return "under-1.5";
        if (ratio < 2) return "1.5-2";
        if (ratio <= 3) return "2-3";
        return "over-3";
    }

    private static String loudestKind(Analysis a) {
        Optional<Prominence.Scored> top = a.prominence().ranked().stream()
                .filter(sc -> sc.aboveFold)
                .findFirst();
        if (top.isEmpty()) return "none";
        Elem e = top.get().elem;
        if (e.heading > 0) return "heading";
        if (e.img != null) return "image";
        if (e.actionable) return "control";
        return "other";
    }

    private static String contentStartsLeft(Analysis a) {
        PageSnapshot s = a.snapshot();
        List<Elem> blocks = VisualGroups.majorBlocks(s);
        if (blocks.isEmpty()) return null;
        double leftmost = blocks.stream().mapToDouble(e -> e.x).min().orElse(0);
        return leftmost <= s.viewportWidth() * 0.2 ? "yes" : "no";
    }

    private static String hasSkipLink(Analysis a) {
        PageSnapshot s = a.snapshot();
        return s.elements.stream()
                .filter(e -> "a".equals(e.tag) && "in-page".equals(e.linkTarget))
                .limit(3)
                .anyMatch(e -> {
                    String words = ((e.accessibleName == null ? "" : e.accessibleName) + " "
                            + (e.visibleText == null ? "" : e.visibleText))
                            .toLowerCase(Locale.ROOT);
                    return words.contains("skip") || words.contains("jump to")
                            || words.contains("zum inhalt") || words.contains("springe");
                }) ? "yes" : "no";
    }

    private static Map<String, Integer> sorted(Map<String, Integer> votes) {
        Map<String, Integer> out = new LinkedHashMap<>();
        votes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    // --------------------------------------------------------------- persistence

    /** Writes the mined consensus, so a page can later be judged without the corpus. */
    public static void write(Path file, List<Consensus> consensus) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(consensus) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }

    public static List<Consensus> read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    /** The consensus shipped with the tool, mined from the corpus at build time. */
    public static List<Consensus> bundled() {
        try (var in = Conventions.class.getResourceAsStream("/com/calpano/uxlens/conventions.json")) {
            if (in == null) return List.of();
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<Consensus> parse(String json) {
        List<Consensus> parsed = GSON.fromJson(json,
                new TypeToken<List<Consensus>>() { }.getType());
        return parsed == null ? List.of() : List.copyOf(parsed);
    }
}
