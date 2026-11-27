package com.calpano.uxlens.corpus;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.rules.RuleSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the rules actually do, measured over pinned real pages.
 *
 * <p>A rule's worth is its precision, not its cleverness. A check that fires fifty times
 * and is right twice has negative value: it buries the forty-eight findings that mattered
 * and teaches the team to skim past the whole report. This turns that from an argument
 * into a number, and lets a precision regression fail a build the same way a broken test
 * does.
 */
public final class Calibration {

    private Calibration() { }

    /** One finding, with the capture it came from and the verdict on it. */
    public record Scored(CorpusStore.Entry entry, Finding finding, String key,
                         Labels.Verdict verdict) {

        public boolean isLabelled() { return verdict != null; }
    }

    /** How one rule performed across the corpus. */
    public record RuleScore(String ruleId, int fired, int truePositives, int falsePositives,
                            int unsure, int unlabelled) {

        /** Share of judged findings that were real. NaN when nothing has been judged yet. */
        public double precision() {
            int judged = truePositives + falsePositives;
            return judged == 0 ? Double.NaN : truePositives / (double) judged;
        }

        public boolean isJudged() { return truePositives + falsePositives > 0; }
    }

    /**
     * Everything a corpus run produced.
     *
     * @param orphanLabels judgements whose finding no longer exists -- the page changed, the
     *                     capture was replaced, the rule was retuned. An orphan is
     *                     indistinguishable from a label nobody has written yet, so it
     *                     quietly inflates how much of the corpus looks unjudged while the
     *                     work it represents has already been done and lost.
     */
    public record Result(List<Scored> findings, List<RuleScore> scores,
                         List<String> orphanLabels) {

        public int labelled() { return (int) findings.stream().filter(Scored::isLabelled).count(); }

        public int unlabelled() { return findings.size() - labelled(); }

        /** Rules whose judged precision is below {@code minimum}, worst first. */
        public List<RuleScore> below(double minimum) {
            return scores.stream()
                    .filter(RuleScore::isJudged)
                    .filter(s -> s.precision() < minimum)
                    .sorted(Comparator.comparingDouble(RuleScore::precision))
                    .toList();
        }
    }

    /** Runs every rule over every pinned capture and scores it against the labels. */
    public static Result run(List<CorpusStore.Entry> corpus, Config config, Labels labels) {
        return run(corpus, config, labels, Severity.INFO);
    }

    public static Result run(List<CorpusStore.Entry> corpus, Config config, Labels labels,
                             Severity minimum) {
        RuleSet rules = RuleSet.standard();
        List<Scored> all = new ArrayList<>();

        for (CorpusStore.Entry entry : corpus) {
            Analysis analysis = entry.hasPixels()
                    ? Analysis.ofStoredCapture(entry.snapshot(), entry.viewport(), config,
                            entry.fullPage(), entry.fold())
                    : Analysis.ofSnapshotOnly(entry.snapshot(), entry.viewport(), config);

            for (Finding finding : rules.run(analysis)) {
                if (!finding.severity.atLeast(minimum)) continue;
                String key = Labels.key(entry, finding);
                all.add(new Scored(entry, finding, key,
                        labels.get(key).map(Labels.Label::verdict).orElse(null)));
            }
        }

        Map<String, int[]> tally = new LinkedHashMap<>();   // fired, tp, fp, unsure, unlabelled
        for (Scored s : all) {
            int[] counts = tally.computeIfAbsent(s.finding().ruleId, k -> new int[5]);
            counts[0]++;
            if (s.verdict() == null) {
                counts[4]++;
            } else {
                switch (s.verdict()) {
                    case TRUE_POSITIVE -> counts[1]++;
                    case FALSE_POSITIVE -> counts[2]++;
                    case UNSURE -> counts[3]++;
                }
            }
        }

        java.util.Set<String> live = all.stream().map(Scored::key)
                .collect(java.util.stream.Collectors.toSet());
        List<String> orphans = labels.all().keySet().stream()
                .filter(key -> !live.contains(key))
                .sorted()
                .toList();

        List<RuleScore> scores = tally.entrySet().stream()
                .map(en -> new RuleScore(en.getKey(), en.getValue()[0], en.getValue()[1],
                        en.getValue()[2], en.getValue()[3], en.getValue()[4]))
                .sorted(Comparator.comparingInt(RuleScore::fired).reversed())
                .toList();

        return new Result(List.copyOf(all), scores, orphans);
    }
}
