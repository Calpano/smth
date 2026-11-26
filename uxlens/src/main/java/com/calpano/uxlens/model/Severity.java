package com.calpano.uxlens.model;

/**
 * How much a finding would bother a designer reviewing the page.
 *
 * <p>Ordinal order matters: {@code compareTo} is used for thresholds.
 */
public enum Severity {
    /** Worth knowing, not worth fixing on its own. */
    INFO,
    /** A sub-pixel-to-2px slip, or a single outlier in an otherwise consistent system. */
    LOW,
    /** Visible to a trained eye; breaks the page's own rhythm or hierarchy. */
    MEDIUM,
    /** Visible to anyone: clipped content, overlap, unreadable text, broken layout. */
    HIGH;

    public boolean atLeast(Severity min) { return compareTo(min) >= 0; }
}
