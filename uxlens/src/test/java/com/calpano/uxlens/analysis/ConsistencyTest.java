package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The discrimination that makes consistency mining usable: a slip is a near miss, a
 * decision is a clean difference.
 *
 * <p>Without it the idea collapses -- every active nav item, every danger button and every
 * primary action becomes a finding, and the whole family gets switched off.
 */
@DisplayName("Consistency mining tells a slip from a decision")
class ConsistencyTest {

    /** A row of sibling buttons, the third one optionally altered. */
    private static PageSnapshot buttonRow(java.util.function.Consumer<Elem> alterThird) {
        PageSnapshot s = new PageSnapshot();
        s.innerWidth = 1200;
        s.innerHeight = 800;
        s.requestedWidth = 1200;
        s.requestedHeight = 800;
        s.documentBackground = "rgba(255,255,255,1)";

        Elem parent = new Elem();
        parent.id = 0;
        parent.parentId = -1;
        parent.tag = "div";
        parent.selector = "#row";
        parent.display = "flex";
        parent.x = 0;
        parent.y = 0;
        parent.w = 1200;
        parent.h = 60;
        parent.childCount = 5;
        s.elements.add(parent);

        for (int i = 0; i < 5; i++) {
            Elem b = new Elem();
            b.id = i + 1;
            b.parentId = 0;
            b.tag = "button";
            b.selector = "#b" + i;
            b.text = "Action " + i;
            b.textLength = 8;
            b.x = i * 120;
            b.y = 0;
            b.w = 100;
            b.h = 40;
            b.display = "inline-block";
            b.borderRadius = 8;
            b.fontSize = 16;
            b.fontWeight = 400;
            b.fontFamily = "Inter";
            b.color = "rgba(255,255,255,1)";
            b.backgroundColor = "rgba(11,79,212,1)";
            b.effectiveBackground = "rgba(11,79,212,1)";
            b.padTop = 10; b.padBottom = 10; b.padLeft = 16; b.padRight = 16;
            b.interactive = true;
            b.actionable = true;
            if (i == 2) alterThird.accept(b);
            s.elements.add(b);
        }
        return s;
    }

    private static List<Consistency.Outlier> mine(java.util.function.Consumer<Elem> alterThird) {
        return Consistency.outliers(buttonRow(alterThird));
    }

    @Test
    void an_identical_row_produces_nothing() {
        assertTrue(mine(e -> { }).isEmpty());
    }

    @Test
    void a_two_pixel_radius_difference_is_a_slip() {
        List<Consistency.Outlier> found = mine(e -> e.borderRadius = 6);
        assertEquals(1, found.size(), found.toString());
        assertEquals("border-radius", found.get(0).property().name());
        assertEquals("6", found.get(0).value());
        assertEquals("8", found.get(0).majority());
        assertEquals(4, found.get(0).majorityCount());
    }

    @Test
    void a_nearly_identical_colour_is_a_slip() {
        List<Consistency.Outlier> found = mine(e -> e.backgroundColor = "rgba(11,80,216,1)");
        assertEquals(1, found.size(), found.toString());
        assertEquals("background-color", found.get(0).property().name());
    }

    @Test
    void a_clearly_different_colour_is_a_decision_and_is_left_alone() {
        // The danger button. This is the case that decides whether the family is usable.
        assertTrue(mine(e -> e.backgroundColor = "rgba(192,57,43,1)").isEmpty(),
                "a red button among blue ones is a decision, not a typo");
    }

    @Test
    void a_decision_does_not_suppress_a_slip_sitting_beside_it() {
        PageSnapshot s = buttonRow(e -> e.backgroundColor = "rgba(192,57,43,1)");
        s.byId(4).backgroundColor = "rgba(11,80,216,1)";        // a fourth button, 2 units off
        List<Consistency.Outlier> found = Consistency.outliers(s);
        assertEquals(1, found.size(), found.toString());
        assertEquals(4, found.get(0).elem().id, "the near-miss, not the red one");
    }

    @Test
    void a_whole_second_variant_is_not_an_outlier() {
        PageSnapshot s = buttonRow(e -> e.borderRadius = 6);
        s.byId(4).borderRadius = 6;
        s.byId(5).borderRadius = 6;        // now 3 of 5 are 6px: that is a variant, not a mistake
        assertTrue(Consistency.outliers(s).isEmpty(),
                "when the minority is a third of the group it is a second style, not a slip");
    }

    @Test
    void a_different_typeface_within_a_cohort_is_always_a_slip() {
        List<Consistency.Outlier> found = mine(e -> e.fontFamily = "Comic Sans MS");
        assertEquals(1, found.size(), found.toString());
        assertEquals("font-family", found.get(0).property().name());
    }

    @Test
    void a_group_of_two_is_too_small_to_have_a_majority() {
        PageSnapshot s = buttonRow(e -> e.borderRadius = 6);
        s.elements.removeIf(e -> e.id > 2);
        assertTrue(Consistency.outliers(s).isEmpty());
    }

    @Test
    void peers_are_found_both_as_siblings_and_as_a_cohort() {
        List<Consistency.Peers> peers = Consistency.peerGroups(buttonRow(e -> { }));
        assertTrue(peers.stream().anyMatch(p -> p.kind().equals("row")), peers.toString());
        assertTrue(peers.stream().anyMatch(p -> p.kind().equals("cohort")), peers.toString());
    }

    @Test
    void padding_on_one_side_only_is_a_choice_rather_than_a_drift() {
        assertTrue(mine(e -> e.padLeft = 0).isEmpty(),
                "removing a side's padding entirely is a layout decision");
    }
}
