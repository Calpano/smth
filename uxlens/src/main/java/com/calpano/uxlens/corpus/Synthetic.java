package com.calpano.uxlens.corpus;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.RuleContract;
import com.calpano.uxlens.rules.RuleContracts;
import com.calpano.uxlens.rules.RuleSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A corpus built on purpose: two pages per rule, one that must trip it and one that must not.
 *
 * <p>The real corpus answers whether a rule is any use. It cannot answer whether a rule
 * works, because a page that happens not to violate anything proves nothing, and fifteen
 * of the sixty rules are silent on every real page pinned here. The discrimination
 * contracts have always held the two pages needed; what they were never put through is the
 * rest of the machinery -- the census, the scoring, the fuzzer -- all of which has only
 * ever seen real sites.
 *
 * <p>Nothing here is committed. The real corpus is pinned because real pages move under
 * you; these pages are generated from the contracts and a browser, and are the same every
 * time, so storing them would only be storing a copy. They go to a scratch directory and
 * are made again when wanted.
 *
 * <p>The measurement this adds is cross-talk. A fixture written for {@code leading} that
 * also trips {@code measure} and {@code text-contrast} is a muddy instrument: it cannot be
 * used to calibrate the rule it was written for, and the coupling is usually a surprise to
 * whoever wrote it. Most cross-talk is honest -- a page with one deliberate defect often
 * has incidental ones, and minimal pages are strange pages -- so it is reported and not
 * failed on. What is worth failing on is a conforming page that trips anything at all,
 * because those sixty pages between them are the cleanest pages this project has.
 */
public final class Synthetic {

    /** Site id prefix for the page a rule must fire on. */
    public static final String VIOLATES = "syn-";

    /** Suffix marking the page the rule must stay silent on. */
    public static final String CONFORMS_SUFFIX = "-ok";

    private Synthetic() { }

    /**
     * Writes a page per contract and a {@code sites.json} that points at them.
     *
     * <p>Marked sparse, because a minimal fixture is by construction thin, flat and
     * unscrolling -- the exact shape the capture check rejects as an error page. Marked to
     * keep its pixels, because they cost a few kilobytes on a page that is mostly white and
     * they are the only way the pixel-reading rules get a turn.
     */
    public static int write(Path dir) {
        try {
            Path pages = dir.resolve("pages");
            Files.createDirectories(pages);
            List<CorpusStore.Site> sites = new ArrayList<>();

            for (Rule rule : RuleSet.standard().rules()) {
                RuleContract contract = rule.contract().orElse(null);
                if (contract == null) continue;
                String viewport = contract.viewport().name();

                sites.add(site(pages, rule.id(), VIOLATES + rule.id(), contract.violates(),
                        viewport, "The page " + rule.id() + " must fire on."));
                sites.add(site(pages, rule.id() + CONFORMS_SUFFIX,
                        VIOLATES + rule.id() + CONFORMS_SUFFIX, contract.conforms(), viewport,
                        "The same page with the defect fixed. " + rule.id()
                                + " must stay silent, and so should everything else."));
            }
            CorpusStore.writeSites(dir, sites);
            return sites.size();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the synthetic corpus to " + dir, e);
        }
    }

    private static CorpusStore.Site site(Path pages, String fileStem, String siteId, String body,
                                         String viewport, String note) throws IOException {
        Path file = pages.resolve(fileStem.replace('/', '-') + ".html");
        Files.writeString(file, "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<title>" + siteId + "</title></head>" + body + "</html>",
                StandardCharsets.UTF_8);
        return new CorpusStore.Site(siteId, file.toUri().toString(), List.of(viewport), null,
                false, true, true, true, note);
    }

    /** The rule a synthetic capture was built for, or empty when it is not one of ours. */
    public static java.util.Optional<String> intendedRule(String siteId) {
        if (!siteId.startsWith(VIOLATES)) return java.util.Optional.empty();
        String id = siteId.substring(VIOLATES.length());
        if (id.endsWith(CONFORMS_SUFFIX)) {
            id = id.substring(0, id.length() - CONFORMS_SUFFIX.length());
        }
        return java.util.Optional.of(id);
    }

    public static boolean isConformingPage(String siteId) {
        return siteId.startsWith(VIOLATES) && siteId.endsWith(CONFORMS_SUFFIX);
    }

    /** What one fixture tripped: the rule it was for, and everything else. */
    public record CrossTalk(String ruleId, boolean conformingPage, String captureKey,
                            int ownFindings, Map<String, Integer> others) {

        /** The rule fired on the page written to make it fire. */
        public boolean worked() { return conformingPage ? ownFindings == 0 : ownFindings > 0; }

        public int otherRuleCount() { return others.size(); }

        public int otherFindingCount() {
            return others.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** A fixture that trips only what it was written for. */
        public boolean isClean() { return worked() && others.isEmpty(); }
    }

    public record Isolation(List<CrossTalk> fixtures, List<String> rulesWithoutFixture) {

        public List<CrossTalk> violating() {
            return fixtures.stream().filter(f -> !f.conformingPage()).toList();
        }

        public List<CrossTalk> conforming() {
            return fixtures.stream().filter(CrossTalk::conformingPage).toList();
        }

        /** Fixtures where the rule did not do what the fixture was written to make it do. */
        public List<CrossTalk> broken() {
            return fixtures.stream().filter(f -> !f.worked()).toList();
        }

        /**
         * Which rules fire on somebody else's fixture, loudest first.
         *
         * <p>Read as a list of couplings rather than a list of faults. A rule appearing
         * against many fixtures is either very general or very eager, and the two look the
         * same from here.
         */
        public List<Map.Entry<String, Integer>> noisiest() {
            Map<String, Integer> tally = new LinkedHashMap<>();
            for (CrossTalk f : fixtures) {
                f.others().forEach((rule, count) -> tally.merge(rule, count, Integer::sum));
            }
            return tally.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .toList();
        }

        /** Conforming pages that tripped something. The cleanest pages here, so this should be short. */
        public List<CrossTalk> dirtyConformingPages() {
            return conforming().stream().filter(f -> !f.others().isEmpty() || !f.worked()).toList();
        }
    }

    /** Runs every rule over every synthetic capture and records what each fixture tripped. */
    public static Isolation isolation(List<CorpusStore.Entry> corpus, Config config) {
        RuleSet rules = RuleSet.standard();
        List<CrossTalk> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        for (CorpusStore.Entry entry : corpus) {
            String intended = intendedRule(entry.siteId()).orElse(null);
            if (intended == null) continue;
            seen.add(intended);

            Analysis analysis = entry.hasPixels()
                    ? Analysis.ofStoredCapture(entry.snapshot(), entry.viewport(), config,
                            entry.fullPage(), entry.fold())
                    : Analysis.ofSnapshotOnly(entry.snapshot(), entry.viewport(), config);

            int own = 0;
            Map<String, Integer> others = new LinkedHashMap<>();
            for (Finding finding : rules.run(analysis)) {
                if (finding.ruleId.equals(intended)) {
                    own++;
                } else {
                    others.merge(finding.ruleId, 1, Integer::sum);
                }
            }
            out.add(new CrossTalk(intended, isConformingPage(entry.siteId()), entry.key(),
                    own, sortedByCount(others)));
        }

        List<String> missing = rules.rules().stream()
                .map(Rule::id)
                .filter(id -> !seen.contains(id))
                .toList();
        out.sort(Comparator.comparing(CrossTalk::ruleId).thenComparing(CrossTalk::conformingPage));
        return new Isolation(List.copyOf(out), missing);
    }

    private static Map<String, Integer> sortedByCount(Map<String, Integer> counts) {
        Map<String, Integer> out = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /**
     * Labels every finding a fixture's own rule produced on its violating page as a true
     * positive.
     *
     * <p>Sound, because the page was written to contain that defect and nothing else was
     * claimed about it. Deliberately says nothing about the cross-talk: a second rule firing
     * may be right, and guessing would put made-up verdicts into the same file that holds
     * the real ones.
     */
    public static Labels labels(List<CorpusStore.Entry> corpus, Config config) {
        RuleSet rules = RuleSet.standard();
        Labels labels = Labels.empty();
        for (CorpusStore.Entry entry : corpus) {
            String intended = intendedRule(entry.siteId()).orElse(null);
            if (intended == null || isConformingPage(entry.siteId())) continue;

            Analysis analysis = entry.hasPixels()
                    ? Analysis.ofStoredCapture(entry.snapshot(), entry.viewport(), config,
                            entry.fullPage(), entry.fold())
                    : Analysis.ofSnapshotOnly(entry.snapshot(), entry.viewport(), config);
            for (Finding finding : rules.run(analysis)) {
                if (!finding.ruleId.equals(intended)) continue;
                labels.put(Labels.key(entry, finding), Labels.Verdict.TRUE_POSITIVE,
                        "by construction: this page was written to contain this defect");
            }
        }
        return labels;
    }

    /** Rules with no contract, and therefore no fixture. */
    public static List<String> withoutContract() {
        return RuleSet.standard().rules().stream()
                .filter(r -> RuleContracts.forRule(r.id()).isEmpty())
                .map(Rule::id)
                .toList();
    }

    /** The viewport a rule's fixture is rendered at. */
    public static Viewport viewportFor(String ruleId) {
        return RuleContracts.forRule(ruleId).map(RuleContract::viewport).orElse(Viewport.DESKTOP);
    }
}
