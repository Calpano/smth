package com.calpano.uxlens.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Command line behaviour")
class MainTest {

    private String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void listing_the_rules_needs_no_browser_and_explains_each_one() throws Exception {
        String[] exit = new String[1];
        String out = capture(() -> {
            try {
                exit[0] = String.valueOf(Main.run(new String[]{"rules"}));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("0", exit[0]);
        assertTrue(out.contains("text-contrast"), out);
        assertTrue(out.contains("edge-alignment"), out);
        assertTrue(out.contains("[needs pixels]"), "pixel-dependent rules should be marked");
        assertTrue(out.contains("WCAG"), "the rationale should be printed, not just the title");
    }

    @Test
    void help_exits_cleanly_and_documents_every_command() throws Exception {
        String[] exit = new String[1];
        String out = capture(() -> {
            try {
                exit[0] = String.valueOf(Main.run(new String[]{"--help"}));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals("0", exit[0]);
        for (String command : new String[]{"audit", "focus", "diff", "rules", "--fail-on", "--out"}) {
            assertTrue(out.contains(command), "help does not mention " + command);
        }
    }

    @Test
    void no_arguments_is_a_usage_error() throws Exception {
        assertEquals(2, Main.run(new String[0]));
    }

    @Test
    void an_unknown_command_lists_the_known_ones() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Main.run(new String[]{"screenshot"}));
        assertTrue(e.getMessage().contains("audit"), e.getMessage());
    }

    @Test
    void a_missing_url_is_a_usage_error_rather_than_a_crash() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Main.run(new String[]{"audit"}));
        assertTrue(e.getMessage().contains("argument"), e.getMessage());
    }

    @Test
    void a_bad_severity_names_the_valid_ones() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Main.run(new String[]{"audit", "http://example.test", "--fail-on", "catastrophic"}));
        assertTrue(e.getMessage().contains("high, medium, low"), e.getMessage());
    }

    @Test
    void a_bad_viewport_is_rejected_before_the_browser_starts() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Main.run(new String[]{"audit", "http://example.test", "--viewport", "huge"}));
        assertTrue(e.getMessage().contains("Not a viewport"), e.getMessage());
    }

    @Test
    void a_non_numeric_grid_step_is_rejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Main.run(new String[]{"audit", "http://example.test", "--grid", "eight"}));
        assertTrue(e.getMessage().contains("needs a number"), e.getMessage());
    }
}
