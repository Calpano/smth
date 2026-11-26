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
     * Runs every enabled rule. A rule that throws is reported as a finding rather than
     * failing the audit -- one broken check must not hide the other twenty.
     */
    public List<Finding> run(Analysis analysis) {
        List<Finding> out = new ArrayList<>();
        boolean havePixels = analysis.fullPageImage().isPresent();
        boolean redacted = analysis.snapshot().redacted;
        for (Rule rule : rules) {
            if (!analysis.config().runs(rule.id())) continue;
            if (rule.needsPixels() && !havePixels) continue;
            if (rule.needsLiteralText() && redacted) continue;
            try {
                List<Finding> found = rule.check(analysis);
                if (found != null) out.addAll(cap(rule, found, analysis.config().maxFindingsPerRule));
            } catch (RuntimeException e) {
                out.add(new Finding(rule.id(), Severity.INFO,
                        "Rule '" + rule.id() + "' could not run: " + e));
            }
        }
        out.sort(Comparator.comparing((Finding f) -> f.severity).reversed()
                .thenComparing(f -> f.ruleId)
                .thenComparing(f -> f.focus == null ? 0.0 : f.focus.y()));
        return out;
    }
}
