package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How long the interface takes to admit that you touched it.
 *
 * <p>Almost everything else in this project measures one still frame, and time is the
 * dimension a still frame does not have. These rules reach the part of it that is written
 * down in the stylesheet: a control that declares a 600ms transition on its own hover
 * state takes 600ms to acknowledge the pointer, and that is readable without waiting for
 * it. The part that has to be waited for -- how long the page takes to answer a click --
 * needs the page driven, and lives in {@code LatencyAudit}.
 *
 * <p>The figure both use is Doherty's. Walter Doherty and Arvind Thadani measured at IBM
 * in 1982 that productivity rose sharply once the machine answered within about 400ms, and
 * it is one of the few numbers in this tool that comes from watching people work rather
 * than from a style guide.
 */
public final class TimingRules {

    private TimingRules() { }

    public static List<Rule> all() {
        return List.of(new SlowStateFeedback());
    }

    /**
     * A control that declares a slow transition on its own state.
     *
     * <p>Scoped to the control's own feedback, which is the case where the delay is pure
     * cost. A long transition on a panel sliding in is a choice about the feel of the
     * thing; a long transition on a button's hover colour means the button spends half a
     * second not yet looking pressed, and nobody asked for that.
     */
    public static final class SlowStateFeedback implements Rule {

        @Override public String id() { return "slow-state-feedback"; }

        @Override public String title() { return "A control is slow to admit it was touched"; }

        @Override public String rationale() {
            return "Doherty and Thadani found that productivity rises once a machine answers "
                    + "inside about 400ms, fast enough that neither party is waiting on the "
                    + "other. A control that declares a longer transition on its own hover, "
                    + "focus or pressed state spends that long not yet looking touched, and the "
                    + "cost is paid on every single interaction with no benefit to set against "
                    + "it. Transitions on things that move or appear are a judgement about feel; "
                    + "a transition on the acknowledgement itself is just latency somebody typed.";
        }

        @Override public List<Finding> check(Analysis a) {
            double doherty = a.config().dohertyThresholdMs;
            double flowBreak = a.config().flowBreakMs;
            List<Finding> out = new ArrayList<>();

            for (Elem e : a.snapshot().elements) {
                if (!e.actionable || e.disabled) continue;
                // A wrapper that happens to be clickable is not the thing being acknowledged.
                if (e.childCount > 2) continue;
                double ms = e.longestTransitionMs();
                if (ms <= doherty) continue;

                boolean breaksFlow = ms >= flowBreak;
                out.add(finding(breaksFlow ? Severity.MEDIUM : Severity.LOW, String.format(
                        Locale.ROOT,
                        "%s declares a %.0fms transition, so it takes %s to show that it was "
                                + "touched (the Doherty threshold is %.0fms).",
                        e.label(), ms,
                        breaksFlow ? "longer than the second that holds a train of thought"
                                : "longer than feels immediate",
                        doherty))
                        .about(e)
                        .with("transitionMs", Math.round(ms))
                        .with("dohertyThresholdMs", doherty)
                        .with("transitionDuration", e.transitionDuration)
                        .suggest(String.format(Locale.ROOT,
                                "Bring the transition on this control's own state under %.0fms "
                                        + "-- 80 to 150ms reads as immediate while still being "
                                        + "visibly a transition. Keep the longer easing for "
                                        + "things that move or appear, not for acknowledging a "
                                        + "pointer.", doherty)));
            }
            return out;
        }
    }
}
