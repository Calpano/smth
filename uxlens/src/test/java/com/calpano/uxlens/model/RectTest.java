package com.calpano.uxlens.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Box geometry")
class RectTest {

    private final Rect a = new Rect(0, 0, 100, 50);

    @Test
    void edges_and_centre() {
        assertEquals(100, a.right());
        assertEquals(50, a.bottom());
        assertEquals(50, a.centerX());
        assertEquals(25, a.centerY());
        assertEquals(5000, a.area());
    }

    @Test
    void touching_boxes_do_not_intersect() {
        assertFalse(a.intersects(new Rect(100, 0, 10, 50)));
        assertEquals(0, a.intersectionArea(new Rect(100, 0, 10, 50)));
    }

    @Test
    void overlap_area_is_the_shared_region() {
        assertEquals(500, a.intersectionArea(new Rect(90, 0, 100, 50)));
        assertTrue(a.intersects(new Rect(90, 0, 100, 50)));
    }

    @Test
    void row_overlap_is_relative_to_the_shorter_box() {
        // a 20px-tall box fully inside a's vertical span shares its whole height
        assertEquals(1.0, a.rowOverlap(new Rect(200, 10, 10, 20)), 0.001);
        // half of a 20px box overlaps a's bottom edge
        assertEquals(0.5, a.rowOverlap(new Rect(200, 40, 10, 20)), 0.001);
        assertEquals(0.0, a.rowOverlap(new Rect(200, 60, 10, 20)), 0.001);
    }

    @Test
    void column_overlap_mirrors_row_overlap() {
        assertEquals(1.0, a.columnOverlap(new Rect(10, 200, 20, 10)), 0.001);
        assertEquals(0.0, a.columnOverlap(new Rect(120, 200, 20, 10)), 0.001);
    }

    @Test
    void gap_is_edge_to_edge_and_zero_when_overlapping() {
        assertEquals(20, a.gapTo(new Rect(120, 0, 10, 50)), 0.001);
        assertEquals(0, a.gapTo(new Rect(50, 25, 10, 10)), 0.001);
        assertEquals(10, a.gapTo(new Rect(0, 60, 100, 10)), 0.001);
    }

    @Test
    void containment_tolerates_sub_pixel_rounding() {
        assertTrue(a.contains(new Rect(0.2, 0.2, 99.6, 49.6)));
        assertFalse(a.contains(new Rect(-2, 0, 100, 50)));
    }

    @Test
    void union_covers_both() {
        Rect u = a.union(new Rect(200, 100, 50, 50));
        assertEquals(new Rect(0, 0, 250, 150), u);
    }

    @Test
    void inflate_grows_on_every_side() {
        assertEquals(new Rect(-5, -5, 110, 60), a.inflate(5));
    }
}
