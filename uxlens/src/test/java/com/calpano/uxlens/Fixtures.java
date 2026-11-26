package com.calpano.uxlens;

import java.net.URL;
import java.nio.file.Path;

/** Locates the fixture pages on the test classpath. */
public final class Fixtures {

    private Fixtures() { }

    /** A {@code file://} URL for a page under {@code src/test/resources/pages}. */
    public static String url(String name) {
        URL res = Fixtures.class.getResource("/pages/" + name);
        if (res == null) throw new IllegalStateException("Fixture not on the classpath: " + name);
        try {
            return Path.of(res.toURI()).toUri().toString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String clean() { return url("clean.html"); }

    public static String broken() { return url("broken.html"); }
}
