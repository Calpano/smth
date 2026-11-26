package com.calpano.uxlens.browser;

/** Thrown when the browser could not be driven or the probe could not be read. */
public class ProbeException extends RuntimeException {
    public ProbeException(String message) { super(message); }
    public ProbeException(String message, Throwable cause) { super(message, cause); }
}
