package com.calpano.uxlens.corpus;

import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.PageSnapshot;

/**
 * Strips the words out of a capture while keeping everything the rules measure.
 *
 * <p>A snapshot of somebody else's page carries their copy, and a calibration corpus is
 * something you commit and share. Almost nothing in this project reads what the text
 * <em>says</em>: the rules work from geometry, computed style, text <em>length</em> and the
 * line boxes the browser produced, all of which survive redaction untouched.
 *
 * <p>Letters are replaced case for case, so a run of capitals stays a run of capitals and
 * the rule that cares about that keeps working. Digits become zeros. Whitespace and
 * punctuation are left alone, so word and line structure is preserved. Lengths never change,
 * and neither does the geometry, which was measured before any of this.
 *
 * <p>What does <em>not</em> survive is any rule matching literal words -- those declare
 * {@link com.calpano.uxlens.rules.Rule#needsLiteralText()} and are skipped on a redacted
 * capture rather than being allowed to quietly return nothing.
 */
public final class Redaction {

    private Redaction() { }

    /** Redacts in place and marks the snapshot, so nothing downstream can mistake it for real text. */
    public static PageSnapshot redact(PageSnapshot snapshot) {
        snapshot.title = mask(snapshot.title);
        for (Elem e : snapshot.elements) {
            e.text = mask(e.text);
            e.ariaLabel = mask(e.ariaLabel);
            if (e.img != null) {
                // alt="" is a decision and alt=null is an omission; the difference is a
                // finding, so it has to survive.
                e.img.alt = e.img.alt == null ? null : mask(e.img.alt);
                e.img.src = e.img.src == null || e.img.src.isBlank() ? e.img.src : "redacted";
            }
        }
        snapshot.redacted = true;
        return snapshot;
    }

    /**
     * Replaces letters with {@code x} or {@code X} and digits with {@code 0}, leaving
     * length, case and punctuation as they were.
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (Character.isDigit(cp)) {
                sb.append('0');
            } else if (Character.isLetter(cp)) {
                sb.append(Character.isUpperCase(cp) ? 'X' : 'x');
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}
