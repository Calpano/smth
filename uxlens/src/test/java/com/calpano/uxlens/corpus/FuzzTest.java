package com.calpano.uxlens.corpus;

import com.calpano.uxlens.Fixtures;
import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.analysis.Consistency;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.junit.SharedProbe;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deforming the input on purpose: does nothing break, and do the measurements respond?
 *
 * <p>Every assertion here corresponds to a fault this found in code that was passing all
 * of its other tests: an endless loop on a cyclic parent chain, a crash on duplicate
 * element ids, a NaN escaping from an infinite width, a string switch on a null tag, and
 * a grouping pass whose answer depended on the order of the markup.
 */
@ExtendWith(SharedProbe.class)
@DisplayName("Fuzz: robustness, and whether the measurements measure")
class FuzzTest {

    @Test
    void every_mutation_survives_and_every_claim_holds_on_a_real_page(PageProbe probe) {
        PageSnapshot snapshot = probe.capture(Fixtures.broken(), Viewport.DESKTOP).snapshot();
        Fuzz.Result result = Fuzz.run("fixture|desktop", snapshot, Viewport.DESKTOP,
                Config.defaults(), 1);

        assertFalse(result.mutations().isEmpty());
        assertTrue(result.timedOut().isEmpty(),
                () -> "hung: " + result.timedOut().stream()
                        .map(m -> m.mutation().name()).toList());
        assertTrue(result.broken().isEmpty(),
                () -> "not clean: " + result.broken().stream()
                        .map(m -> m.mutation().name()
                                + (m.error() == null ? "" : " threw " + m.error())
                                + (m.nonFiniteMeasures() == 0 ? ""
                                        : " not finite " + m.nonFiniteNames())
                                + m.claims().stream().filter(c -> !c.held() && !c.skipped())
                                        .map(c -> " BROKEN " + c.claim().relation() + " "
                                                + c.claim().measure() + " " + c.before()
                                                + "->" + c.after())
                                        .toList())
                        .toList());
    }

    @Test
    void at_least_one_claim_is_actually_asked() {
        // A guard that is never satisfied makes a claim look like it passed. The run has to
        // report how many were asked, or this whole file proves nothing.
        assertTrue(Fuzz.metamorphic().stream().mapToInt(m -> m.claims().size()).sum() >= 10,
                "there should be real claims to check");
        assertTrue(Fuzz.chaos().size() >= 15, "and real abuse to survive");
        assertTrue(Fuzz.mutations().stream().noneMatch(m -> m.name().isBlank()));
    }

    // ------------------------------------------------- the specific faults found

    @Test
    void a_parent_that_is_its_own_child_does_not_hang_the_isolation_pass() {
        // Unguarded, this consumed ten minutes of CPU in Prominence and printed nothing.
        PageSnapshot s = page(60);
        s.elements.get(1).parentId = s.elements.get(2).id;
        s.elements.get(2).parentId = s.elements.get(1).id;
        for (Elem e : s.elements.subList(3, s.elements.size())) e.parentId = e.id;

        Analysis a = Analysis.ofSnapshotOnly(s, Viewport.DESKTOP, Config.defaults());
        assertTrue(a.prominence().ranked().size() >= 0, "it returned at all");
        assertTrue(s.ancestorsOf(s.elements.get(1)).size() <= 256);
        assertTrue(s.areRelated(s.elements.get(1), s.elements.get(2)));
    }

    @Test
    void duplicate_element_ids_do_not_crash_the_lookup() {
        PageSnapshot s = page(8);
        for (Elem e : s.elements) e.id = 0;
        assertEquals(s.elements.get(0), s.byId(0), "first wins rather than throwing");
    }

    @Test
    void a_null_tag_does_not_throw_from_a_string_switch() {
        PageSnapshot s = page(8);
        for (Elem e : s.elements) { e.tag = null; e.interactive = true; e.actionable = true; }
        Analysis a = Analysis.ofSnapshotOnly(s, Viewport.DESKTOP, Config.defaults());
        assertTrue(Consistency.peerGroups(s).isEmpty(),
                "nothing can be said about elements with no tag: " + Consistency.peerGroups(s)
                        .stream().map(Consistency.Peers::signature).toList());
        assertTrue(a.primaryAction().isEmpty());
    }

    @Test
    void an_infinite_width_does_not_leak_into_a_measurement() {
        PageSnapshot s = page(20);
        s.elements.get(3).w = Double.POSITIVE_INFINITY;
        s.elements.get(4).x = Double.NaN;
        s.elements.get(5).h = Double.NEGATIVE_INFINITY;

        Analysis a = Analysis.ofSnapshotOnly(s, Viewport.DESKTOP, Config.defaults());
        Fuzz.readings(a).forEach((name, value) ->
                assertTrue(Double.isFinite(value), name + " came back as " + value));
    }

    @Test
    void grouping_does_not_depend_on_the_order_of_the_markup(PageProbe probe) {
        // Reversing the element list changes nothing a reader can see. It used to move two
        // peer groups on the Arabic Wikipedia, because the row pass was greedy over the list.
        PageSnapshot forwards = probe.capture(Fixtures.broken(), Viewport.DESKTOP).snapshot();
        PageSnapshot backwards = Fuzz.copy(forwards);
        Collections.reverse(backwards.elements);

        assertEquals(Consistency.peerGroups(forwards).size(),
                Consistency.peerGroups(backwards).size());
        assertEquals(com.calpano.uxlens.analysis.VisualGroups.groups(forwards).size(),
                com.calpano.uxlens.analysis.VisualGroups.groups(backwards).size());
    }

    @Test
    void a_capped_rule_reports_its_uncapped_total_so_it_can_still_respond(PageProbe probe) {
        // A rule over the per-rule cap has its tail replaced by a summary finding, so the
        // count sticks at the ceiling. The fuzz reading has to see past that or a capped
        // rule looks like one that has stopped noticing.
        PageSnapshot s = page(200);
        for (Elem e : s.elements) {
            e.interactive = true;
            e.actionable = true;
            e.tag = "button";
            e.w = 10;
            e.h = 10;
            e.childCount = 0;
        }
        Analysis a = Analysis.ofSnapshotOnly(s, Viewport.MOBILE, Config.defaults());
        double reported = Fuzz.readings(a).get("rule:touch-target");
        assertTrue(reported > Config.defaults().maxFindingsPerRule,
                "read " + reported + ", which is at or below the cap");
    }

    @Test
    void the_spacing_detector_does_not_report_a_finer_grid_than_the_page_has() {
        // Chance-corrected selection. A 2px unit scores about 0.51 on any page at all,
        // because the half-pixel tolerance is a quarter of the unit.
        PageSnapshot s = page(40);
        for (int i = 0; i < s.elements.size(); i++) {
            Elem e = s.elements.get(i);
            e.marginTop = 16;
            e.marginBottom = 24;
            e.padLeft = 8;
            e.padRight = 8;
            e.gap = 16;
        }
        var spacing = com.calpano.uxlens.analysis.SpacingSystem.infer(s);
        assertEquals(8, spacing.baseUnit(), "an 8px page is an 8px page, not a 2px one");
        assertTrue(spacing.adherence() > 0.95);
    }

    @Test
    void a_page_on_no_scale_at_all_is_reported_as_having_none() {
        PageSnapshot s = page(40);
        double[] awkward = {7.3, 11.7, 13.1, 19.9, 23.3};
        for (int i = 0; i < s.elements.size(); i++) {
            Elem e = s.elements.get(i);
            e.marginTop = awkward[i % awkward.length];
            e.padLeft = awkward[(i + 1) % awkward.length];
            e.gap = awkward[(i + 2) % awkward.length];
        }
        var spacing = com.calpano.uxlens.analysis.SpacingSystem.infer(s);
        assertEquals(0, spacing.baseUnit(),
                "reporting a spurious 2px grid is worse than admitting there is none");
    }

    private static PageSnapshot page(int count) {
        PageSnapshot s = new PageSnapshot();
        s.url = "about:blank";
        s.requestedWidth = 1440;
        s.requestedHeight = 900;
        s.scrollHeight = 2000;
        s.documentBackground = "rgba(255,255,255,1)";
        s.elements = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Elem e = new Elem();
            e.id = i;
            e.parentId = i == 0 ? -1 : 0;
            e.tag = i == 0 ? "body" : "div";
            e.selector = "div:nth-of-type(" + i + ")";
            e.text = "item " + i;
            e.textLength = 6;
            e.x = 20 + (i % 5) * 200;
            e.y = 20 + (i / 5) * 60;
            e.w = 180;
            e.h = 40;
            e.fontSize = 16;
            e.fontWeight = 400;
            e.lineHeight = 24;
            e.fontFamily = "Inter";
            e.opacity = 1;
            e.color = "rgba(17,17,17,1)";
            e.backgroundColor = "rgba(0,0,0,0)";
            e.effectiveBackground = "rgba(255,255,255,1)";
            e.display = "block";
            s.elements.add(e);
        }
        return s;
    }

}
