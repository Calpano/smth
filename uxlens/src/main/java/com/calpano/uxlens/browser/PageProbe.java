package com.calpano.uxlens.browser;

import com.calpano.uxlens.model.PageSnapshot;
import com.google.gson.Gson;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ColorScheme;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ReducedMotion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Drives a real Chromium and measures what it painted.
 *
 * <p>One probe owns one browser; each {@link #capture} opens a fresh context so pages
 * do not inherit each other's state. Reuse one probe across a whole test class --
 * launching Chromium is by far the slowest step.
 *
 * <pre>{@code
 * try (PageProbe probe = PageProbe.launch()) {
 *     Capture c = probe.capture("https://example.com", Viewport.DESKTOP);
 *     PageSnapshot snap = c.snapshot();
 *     BufferedImage shot = c.fullPageImage();
 * }
 * }</pre>
 */
public class PageProbe implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final String PROBE_JS = loadResource("/com/calpano/uxlens/js/probe.js");

    private final Playwright playwright;
    private final Browser browser;
    /** Extra settle time after the network goes quiet, for entry animations. */
    private int settleMs = 400;
    private boolean freezeAnimations = true;

    private PageProbe(Playwright playwright, Browser browser) {
        this.playwright = playwright;
        this.browser = browser;
    }

    /** Launches headless Chromium with the flags a CI container needs. */
    public static PageProbe launch() { return launch(true); }

    public static PageProbe launch(boolean headless) {
        Playwright pw = Playwright.create();
        try {
            Browser b = pw.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage",
                            "--force-color-profile=srgb", "--font-render-hinting=none",
                            "--hide-scrollbars")));
            return new PageProbe(pw, b);
        } catch (RuntimeException e) {
            pw.close();
            throw new ProbeException("Could not launch Chromium. If the browser is not installed, "
                    + "run 'mvn exec:java -D exec.mainClass=com.microsoft.playwright.CLI "
                    + "-D exec.args=\"install chromium\"', or point PLAYWRIGHT_BROWSERS_PATH at an "
                    + "existing install.", e);
        }
    }

    /** Milliseconds to wait after the network is idle. Default 400. */
    public PageProbe settleMs(int ms) { this.settleMs = ms; return this; }

    /** Whether to pause CSS animations and transitions before measuring. Default true. */
    public PageProbe freezeAnimations(boolean freeze) { this.freezeAnimations = freeze; return this; }

    /**
     * Loads a URL at one viewport and measures it.
     *
     * @param url      http(s), or {@code file://} for a local fixture
     * @param viewport the viewport to render at
     */
    public Capture capture(String url, Viewport viewport) {
        return capture(url, viewport, ColorScheme.LIGHT);
    }

    public Capture capture(String url, Viewport viewport, ColorScheme scheme) {
        Objects.requireNonNull(url, "url");
        try (BrowserContext ctx = newContext(viewport, scheme)) {
            Page page = ctx.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(30_000)
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
            return measure(page, viewport);
        }
    }

    /** Measures an HTML string -- the fast path for unit-testing a component in isolation. */
    public Capture captureHtml(String html, Viewport viewport) {
        return captureHtml(html, viewport, ColorScheme.LIGHT);
    }

    public Capture captureHtml(String html, Viewport viewport, ColorScheme scheme) {
        try (BrowserContext ctx = newContext(viewport, scheme)) {
            Page page = ctx.newPage();
            page.setContent(withViewportMeta(html));
            return measure(page, viewport);
        }
    }

    /**
     * A fragment with no viewport meta is laid out by a mobile context at a 980px layout
     * viewport and then scaled down, so a component "measured at 390px" is really measured
     * at 980px and every responsive finding is wrong. A real page declares the meta; a
     * fragment under test usually forgets to, so it is added.
     */
    private static String withViewportMeta(String html) {
        if (html.contains("name=\"viewport\"") || html.contains("name='viewport'")) return html;
        return "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" + html;
    }

    /**
     * Measures a page you have already driven yourself -- log in, open the modal, hover
     * the menu, then measure that state.
     */
    public Capture measureCurrentState(Page page, Viewport viewport) { return measure(page, viewport); }

    /** Opens a context and hands you the page; you drive it, then call {@link #measureCurrentState}. */
    public BrowserContext newContext(Viewport viewport) { return newContext(viewport, ColorScheme.LIGHT); }

    private BrowserContext newContext(Viewport viewport, ColorScheme scheme) {
        return browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(viewport.width(), viewport.height())
                .setDeviceScaleFactor(viewport.deviceScaleFactor())
                .setIsMobile(viewport.mobile())
                .setHasTouch(viewport.mobile())
                .setColorScheme(scheme)
                .setReducedMotion(ReducedMotion.REDUCE));
    }

    private Capture measure(Page page, Viewport viewport) {
        boolean frozen = freezeAnimations;
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (RuntimeException ignored) {
            // A page with a long-poll or analytics beacon never goes idle; LOAD is enough.
        }
        page.evaluate("() => document.fonts ? document.fonts.ready : null");
        if (freezeAnimations) {
            try {
                page.addStyleTag(new Page.AddStyleTagOptions().setContent(
                        "*,*::before,*::after{animation-play-state:paused!important;"
                                // transition-property, not `transition`: the shorthand resets
                                // transition-duration to 0s and the page's declared timing --
                                // which is part of its design system -- becomes unmeasurable.
                                + "transition-property:none!important;caret-color:transparent!important}"));
            } catch (RuntimeException e) {
                // A strict Content-Security-Policy refuses injected styles. Steadying the
                // page is worth having, not worth losing the page over: measure it moving.
                frozen = false;
            }
        }
        if (settleMs > 0) page.waitForTimeout(settleMs);

        Object raw = page.evaluate(PROBE_JS);
        PageSnapshot snapshot = GSON.fromJson(GSON.toJson(raw), PageSnapshot.class);
        if (snapshot == null || snapshot.elements == null) {
            throw new ProbeException("Probe returned no elements for " + page.url());
        }
        snapshot.viewportName = viewport.name();
        snapshot.requestedWidth = viewport.width();
        snapshot.requestedHeight = viewport.height();
        snapshot.animationsFrozen = frozen;
        snapshot.capturedAtEpochMs = System.currentTimeMillis();

        byte[] full = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        byte[] fold = page.screenshot(new Page.ScreenshotOptions().setFullPage(false));
        return new Capture(snapshot, viewport, full, fold);
    }

    @Override public void close() {
        try {
            browser.close();
        } finally {
            playwright.close();
        }
    }

    /** A page's measurements plus the pixels it was measured from. */
    public record Capture(PageSnapshot snapshot, Viewport viewport, byte[] fullPagePng, byte[] foldPng) {

        /** The whole scrollable page, as pixels. */
        public BufferedImage fullPageImage() { return decode(fullPagePng); }

        /** Just the first viewport -- what a visitor sees before scrolling. */
        public BufferedImage foldImage() { return decode(foldPng); }

        private static BufferedImage decode(byte[] png) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
                if (img == null) throw new ProbeException("Screenshot was not a readable image");
                return img;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static String loadResource(String path) {
        try (InputStream in = PageProbe.class.getResourceAsStream(path)) {
            if (in == null) throw new ProbeException("Missing resource on classpath: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
