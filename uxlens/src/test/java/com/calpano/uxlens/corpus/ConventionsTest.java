package com.calpano.uxlens.corpus;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jakob's law: judging a page against other pages rather than against a principle.
 *
 * <p>Most of what matters here cannot be asserted, because the answer depends on which
 * sites are in the corpus. What is asserted is the machinery: that a site votes once rather
 * than once per capture, that a site whose own captures disagree abstains, that populations
 * and viewports are kept apart, and that the questions survive the two situations that
 * broke them -- a cookie banner pushing the header down, and two links on the same visual
 * row differing by two pixels of y.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Conventions: what the other sites do")
class ConventionsTest {

    @Test
    void every_question_has_a_small_closed_set_of_answers() {
        for (Conventions.Feature f : Conventions.features()) {
            assertTrue(f.answers().size() >= 2 && f.answers().size() <= 6,
                    f.id() + " has " + f.answers().size() + " answers; agreement between sites "
                            + "means nothing once the answers are nearly unique");
            assertTrue(f.question().endsWith("?"), f.id() + ": " + f.question());
        }
        assertTrue(Conventions.features().size() >= 10);
    }

    @Test
    void a_site_votes_once_however_many_captures_it_has() {
        // Four captures of one design system must not outvote four separate sites.
        List<CorpusStore.Entry> corpus = new ArrayList<>();
        for (String capture : List.of("desktop", "desktop-dark", "mobile", "mobile-dark")) {
            corpus.add(entry("loud", capture, page(true)));
        }
        corpus.add(entry("quiet", "desktop", page(false)));

        List<Conventions.Consensus> consensus = Conventions.mine(corpus, Config.defaults(),
                Map.of("loud", "content", "quiet", "content"));
        Conventions.Consensus identity = pick(consensus, "identity-top-left", "content", "desktop");
        assertEquals(2, identity.sitesAnswering(),
                "two sites answered at desktop, whatever the capture count");
    }

    @Test
    void a_site_whose_own_captures_disagree_abstains() {
        // Not a tie-break: a site with no settled answer has none to give, and inventing one
        // puts a coin toss into the consensus.
        List<CorpusStore.Entry> corpus = List.of(
                entry("split", "desktop", page(true)),
                entry("split", "desktop-dark", page(false)));

        Conventions.Consensus identity = pick(
                Conventions.mine(corpus, Config.defaults(), Map.of("split", "content")),
                "identity-top-left", "content", "desktop");
        assertEquals(0, identity.sitesAnswering());
        assertFalse(identity.isConvention());
    }

    @Test
    void populations_and_viewports_are_kept_apart() {
        // Pooling applications with content sites hid every convention in the corpus: a
        // link home from the top-left corner is on every content site and almost no app.
        List<CorpusStore.Entry> corpus = List.of(
                entry("doc", "desktop", page(true)),
                entry("app", "desktop", page(false)),
                entry("doc", "mobile", page(true)));

        List<Conventions.Consensus> consensus = Conventions.mine(corpus, Config.defaults(),
                Map.of("doc", "content", "app", "app"));
        assertEquals("yes", pick(consensus, "identity-top-left", "content", "desktop").answer());
        assertEquals("no", pick(consensus, "identity-top-left", "app", "desktop").answer());
        assertEquals("yes", pick(consensus, "identity-top-left", "content", "mobile").answer());
    }

    @Test
    void a_consensus_needs_enough_sites_and_enough_agreement() {
        Conventions.Consensus thin = new Conventions.Consensus("f", "q?", "content", "desktop",
                "yes", 3, 3, Map.of("yes", 3));
        assertFalse(thin.isConvention(), "unanimous among three sites is still three sites");

        Conventions.Consensus split = new Conventions.Consensus("f", "q?", "content", "desktop",
                "yes", 6, 10, Map.of("yes", 6, "no", 4));
        assertFalse(split.isConvention(), "a 60% majority is not a convention");

        Conventions.Consensus real = new Conventions.Consensus("f", "q?", "content", "desktop",
                "yes", 9, 10, Map.of("yes", 9, "no", 1));
        assertTrue(real.isConvention());
        assertEquals(0.9, real.share(), 1e-9);
    }

    @Test
    void a_departure_is_only_reported_against_a_convention_in_the_page_s_own_population() {
        List<CorpusStore.Entry> corpus = new ArrayList<>();
        Map<String, String> populations = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            corpus.add(entry("doc" + i, "desktop", page(true)));
            populations.put("doc" + i, "content");
        }
        CorpusStore.Entry odd = entry("odd", "desktop", page(false));
        corpus.add(odd);
        populations.put("odd", "content");

        List<Conventions.Consensus> consensus = Conventions.mine(corpus, Config.defaults(),
                populations);
        List<Conventions.Departure> departures = Conventions.departures(corpus, consensus,
                Config.defaults(), populations);

        assertTrue(departures.stream().allMatch(d -> d.key().startsWith("odd")),
                () -> "only the odd one out should depart: " + departures);
        assertTrue(departures.stream().anyMatch(d -> d.featureId().equals("identity-top-left")),
                () -> departures.toString());
    }

    // -------------------------------------------------- the two readings that broke

    @Test
    void a_cookie_banner_does_not_hide_the_header(PageProbe probe) {
        // GOV.UK's crown sits at y=337 behind a consent banner, and a band measured from the
        // top of the viewport reported that the most heavily user-tested site in the corpus
        // had no link home at all.
        String html = """
                <div style="height:300px;background:#f3f2f1;padding:24px">
                  <p>We use some essential cookies to make this service work.</p>
                </div>
                <header style="background:#0b0c0c;padding:12px 24px">
                  <a href="/" style="color:#fff">The Service</a>
                  <a href="/browse" style="color:#fff;margin-left:24px">Browse</a>
                </header>
                <main style="padding:24px"><h1>A page</h1><p>Some content.</p></main>
                """;
        assertEquals("yes", answer(probe, html, "identity-top-left"));
    }

    @Test
    void two_links_on_one_row_are_not_separated_by_two_pixels_of_y(PageProbe probe) {
        // The Tailwind docs put the logo at y=18 and the first nav link at y=16, so "the
        // first same-site link by position" picked the nav link and recorded that site as
        // putting its identity on the right.
        String html = """
                <header style="position:relative;height:60px">
                  <a href="/" style="position:absolute;left:24px;top:18px">Logo</a>
                  <a href="/docs" style="position:absolute;left:1200px;top:16px">Docs</a>
                </header>
                <main style="padding:24px"><h1>A page</h1><p>Some content.</p></main>
                """;
        assertEquals("yes", answer(probe, html, "identity-top-left"));
    }

    @Test
    void a_page_with_nothing_in_the_corner_says_so(PageProbe probe) {
        String html = """
                <header style="position:relative;height:60px">
                  <a href="/docs" style="position:absolute;left:1200px;top:16px">Docs</a>
                </header>
                <main style="padding:24px"><h1>A page</h1><p>Some content.</p></main>
                """;
        assertEquals("no", answer(probe, html, "identity-top-left"));
    }

    @Test
    void the_bundled_consensus_loads_and_says_how_many_sites_stood_behind_it() {
        List<Conventions.Consensus> bundled = Conventions.bundled();
        assertFalse(bundled.isEmpty(), "a consensus is shipped with the tool");
        for (Conventions.Consensus c : bundled) {
            assertTrue(c.sitesAnswering() >= 0 && c.sitesAgreeing() <= c.sitesAnswering(),
                    c.featureId() + ": " + c.sitesAgreeing() + "/" + c.sitesAnswering());
            assertFalse(c.allAnswers().isEmpty() && c.sitesAnswering() > 0,
                    c.featureId() + " records a vote with no ballots");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String answer(PageProbe probe, String body, String feature) {
        var capture = probe.captureHtml("<body style=\"margin:0;font:16px/1.6 system-ui;"
                + "background:#fff;color:#14161a\">" + body + "</body>", Viewport.DESKTOP);
        Analysis a = Analysis.of(capture, Config.defaults());
        return Conventions.readAll(a).get(feature);
    }

    private static Conventions.Consensus pick(List<Conventions.Consensus> all, String feature,
                                              String population, String viewport) {
        return all.stream()
                .filter(c -> c.featureId().equals(feature))
                .filter(c -> c.population().equals(population))
                .filter(c -> c.viewport().equals(viewport))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no consensus row for " + feature + " / " + population + " / " + viewport));
    }

    private static CorpusStore.Entry entry(String siteId, String capture, PageSnapshot snapshot) {
        Viewport viewport = capture.startsWith("mobile") ? Viewport.MOBILE : Viewport.DESKTOP;
        var scheme = capture.endsWith("-dark")
                ? com.microsoft.playwright.options.ColorScheme.DARK
                : com.microsoft.playwright.options.ColorScheme.LIGHT;
        return new CorpusStore.Entry(siteId, "https://example.test/", viewport, scheme,
                snapshot, null, null);
    }

    /** A minimal page that either does or does not have a link home in the top left. */
    private static PageSnapshot page(boolean identityTopLeft) {
        PageSnapshot s = new PageSnapshot();
        s.url = "https://example.test/";
        s.requestedWidth = 1440;
        s.requestedHeight = 900;
        s.scrollHeight = 2000;
        s.documentBackground = "rgba(255,255,255,1)";
        s.elements = new ArrayList<>();

        Elem body = new Elem();
        body.id = 0;
        body.parentId = -1;
        body.tag = "body";
        body.selector = "body";
        body.w = 1440;
        body.h = 900;
        body.opacity = 1;
        s.elements.add(body);

        Elem logo = new Elem();
        logo.id = 1;
        logo.parentId = 0;
        logo.tag = "a";
        logo.selector = "a.logo";
        logo.linkTarget = "root";
        logo.text = "Home";
        logo.x = identityTopLeft ? 24 : 1300;
        logo.y = 20;
        logo.w = 120;
        logo.h = 32;
        logo.fontSize = 16;
        logo.fontWeight = 400;
        logo.opacity = 1;
        logo.color = "rgba(17,17,17,1)";
        logo.effectiveBackground = "rgba(255,255,255,1)";
        s.elements.add(logo);

        for (int i = 0; i < 5; i++) {
            Elem p = new Elem();
            p.id = 2 + i;
            p.parentId = 0;
            p.tag = "p";
            p.selector = "p:nth-of-type(" + (i + 1) + ")";
            p.text = "Body copy number " + i + " on a page that exists to be measured.";
            p.textLength = 60;
            p.x = 24;
            p.y = 120 + i * 48;
            p.w = 600;
            p.h = 40;
            p.fontSize = 16;
            p.fontWeight = 400;
            p.lineHeight = 26;
            p.opacity = 1;
            p.color = "rgba(17,17,17,1)";
            p.effectiveBackground = "rgba(255,255,255,1)";
            s.elements.add(p);
        }
        return s;
    }
}
