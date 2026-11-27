package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The checks a design review runs, and the order they are reported in. */
public final class RuleSet {

    private final List<Rule> rules;

    private RuleSet(List<Rule> rules) { this.rules = List.copyOf(rules); }

    /** Everything. */
    public static RuleSet standard() {
        List<Rule> rules = new ArrayList<>();
        rules.addAll(LayoutRules.all());
        rules.addAll(AlignmentRules.all());
        rules.addAll(RhythmRules.all());
        rules.addAll(TypographyRules.all());
        rules.addAll(ColorRules.all());
        rules.addAll(HierarchyRules.all());
        rules.addAll(ProximityRules.all());
        rules.addAll(ImageRules.all());
        rules.addAll(ConsistencyRules.all());
        rules.addAll(AffordanceRules.all());
        rules.addAll(OpticalRules.all());
        rules.addAll(SystemRules.all());
        rules.addAll(BokRules.all());
        rules.addAll(HeuristicRules.all());
        rules.addAll(GestaltRules.all());
        rules.addAll(NielsenRules.all());
        rules.addAll(TimingRules.all());
        return new RuleSet(rules);
    }

    public static RuleSet of(Rule... rules) { return new RuleSet(List.of(rules)); }

    public static RuleSet of(List<Rule> rules) { return new RuleSet(rules); }

    public List<Rule> rules() { return rules; }

    /**
     * Keeps the most severe findings and replaces the tail with a count.
     *
     * <p>A rule that fires five hundred times has said what it has to say; printing all of
     * it buries every other rule in the report.
     */
    private static List<Finding> cap(Rule rule, List<Finding> found, int max) {
        if (max <= 0 || found.size() <= max) return found;
        List<Finding> kept = new ArrayList<>(found.stream()
                .sorted(Comparator.comparing((Finding f) -> f.severity).reversed())
                .limit(max)
                .toList());
        int hidden = found.size() - kept.size();
        kept.add(new Finding(rule.id(), kept.get(kept.size() - 1).severity, String.format(
                "%d further %s finding%s on this page were not listed.",
                hidden, rule.id(), hidden == 1 ? "" : "s"))
                .with("hiddenFindings", hidden)
                .with("totalFindings", found.size())
                .suggest("These are the same defect repeated. Fix the pattern rather than the "
                        + "instances, or raise Config.maxFindingsPerRule to see them all."));
        return kept;
    }

    /**
     * Why each rule did or did not say anything.
     *
     * <p>The corpus kept teaching the same lesson: a rule that reports nothing and a rule
     * that never ran look identical from the outside, and only the second is a bug. The
     * audit path cannot tell them apart because it only ever returns findings. This does,
     * and {@link #run} is built on it, so what the census measures is the same loop the
     * audit executes rather than a second copy of it that could drift.
     */
    public enum Outcome {
        /** Ran and had something to say. */
        FIRED,
        /** Ran and found nothing. Evidence of absence, which is what a clean page looks like. */
        SILENT,
        /** Switched off by configuration. */
        SKIPPED_DISABLED,
        /** Needs the rendered image and this capture has none. */
        SKIPPED_NO_PIXELS,
        /** Reads literal words and this capture was redacted. */
        SKIPPED_REDACTED,
        /** Threw. Reported as a finding so one broken check cannot hide the others. */
        THREW
    }

    /** One rule's turn on one analysis. */
    public record Execution(Rule rule, Outcome outcome, List<Finding> findings, String error) {

        public boolean ran() {
            return outcome == Outcome.FIRED || outcome == Outcome.SILENT || outcome == Outcome.THREW;
        }
    }

    /**
     * Runs every rule and records what happened to each, including the ones that never got
     * their turn.
     */
    public List<Execution> explain(Analysis analysis) {
        // The fold, not the whole-page render: that is what every pixel-reading rule reads,
        // and gating on the larger image switched them all off wherever only the fold was kept.
        boolean havePixels = analysis.foldImage().isPresent();
        boolean redacted = analysis.snapshot().redacted;
        List<Execution> out = new ArrayList<>(rules.size());
        for (Rule rule : rules) {
            if (!analysis.config().runs(rule.id())) {
                out.add(new Execution(rule, Outcome.SKIPPED_DISABLED, List.of(), null));
            } else if (rule.needsPixels() && !havePixels) {
                out.add(new Execution(rule, Outcome.SKIPPED_NO_PIXELS, List.of(), null));
            } else if (rule.needsLiteralText() && redacted) {
                out.add(new Execution(rule, Outcome.SKIPPED_REDACTED, List.of(), null));
            } else {
                out.add(attempt(rule, analysis));
            }
        }
        return out;
    }

    private Execution attempt(Rule rule, Analysis analysis) {
        try {
            List<Finding> found = rule.check(analysis);
            if (found == null || found.isEmpty()) {
                return new Execution(rule, Outcome.SILENT, List.of(), null);
            }
            return new Execution(rule, Outcome.FIRED,
                    cap(rule, found, analysis.config().maxFindingsPerRule), null);
        } catch (RuntimeException e) {
            return new Execution(rule, Outcome.THREW, List.of(new Finding(rule.id(), Severity.INFO,
                    "Rule '" + rule.id() + "' could not run: " + e)), String.valueOf(e));
        }
    }

    /**
     * Runs every enabled rule. A rule that throws is reported as a finding rather than
     * failing the audit -- one broken check must not hide the other twenty.
     */
    public List<Finding> run(Analysis analysis) {
        List<Finding> out = new ArrayList<>();
        for (Execution execution : explain(analysis)) out.addAll(execution.findings());
        out.sort(Comparator.comparing((Finding f) -> f.severity).reversed()
                .thenComparing(f -> f.ruleId)
                .thenComparing(f -> f.focus == null ? 0.0 : f.focus.y()));
        return out;
    }
}
