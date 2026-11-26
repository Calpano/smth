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
        return new RuleSet(rules);
    }

    public static RuleSet of(Rule... rules) { return new RuleSet(List.of(rules)); }

    public static RuleSet of(List<Rule> rules) { return new RuleSet(rules); }

    public List<Rule> rules() { return rules; }

    /**
     * Runs every enabled rule. A rule that throws is reported as a finding rather than
     * failing the audit -- one broken check must not hide the other twenty.
     */
    public List<Finding> run(Analysis analysis) {
        List<Finding> out = new ArrayList<>();
        boolean havePixels = analysis.fullPageImage().isPresent();
        for (Rule rule : rules) {
            if (!analysis.config().runs(rule.id())) continue;
            if (rule.needsPixels() && !havePixels) continue;
            try {
                List<Finding> found = rule.check(analysis);
                if (found != null) out.addAll(found);
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
