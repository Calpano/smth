package com.calpano.uxlens.junit;

import com.calpano.uxlens.Auditor;
import com.calpano.uxlens.browser.PageProbe;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * One browser for the whole test run.
 *
 * <p>Launching Chromium costs about a second; every other step of an audit costs
 * milliseconds. So the browser is launched once, lazily, stored in JUnit's root context,
 * and closed when the run ends.
 *
 * <pre>{@code
 * @ExtendWith(SharedProbe.class)
 * class LandingPageVisualTest {
 *
 *     @Test
 *     void landing_page_survives_design_review(Auditor auditor) {
 *         Report report = auditor.viewports(Viewport.DESKTOP, Viewport.MOBILE)
 *                                .audit("http://localhost:8080/");
 *         UxAssert.that(report).hasNothingAtOrAbove(Severity.MEDIUM);
 *     }
 * }
 * }</pre>
 *
 * <p>Tests may declare a {@link PageProbe} or an {@link Auditor} parameter. Each injected
 * {@code Auditor} is fresh (so per-test configuration does not leak) but shares the one
 * browser.
 */
public class SharedProbe implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(SharedProbe.class);
    private static final String KEY = "probe";

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == PageProbe.class || type == Auditor.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        PageProbe probe = probeFor(context);
        Class<?> type = parameter.getParameter().getType();
        if (type == PageProbe.class) return probe;
        if (type == Auditor.class) return Auditor.using(probe);
        throw new ParameterResolutionException("Cannot resolve " + type);
    }

    /** The run's browser, launched on first use. */
    public static PageProbe probeFor(ExtensionContext context) {
        ExtensionContext root = context.getRoot();
        return root.getStore(NAMESPACE).getOrComputeIfAbsent(KEY, k -> new Holder(), Holder.class).probe;
    }

    /** Holds the browser and closes it when the test run finishes. */
    private static final class Holder implements ExtensionContext.Store.CloseableResource {
        private final PageProbe probe = PageProbe.launch();

        @Override public void close() { probe.close(); }
    }
}
