package com.calpano.uxlens.analysis;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inferring the page's own design system, on hand-built snapshots so the expected answer
 * is arithmetic rather than a browser's opinion.
 */
@DisplayName("Inferring the design system a page actually uses")
class InferredSystemsTest {

    private static PageSnapshot snapshot(Elem... elements) {
        PageSnapshot s = new PageSnapshot();
        s.innerWidth = 1440;
        s.innerHeight = 900;
        s.scrollWidth = 1440;
        s.scrollHeight = 2000;
        s.documentBackground = "rgba(255,255,255,1)";
        for (int i = 0; i < elements.length; i++) {
            elements[i].id = i;
            s.elements.add(elements[i]);
        }
        return s;
    }

    private static Elem spaced(String selector, double margin, double padding) {
        Elem e = new Elem();
        e.tag = "div";
        e.selector = selector;
        e.w = 100;
        e.h = 40;
        e.marginTop = margin;
        e.padLeft = padding;
        e.display = "block";
        return e;
    }

    private static Elem text(String content, double fontSize, int weight, int length) {
        Elem e = new Elem();
        e.tag = "p";
        e.selector = "p";
        e.text = content;
        e.textLength = length;
        e.fontSize = fontSize;
        e.fontWeight = weight;
        e.fontFamily = "Inter";
        e.w = 600;
        e.h = fontSize * 1.5;
        e.display = "block";
        e.color = "rgba(0,0,0,1)";
        e.effectiveBackground = "rgba(255,255,255,1)";
        return e;
    }

    @Test
    void an_eight_pixel_page_is_recognised_as_eight_not_four() {
        SpacingSystem sp = SpacingSystem.infer(snapshot(
                spaced("a", 8, 16), spaced("b", 16, 24), spaced("c", 32, 8), spaced("d", 24, 48)));
        assertEquals(8, sp.baseUnit(), "the largest unit that explains the values should win");
        assertEquals(1.0, sp.adherence(), 0.001);
        assertTrue(sp.outliers().isEmpty());
    }

    @Test
    void a_page_on_a_five_pixel_scale_is_judged_on_five() {
        SpacingSystem sp = SpacingSystem.infer(snapshot(
                spaced("a", 5, 10), spaced("b", 15, 20), spaced("c", 25, 30), spaced("d", 10, 5)));
        assertEquals(5, sp.baseUnit());
        assertTrue(sp.outliers().isEmpty(), "nothing is wrong with a 5px scale: " + sp.outliers());
    }

    @Test
    void one_stray_value_is_reported_with_the_value_that_was_meant() {
        SpacingSystem sp = SpacingSystem.infer(snapshot(
                spaced("a", 8, 16), spaced("b", 16, 24), spaced("c", 32, 8),
                spaced("d", 24, 48), spaced("e", 15, 16)));
        assertEquals(8, sp.baseUnit());
        assertEquals(1, sp.outliers().size(), sp.outliers().toString());
        SpacingSystem.Outlier o = sp.outliers().get(0);
        assertEquals(15.0, o.value(), 0.001);
        assertEquals(16.0, o.nearestOnScale(), 0.001);
        assertEquals("margin-top", o.property());
    }

    @Test
    void nearly_correct_outliers_are_reported_before_wild_ones() {
        SpacingSystem sp = SpacingSystem.infer(snapshot(
                spaced("a", 8, 16), spaced("b", 16, 24), spaced("c", 32, 8), spaced("d", 24, 48),
                spaced("wild", 13, 16), spaced("near", 17, 16)));
        assertTrue(sp.outliers().get(0).drift() <= sp.outliers().get(1).drift(),
                "sorted by how nearly right they are: " + sp.outliers());
        assertEquals(17.0, sp.outliers().get(0).value(), 0.001);
    }

    @Test
    void sub_pixel_values_are_not_treated_as_offences() {
        SpacingSystem sp = SpacingSystem.infer(snapshot(
                spaced("a", 8, 16), spaced("b", 16.4, 24), spaced("c", 32, 7.8), spaced("d", 24, 48)));
        assertEquals(8, sp.baseUnit());
        assertTrue(sp.outliers().isEmpty(), sp.outliers().toString());
    }

    @Test
    void the_body_size_is_the_size_most_of_the_text_is_set_at() {
        TypeSystem t = TypeSystem.infer(snapshot(
                text("heading", 48, 700, 20),
                text("body one", 16, 400, 400),
                text("body two", 16, 400, 600),
                text("caption", 13, 400, 40)));
        assertEquals(16.0, t.bodySize(), 0.001);
        assertEquals(3.0, t.largestToBodyRatio(), 0.001);
        assertEquals(3, t.scale().size());
    }

    @Test
    void a_size_used_for_almost_nothing_next_to_a_popular_one_is_a_near_duplicate() {
        TypeSystem t = TypeSystem.infer(snapshot(
                text("body", 16, 400, 800),
                text("stray", 15, 400, 12),
                text("heading", 32, 700, 30)));
        assertTrue(t.nearDuplicateSizes(2).contains(15.0), t.nearDuplicateSizes(2).toString());
        assertFalse(t.nearDuplicateSizes(2).contains(32.0));
    }

    @Test
    void a_flat_page_has_a_low_largest_to_body_ratio() {
        TypeSystem t = TypeSystem.infer(snapshot(
                text("body", 16, 400, 900), text("heading", 19, 700, 40)));
        assertTrue(t.largestToBodyRatio() < 1.5, "ratio was " + t.largestToBodyRatio());
    }

    @Test
    void grouping_finds_a_row_of_cards_and_the_stack_they_sit_in() {
        PageSnapshot s = new PageSnapshot();
        s.innerWidth = 1200;
        s.innerHeight = 800;
        Elem parent = spaced("#grid", 0, 0);
        parent.w = 1200;
        parent.h = 200;
        parent.display = "flex";
        parent.childCount = 3;
        s.elements.add(parent);
        parent.id = 0;
        for (int i = 0; i < 3; i++) {
            Elem card = spaced(".card" + i, 0, 0);
            card.id = i + 1;
            card.parentId = 0;
            card.x = i * 400;
            card.y = 0;
            card.w = 380;
            card.h = 200;
            card.text = "Card " + i;
            card.textLength = 6;
            s.elements.add(card);
        }
        var rows = VisualGroups.rows(s);
        assertEquals(1, rows.size(), rows.toString());
        assertEquals(3, rows.get(0).size());
        assertEquals(java.util.List.of(20.0, 20.0), rows.get(0).gaps());
    }
}
