package com.calpano.uxlens.cli;

import com.calpano.uxlens.Auditor;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.browser.PageProbe;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.corpus.Calibration;
import com.calpano.uxlens.corpus.Census;
import com.calpano.uxlens.corpus.Fuzz;
import com.calpano.uxlens.corpus.CorpusStore;
import com.calpano.uxlens.corpus.Labels;
import com.calpano.uxlens.corpus.Synthetic;
import com.calpano.uxlens.interactive.LatencyAudit;
import com.calpano.uxlens.interactive.StateAudit;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;
import com.calpano.uxlens.perception.VisualDiff;
import com.calpano.uxlens.report.AgentBriefing;
import com.calpano.uxlens.report.HtmlReport;
import com.calpano.uxlens.report.JsonReport;
import com.calpano.uxlens.rules.Reference;
import com.calpano.uxlens.rules.Rule;
import com.calpano.uxlens.rules.StandardsCoverage;
import com.calpano.uxlens.rules.RuleSet;

import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command line entry point.
 *
 * <pre>
 * uxlens audit URL  [--viewport desktop,mobile] [--out DIR] [--fail-on medium]
 *                   [--aaa] [--only id,...] [--skip id,...] [--grid 8] [--quiet]
 * uxlens focus URL  [--viewport desktop]
 * uxlens diff A B   [--viewport desktop] [--out DIR]
 * uxlens rules
 * </pre>
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IllegalArgumentException e) {
            System.err.println("uxlens: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("uxlens: " + e);
            System.exit(3);
        }
    }

    static int run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            usage();
            return args.length == 0 ? 2 : 0;
        }
        String command = args[0];
        List<String> rest = List.of(args).subList(1, args.length);
        return switch (command) {
            case "audit" -> audit(rest);
            case "states", "focus" -> states(rest);
            case "latency" -> latency(rest);
            case "diff" -> diff(rest);
            case "corpus" -> corpus(rest);
            case "rules" -> rules();
            case "standards" -> standards();
            default -> throw new IllegalArgumentException("Unknown command '" + command
                    + "'. Try: audit, focus, diff, rules.");
        };
    }

    private static int audit(List<String> args) throws Exception {
        Args a = Args.parse(args, 1);
        String url = a.positional.get(0);
        Path out = a.path("out", null);
        Severity failOn = a.severity("fail-on", Severity.HIGH);
        boolean quiet = a.flag("quiet");

        Config config = Config.defaults();
        if (a.flag("aaa")) config.aaa();
        a.list("only").forEach(config.onlyRules::add);
        a.list("skip").forEach(config.disabledRules::add);

        Report report;
        try (Auditor auditor = Auditor.create()) {
            auditor.config(config).viewports(a.viewports()).colorSchemes(a.colorSchemes());
            if (out != null) auditor.artifactDir(out);
            auditor.gridStep(a.number("grid", 8));
            report = auditor.audit(url);
        }

        if (out != null) {
            JsonReport.write(report, out.resolve("report.json"));
            HtmlReport.write(report, out);
            AgentBriefing.write(report, out.resolve("briefing.md"));
            System.out.println("Wrote " + out.resolve("report.html").toAbsolutePath());
            System.out.println("Wrote " + out.resolve("briefing.md").toAbsolutePath() + "  (for an LLM agent)");
        }
        if (!quiet) printFindings(report);
        printSchemeParity(report);

        List<Finding> blocking = report.findings(failOn);
        System.out.printf(Locale.ROOT, "%n%s: %s%n", blocking.isEmpty() ? "PASS" : "FAIL",
                summary(report, failOn));
        return blocking.isEmpty() ? 0 : 1;
    }

    private static int states(List<String> args) {
        Args a = Args.parse(args, 1);
        String url = a.positional.get(0);
        Viewport viewport = a.viewports().get(0);

        java.util.Set<StateAudit.State> states = a.list("states").isEmpty()
                ? java.util.EnumSet.allOf(StateAudit.State.class)
                : a.list("states").stream()
                        .map(s -> StateAudit.State.valueOf(s.trim().toUpperCase(Locale.ROOT)))
                        .collect(java.util.stream.Collectors.toCollection(
                                () -> java.util.EnumSet.noneOf(StateAudit.State.class)));

        List<Finding> findings;
        try (PageProbe probe = PageProbe.launch()) {
            findings = StateAudit.run(probe, url, viewport, states);
        }
        if (findings.isEmpty()) {
            System.out.println("PASS: every control reacts visibly when focused and hovered.");
            return 0;
        }
        findings.forEach(f -> System.out.println(format(f)));
        System.out.printf("%nFAIL: %d control(s) without a usable focus or hover state.%n",
                findings.size());
        return 1;
    }

    /**
     * How long the page takes to answer a click: the one measurement here that needs a
     * stopwatch rather than a screenshot.
     */
    private static int latency(List<String> args) {
        Args a = Args.parse(args, 1);
        String url = a.positional.get(0);
        Viewport viewport = a.viewports().get(0);
        Config config = Config.defaults();
        if (a.number("doherty", 0) > 0) config.dohertyThresholdMs = a.number("doherty", 400);

        LatencyAudit.Result result;
        try (PageProbe probe = PageProbe.launch()) {
            result = LatencyAudit.audit(probe, url, viewport, config);
        }
        result.findings().forEach(f -> System.out.println(format(f)));

        // Always, before the verdict. A pass with nothing behind it reads exactly like a
        // pass with evidence, and this project has been bitten by that difference all week.
        LatencyAudit.Summary s = result.summary();
        System.out.printf("%nWHAT WAS MEASURED%n");
        System.out.printf("   %d actionable control(s) on the page, %d safe to click%n",
                s.actionableOnPage(), s.safeToClick());
        System.out.printf("   %d clicked, %d timed, %d changed nothing, %d navigated away, "
                + "%d unreachable%n", s.clicked(), s.measured(), s.silent(), s.navigated(),
                s.unreachable());
        System.out.printf("   the untouched page mutated %.1f time(s) a second%n",
                s.mutationsPerSecond());
        if (s.isConclusive()) {
            System.out.printf("   response: median %.0fms, slowest %.0fms, against a %.0fms "
                            + "threshold%n", s.median(), s.slowest(), config.dohertyThresholdMs);
        }
        System.out.println("   Controls with destructive wording, a form submit or a link that "
                + "leaves\n   the page are never clicked, so this covers less of the page "
                + "than it could.");

        if (!s.isConclusive()) {
            System.out.printf("%nINCONCLUSIVE: nothing could be timed -- %s.%n",
                    s.whyInconclusive().orElse("no reason recorded"));
            return 0;
        }
        long slow = result.findings().stream()
                .filter(f -> f.ruleId.equals(LatencyAudit.SLOW_RULE_ID)).count();
        long dead = result.findings().stream()
                .filter(f -> f.ruleId.equals(LatencyAudit.SILENT_RULE_ID)).count();
        System.out.printf("%n%s: %d of %d timed response(s) slower than %.0fms",
                slow == 0 && dead == 0 ? "PASS" : "FAIL", slow, s.measured(),
                config.dohertyThresholdMs);
        System.out.println(dead == 0 ? "." : String.format(
                ", and %d control(s) that changed nothing at all.", dead));
        return slow == 0 && dead == 0 ? 0 : 1;
    }

    private static int diff(List<String> args) throws Exception {
        Args a = Args.parse(args, 2);
        Viewport viewport = a.viewports().get(0);
        Path out = a.path("out", null);
        VisualDiff.Result result;
        try (PageProbe probe = PageProbe.launch()) {
            var left = probe.capture(a.positional.get(0), viewport).fullPageImage();
            var right = probe.capture(a.positional.get(1), viewport).fullPageImage();
            if (left.getHeight() != right.getHeight()) {
                System.out.printf("Page heights differ: %dpx vs %dpx -- content height changed.%n",
                        left.getHeight(), right.getHeight());
                return 1;
            }
            result = VisualDiff.compare(left, right);
        }
        System.out.printf(Locale.ROOT,
                "changed pixels %.4f%%   ssim %.4f   layout similarity %.4f%n",
                result.changedPixelRatio() * 100, result.ssim(), result.layoutSimilarity());
        System.out.println(result.looksIdentical() ? "The two renders look identical."
                : result.layoutStable() ? "Detail changed; the layout blocks did not move."
                : "The layout moved.");
        if (out != null) {
            java.nio.file.Files.createDirectories(out);
            Path file = out.resolve("diff.png");
            ImageIO.write(result.diffImage(), "png", file.toFile());
            System.out.println("Wrote " + file.toAbsolutePath());
        }
        return result.looksIdentical() ? 0 : 1;
    }

    /**
     * The calibration corpus: capture, run, score.
     *
     * <p>Precision is the number that decides whether a rule earns its place, and it cannot
     * be argued into existence -- somebody has to look at real findings on real pages and
     * say which were right.
     */
    private static int corpus(List<String> args) throws Exception {
        Args a = Args.parse(args, 1);
        String action = a.positional.get(0);
        Path dir = a.path("dir", Path.of("corpus"));

        return switch (action) {
            case "capture" -> {
                boolean withImages = a.flag("with-images");
                boolean redact = a.flag("redact");
                boolean skipExisting = a.flag("skip-existing");
                CorpusStore.CaptureReport report;
                try (PageProbe probe = PageProbe.launch()) {
                    // Corpus captures should be patient: an application shell that is still
                    // mounting when it is measured pins a page nobody ever sees.
                    report = CorpusStore.capture(probe.settleMs(2500), dir, withImages, redact,
                            skipExisting);
                }
                System.out.printf("Pinned %d capture(s) under %s (%s%s).%n", report.written(),
                        dir.toAbsolutePath(),
                        withImages ? "with screenshots" : "measurements only",
                        redact ? ", redacted" : "");
                if (report.skipped() > 0) {
                    System.out.printf("Skipped %d already pinned.%n", report.skipped());
                }
                for (CorpusStore.Failure failure : report.failures()) {
                    System.out.printf("FAILED %s/%s: %s%n", failure.siteId(),
                            failure.captureName(), failure.reason());
                }
                if (!report.isComplete()) {
                    System.out.println("Re-run with --skip-existing to fill the gaps.");
                }
                if (redact) {
                    System.out.println("Words were stripped; geometry, style, text length and "
                            + "line boxes are intact. Rules matching literal words are skipped "
                            + "on these captures.");
                }
                yield 0;
            }
            case "run" -> {
                Calibration.Result result = runCorpus(dir, a);
                System.out.printf("%d finding(s) over %d capture(s); %d labelled, %d not.%n%n",
                        result.findings().size(), CorpusStore.load(dir).size(),
                        result.labelled(), result.unlabelled());
                for (Calibration.Scored s : result.findings()) {
                    System.out.printf("%-14s %-7s %s%n     %s%n",
                            s.verdict() == null ? "UNLABELLED" : s.verdict(),
                            s.finding().severity, s.key(),
                            s.finding().message.length() > 120
                                    ? s.finding().message.substring(0, 120) + "..."
                                    : s.finding().message);
                }
                System.out.println("\nLabel a finding by adding its key to "
                        + dir.resolve("labels.json") + " with TRUE_POSITIVE or FALSE_POSITIVE.");
                yield 0;
            }
            case "score" -> {
                Calibration.Result result = runCorpus(dir, a);
                double minimum = a.number("min-precision", 0);
                System.out.printf("%-30s %6s %6s %6s %7s %10s%n",
                        "rule", "fired", "right", "wrong", "unsure", "precision");
                for (Calibration.RuleScore s : result.scores()) {
                    System.out.printf("%-30s %6d %6d %6d %7d %10s%n", s.ruleId(), s.fired(),
                            s.truePositives(), s.falsePositives(), s.unsure(),
                            s.isJudged() ? String.format(Locale.ROOT, "%.0f%%", s.precision() * 100)
                                    : "-");
                }
                System.out.printf("%n%d of %d findings judged.%n",
                        result.labelled(), result.findings().size());
                if (!result.orphanLabels().isEmpty()) {
                    System.out.printf("%n%d label(s) point at findings that no longer exist. "
                            + "Somebody judged these and the judgement has been lost:%n",
                            result.orphanLabels().size());
                    result.orphanLabels().forEach(key -> System.out.printf("   %s%n", key));
                }

                List<Calibration.RuleScore> failing = result.below(minimum);
                if (failing.isEmpty()) yield 0;
                System.out.printf("%nBelow the %.0f%% precision floor:%n", minimum * 100);
                failing.forEach(s -> System.out.printf("   %-30s %.0f%%%n",
                        s.ruleId(), s.precision() * 100));
                yield 1;
            }
            case "census" -> census(dir, a);
            case "fuzz" -> fuzz(dir, a);
            case "synth" -> synth(a);
            default -> throw new IllegalArgumentException(
                    "Unknown corpus action '" + action
                            + "'. Try: capture, run, score, census, fuzz, synth.");
        };
    }

    /**
     * Whether every rule and every measurement actually works -- which precision cannot
     * answer, because a rule that never ran and a rule with nothing to report are the same
     * silence from outside.
     */
    private static int census(Path dir, Args a) {
        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        if (corpus.isEmpty()) {
            throw new IllegalArgumentException("No captures under " + dir.toAbsolutePath()
                    + ". Run 'uxlens corpus capture' first.");
        }
        Config config = Config.defaults();
        a.list("skip").forEach(config.disabledRules::add);
        Path scratch = a.path("out", Path.of("target", "census"));
        java.util.Set<String> sparse = CorpusStore.sites(dir).stream()
                .filter(CorpusStore.Site::isSparse).map(CorpusStore.Site::id)
                .collect(java.util.stream.Collectors.toSet());
        Census.Result result = Census.run(corpus, config, scratch, sparse);

        System.out.printf("%d capture(s): %d with pixels, %d redacted.%n%n", result.captures(),
                result.capturesWithPixels(), result.capturesRedacted());

        boolean verbose = a.flag("verbose");
        System.out.println("CAPTURES  (is each pinned page the page it claims to be?)\n");
        System.out.printf("%-28s %6s %6s %6s %6s %-7s %s%n", "capture", "elems", "links",
                "h1-h6", "inter", "pixels", "looks like");
        for (Census.CaptureRow c : result.captureRows()) {
            if (!verbose && !c.isSuspect()) continue;
            System.out.printf("%-28s %6d %6d %6d %6d %-7s %s%n", c.key(), c.elements(), c.links(),
                    c.headings(), c.interactive(), c.hasPixels() ? "yes" : "-",
                    c.flags().isEmpty() ? "a page" : String.join(", ", c.flags()));
            if (c.isSuspect()) System.out.printf("%-28s   %s%n", "", c.url());
        }
        if (!verbose) {
            System.out.printf("%n   (%d plausible capture(s) not listed; --verbose shows all)%n",
                    result.captures() - result.suspectCaptures().size());
        }
        System.out.printf("   %d suspect of %d.%n", result.suspectCaptures().size(),
                result.captures());

        System.out.println("\nRULES  (ran = got its turn; silent = ran and found nothing)\n");
        System.out.printf("%-30s %-18s %5s %6s %6s %9s %7s%n", "rule", "verdict", "ran",
                "fired", "silent", "skipped", "found");
        for (Census.RuleRow r : result.rules()) {
            if (!verbose && r.verdict() == Census.Verdict.EXERCISED) continue;
            System.out.printf("%-30s %-18s %5d %6d %6d %9d %7d%n", r.ruleId(), r.verdict(),
                    r.ran(), r.firedOn(), r.silentOn(), r.skipped(), r.findings());
            if (r.error() != null) System.out.printf("%-30s   threw: %s%n", "", r.error());
        }
        if (!verbose) {
            System.out.printf("%n   (%d exercised rule(s) not listed; --verbose shows all)%n",
                    result.rulesWith(Census.Verdict.EXERCISED).size());
        }
        for (Census.Verdict v : Census.Verdict.values()) {
            System.out.printf("   %-18s %d%n", v, result.rulesWith(v).size());
        }

        System.out.println("\nMEASUREMENTS  (one reading per capture)\n");
        System.out.printf("%-12s %-28s %-7s %5s %5s %5s %12s %12s%n", "group", "measure",
                "health", "read", "n/a", "void", "min", "max");
        String group = "";
        for (Census.MeasureRow m : result.measures()) {
            if (!verbose && m.health() == Census.Health.OK) continue;
            System.out.printf("%-12s %-28s %-7s %5d %5d %5d %12s %12s%n",
                    m.measure().group().equals(group) ? "" : (group = m.measure().group()),
                    m.measure().name(), m.health(), m.read(), m.notApplicable(), m.noValue(),
                    number(m.min()), number(m.max()));
            if (m.error() != null) System.out.printf("%-12s   threw: %s%n", "", m.error());
            if (!m.offenders().isEmpty()) {
                System.out.printf("%-12s   on: %s%n", "", String.join(", ", m.offenders()));
            }
        }
        if (!verbose) {
            System.out.printf("%n   (%d healthy measurement(s) not listed; --verbose shows all)%n",
                    result.measuresWith(Census.Health.OK).size());
        }
        for (Census.Health h : Census.Health.values()) {
            System.out.printf("   %-8s %d%n", h, result.measuresWith(h).size());
        }

        System.out.println("\nWHAT A PINNED CAPTURE CANNOT EXERCISE\n");
        result.notReachable().forEach(line -> System.out.printf("   - %s%n", wrap(line, 86, 5)));

        System.out.printf("%n%s%n", result.isHealthy()
                ? "Every rule ran and every measurement produced live values."
                : "Something is not working; the rows above say which.");
        return result.isHealthy() ? 0 : 1;
    }

    /**
     * Deform the input on purpose and see whether the measurements respond as they must.
     *
     * <p>The census says a number is alive. This says it is a measurement of the page
     * rather than a coincidence that happens to vary.
     */
    private static int fuzz(Path dir, Args a) {
        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        if (corpus.isEmpty()) {
            throw new IllegalArgumentException("No captures under " + dir.toAbsolutePath()
                    + ". Run 'uxlens corpus capture' first.");
        }
        Config config = Config.defaults();
        a.list("skip").forEach(config.disabledRules::add);
        long seed = (long) a.number("seed", 20_251_127);
        boolean verbose = a.flag("verbose");

        List<String> only = a.list("only");
        List<CorpusStore.Entry> subjects = only.isEmpty()
                ? corpus
                : corpus.stream().filter(e -> only.contains(e.siteId())).toList();
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("No capture matched --only=" + String.join(",", only));
        }

        int brokenMutations = 0;
        int brokenClaims = 0;
        int claims = 0;
        int skippedClaims = 0;
        // A claim nobody could ask is as untested as a rule that never ran.
        java.util.Set<String> asked = new java.util.LinkedHashSet<>();
        java.util.Set<String> stated = new java.util.LinkedHashSet<>();
        for (Fuzz.Result result : Fuzz.runAll(subjects, config, seed)) {
            List<Fuzz.MutationResult> broken = result.broken();
            claims += result.claimCount();
            skippedClaims += result.skippedClaims().size();
            result.mutations().forEach(m -> m.claims().forEach(c -> {
                String id = m.mutation().name() + "/" + c.claim().measure();
                stated.add(id);
                if (!c.skipped()) asked.add(id);
            }));
            brokenMutations += broken.size();
            brokenClaims += result.brokenClaims().size();
            if (broken.isEmpty() && !verbose) continue;

            System.out.printf("%n%s%n", result.baseline());
            for (Fuzz.MutationResult m : result.mutations()) {
                if (!verbose && m.isClean()) continue;
                System.out.printf("   %-26s %s%n", m.mutation().name(),
                        m.isClean() ? "ok" : m.timedOut() ? "HUNG" : m.survived() ? "" : "THREW");
                System.out.printf("   %-26s   %s%n", "", wrap(m.mutation().what(), 84, 31));
                if (m.error() != null) {
                    System.out.printf("   %-26s   threw: %s%n", "", m.error());
                }
                if (m.nonFiniteMeasures() > 0) {
                    System.out.printf("   %-26s   not finite: %s%n", "",
                            String.join(", ", m.nonFiniteNames()));
                }
                for (Fuzz.ClaimResult c : m.claims()) {
                    if (!verbose && (c.held() || c.skipped())) continue;
                    System.out.printf("   %-26s   %-6s %-10s %-22s %12s -> %s%n", "",
                            c.skipped() ? "n/a" : c.held() ? "held" : "BROKEN",
                            c.claim().relation(),
                            c.claim().measure(), number(c.before()), number(c.after()));
                    if (!c.held()) {
                        System.out.printf("   %-26s     claim: %s%n", "",
                                wrap(c.claim().why(), 80, 36));
                    }
                }
            }
        }

        int neverAsked = (int) stated.stream().filter(id -> !asked.contains(id)).count();
        System.out.printf("%n%d capture(s) x %d mutation(s); %d claim(s) stated, %d asked, "
                        + "%d not applicable.%n", subjects.size(), Fuzz.mutations().size(),
                claims, claims - skippedClaims, skippedClaims);
        System.out.printf("%d mutation(s) not clean, %d claim(s) broken.%n",
                brokenMutations, brokenClaims);
        if (neverAsked > 0) {
            System.out.printf("%d claim(s) were skipped on every capture, so nothing here "
                    + "tests them.%n", neverAsked);
        }
        if (brokenMutations == 0) {
            System.out.println("Nothing throws on input no browser would produce, no "
                    + "measurement comes back as NaN, and every claim about what the "
                    + "measurements mean held.");
        }
        return brokenMutations == 0 ? 0 : 1;
    }

    /**
     * Two pages per rule, generated from its discrimination contract: one it must fire on,
     * one it must stay silent on. Then: did each rule do its job, and what else did it trip?
     */
    private static int synth(Args a) throws Exception {
        Path dir = a.path("dir", Path.of("target", "synth"));
        boolean verbose = a.flag("verbose");

        if (!a.flag("reuse")) {
            int written = Synthetic.write(dir);
            try (PageProbe probe = PageProbe.launch()) {
                CorpusStore.CaptureReport report = CorpusStore.capture(probe, dir, true, false,
                        false);
                System.out.printf("Generated %d page(s) and captured %d under %s.%n", written,
                        report.written(), dir.toAbsolutePath());
                for (CorpusStore.Failure failure : report.failures()) {
                    System.out.printf("FAILED %s/%s: %s%n", failure.siteId(),
                            failure.captureName(), failure.reason());
                }
            }
        }

        Config config = Config.defaults();
        a.list("skip").forEach(config.disabledRules::add);
        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        Synthetic.Isolation isolation = Synthetic.isolation(corpus, config);

        List<String> noContract = Synthetic.withoutContract();
        if (!noContract.isEmpty()) {
            System.out.printf("%n%d rule(s) have no contract and so no fixture: %s%n",
                    noContract.size(), String.join(", ", noContract));
        }
        if (!isolation.rulesWithoutFixture().isEmpty()) {
            System.out.printf("%n%d rule(s) have a contract but no capture: %s%n",
                    isolation.rulesWithoutFixture().size(),
                    String.join(", ", isolation.rulesWithoutFixture()));
        }

        System.out.println("\nDID EACH FIXTURE DO ITS JOB?\n");
        List<Synthetic.CrossTalk> broken = isolation.broken();
        if (broken.isEmpty()) {
            System.out.printf("   Yes. All %d violating page(s) tripped their rule and all %d "
                            + "conforming page(s) did not.%n", isolation.violating().size(),
                    isolation.conforming().size());
        } else {
            for (Synthetic.CrossTalk f : broken) {
                System.out.printf("   %-30s %s%n", f.ruleId(), f.conformingPage()
                        ? "fired " + f.ownFindings() + "x on the page it must stay silent on"
                        : "did not fire on the page written to make it fire");
            }
        }

        System.out.println("\nCROSS-TALK  (what else each fixture tripped)\n");
        System.out.printf("   %-30s %5s %6s %7s  %s%n", "fixture", "own", "others",
                "finds", "rules");
        for (Synthetic.CrossTalk f : isolation.violating()) {
            if (!verbose && f.others().isEmpty()) continue;
            System.out.printf("   %-30s %5d %6d %7d  %s%n", f.ruleId(), f.ownFindings(),
                    f.otherRuleCount(), f.otherFindingCount(),
                    wrap(String.join(", ", f.others().keySet()), 78, 56));
        }
        long cleanFixtures = isolation.violating().stream()
                .filter(Synthetic.CrossTalk::isClean).count();
        System.out.printf("%n   %d of %d violating fixture(s) trip nothing but their own rule.%n",
                cleanFixtures, isolation.violating().size());

        System.out.println("\nWHICH RULES FIRE ON OTHER PEOPLE'S FIXTURES\n");
        for (Map.Entry<String, Integer> e : isolation.noisiest()) {
            System.out.printf("   %-30s %d finding(s)%n", e.getKey(), e.getValue());
        }

        List<Synthetic.CrossTalk> dirty = isolation.dirtyConformingPages();
        System.out.println("\nCONFORMING PAGES THAT TRIPPED SOMETHING\n");
        System.out.println("   These are the cleanest pages this project has: a minimal page "
                + "with\n   its one deliberate defect removed. Anything firing here is a "
                + "false positive\n   candidate.\n");
        if (dirty.isEmpty()) {
            System.out.printf("   None. All %d are silent.%n", isolation.conforming().size());
        } else {
            for (Synthetic.CrossTalk f : dirty) {
                System.out.printf("   %-30s %s%n", f.ruleId() + Synthetic.CONFORMS_SUFFIX,
                        f.others().entrySet().stream()
                                .map(e -> e.getKey() + " x" + e.getValue()).toList());
            }
            System.out.printf("%n   %d of %d conforming page(s) are not clean.%n", dirty.size(),
                    isolation.conforming().size());
        }

        if (a.flag("label")) {
            Labels labels = Synthetic.labels(corpus, config);
            labels.save(dir);
            System.out.printf("%nLabelled %d finding(s) as true positives by construction in "
                    + "%s.%n", labels.size(), dir.resolve("labels.json"));
        }

        return broken.isEmpty() && dirty.isEmpty() ? 0 : 1;
    }

    private static String number(double v) {
        if (Double.isNaN(v)) return "-";
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static Calibration.Result runCorpus(Path dir, Args a) {
        List<CorpusStore.Entry> corpus = CorpusStore.load(dir);
        if (corpus.isEmpty()) {
            throw new IllegalArgumentException("No captures under " + dir.toAbsolutePath()
                    + ". Run 'uxlens corpus capture' first.");
        }
        Config config = Config.defaults();
        a.list("skip").forEach(config.disabledRules::add);
        return Calibration.run(corpus, config, Labels.load(dir), a.severity("min-severity", Severity.INFO));
    }

    private static int rules() {
        for (Rule rule : RuleSet.standard().rules()) {
            System.out.printf("%-28s %s%s%n", rule.id(), rule.title(),
                    rule.needsPixels() ? "  [needs pixels]" : "");
            System.out.printf("%-28s %s%n", "", wrap(rule.rationale(), 88, 29));
            for (Reference reference : rule.references()) {
                System.out.printf("%-28s - %s%n", "",
                        wrap(reference.toString(), 88, 31));
            }
            System.out.println();
        }
        return 0;
    }

    /** What of the body of knowledge is covered, and -- more usefully -- what is not. */
    private static int standards() {
        System.out.println("COVERED\n");
        for (StandardsCoverage.SourceCoverage c : StandardsCoverage.coverage()) {
            System.out.printf("%-46s %d rule%s%n", c.source().title(), c.ruleCount(),
                    c.ruleCount() == 1 ? "" : "s");
            System.out.printf("%-46s %s%n%n", "", wrap(String.join(", ", c.ruleIds()), 88, 47));
        }

        List<String> house = StandardsCoverage.houseOnly();
        System.out.printf("RESTING ON NOTHING BUT THIS PROJECT'S JUDGEMENT (%d)%n%n", house.size());
        System.out.printf("   %s%n%n", wrap(String.join(", ", house), 88, 3));
        System.out.println("   These are the thresholds to argue with first.\n");

        System.out.println("THE NAMED HEURISTICS, ITEM BY ITEM\n");
        String currentSet = "";
        for (StandardsCoverage.Heuristic h : StandardsCoverage.heuristics()) {
            if (!h.set().equals(currentSet)) {
                currentSet = h.set();
                System.out.printf("%n  %s%n", currentSet);
            }
            System.out.printf("   %-14s %-44s %s%n", h.status(), h.item(),
                    h.ruleIds().isEmpty() ? "" : h.ruleIds().size() + " rule(s)");
        }
        java.util.Map<StandardsCoverage.Status, Long> tally = StandardsCoverage.heuristics().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        StandardsCoverage.Heuristic::status, java.util.stream.Collectors.counting()));
        System.out.printf("%n   %d covered, %d partial, %d out of reach.%n",
                tally.getOrDefault(StandardsCoverage.Status.COVERED, 0L),
                tally.getOrDefault(StandardsCoverage.Status.PARTIAL, 0L),
                tally.getOrDefault(StandardsCoverage.Status.OUT_OF_REACH, 0L));
        System.out.println("   The presentation principles are largely covered and the "
                + "interaction\n   principles largely are not, which is what measuring one "
                + "rendered moment buys.\n");

        System.out.println("NOT CHECKED, DELIBERATELY\n");
        for (StandardsCoverage.OutOfScope o : StandardsCoverage.outOfScope()) {
            System.out.printf("%s  [%s]%n", o.area(), o.source().title());
            System.out.printf("   why not: %s%n", wrap(o.why(), 86, 12));
            System.out.printf("   instead: %s%n%n", wrap(o.insteadUse(), 86, 12));
        }
        return 0;
    }

    /** Where dark mode and light mode disagree, which is where dark mode was bolted on. */
    private static void printSchemeParity(Report report) {
        for (Report.SchemeParity parity : report.schemeParity()) {
            if (parity.isClean()) {
                System.out.printf("%nColour schemes agree at %s.%n", parity.viewport);
                continue;
            }
            System.out.printf("%nColour scheme parity at %s:%n", parity.viewport);
            if (!parity.onlyInDark.isEmpty()) {
                System.out.println("   only in dark:  " + String.join(", ", parity.onlyInDark));
            }
            if (!parity.onlyInLight.isEmpty()) {
                System.out.println("   only in light: " + String.join(", ", parity.onlyInLight));
            }
        }
    }

    private static void printFindings(Report report) {
        for (Report.ViewportResult v : report.viewports) {
            System.out.printf("%n=== %s (%dx%d) -- %d elements, %d finding(s) ===%n",
                    v.viewportName, v.width, v.height, v.elementCount, v.findings.size());
            if (!v.profile.isEmpty()) {
                System.out.print("    ");
                v.profile.forEach((k, value) -> System.out.printf("%s=%s  ", k, value));
                System.out.println();
            }
            v.findings.forEach(f -> System.out.println(format(f)));
        }
    }

    private static String format(Finding f) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n[%-6s] %-26s %s", f.severity, f.ruleId, f.message));
        if (!f.selectors.isEmpty()) sb.append("\n           at: ").append(String.join(", ", f.selectors));
        if (f.suggestion != null) sb.append("\n           fix: ").append(wrap(f.suggestion, 88, 16));
        return sb.toString();
    }

    private static String summary(Report report, Severity failOn) {
        Map<Severity, Long> counts = report.countsBySeverity();
        List<String> parts = new ArrayList<>();
        for (Severity s : List.of(Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO)) {
            long n = counts.getOrDefault(s, 0L);
            if (n > 0) parts.add(n + " " + s.name().toLowerCase(Locale.ROOT));
        }
        return (parts.isEmpty() ? "nothing found" : String.join(", ", parts))
                + " (failing at " + failOn.name().toLowerCase(Locale.ROOT) + " and above)";
    }

    private static String wrap(String text, int width, int indent) {
        StringBuilder sb = new StringBuilder();
        int lineLength = indent;
        for (String word : text.split(" ")) {
            if (lineLength + word.length() > width) {
                sb.append('\n').append(" ".repeat(indent));
                lineLength = indent;
            }
            sb.append(word).append(' ');
            lineLength += word.length() + 1;
        }
        return sb.toString().stripTrailing();
    }

    private static void usage() {
        System.out.println("""
                uxlens -- automated visual and UX review of a rendered web page

                  uxlens audit URL [options]     measure, check and report
                  uxlens states URL [options]    check focus and hover states (drives the page)
                  uxlens latency URL [options]   time the page's answer to a click (drives the page)
                  uxlens diff URL_A URL_B [opts] compare two renders perceptually
                  uxlens corpus ACTION [options] capture / run / score / census / fuzz the corpus
                  uxlens corpus synth [options]  two pages per rule, from its contract
                  uxlens rules                   list every check with its rationale and sources
                  uxlens standards               what of the UX body of knowledge is and is not covered

                Options
                  --viewport desktop,mobile  presets (desktop laptop tablet mobile wide) or 1440x900[@2x]
                  --out DIR                  write screenshots, overlays, report.html, report.json, briefing.md
                  --fail-on high|medium|low  exit 1 at this severity or above (default high)
                  --aaa                      hold text to WCAG AAA instead of AA
                  --only id,id               run only these rules
                  --skip id,id               skip these rules
                  --scheme light,dark        audit each viewport in these colour schemes
                  --grid N                   grid overlay spacing in px (default 8)
                  --states focus,hover       which states to check (default both)
                  --quiet                    write artifacts without printing findings

                Corpus options
                  --dir DIR                  corpus directory (default ./corpus)
                  --with-images              also pin screenshots, so pixel rules can be scored
                  --redact                   strip the words from captures of pages you do not own
                  --skip-existing            only capture what is not pinned yet
                  --min-precision 0.8        'score' exits 1 if a judged rule falls below this
                  --min-severity low         ignore findings below this severity

                Exit codes: 0 clean, 1 findings at or above --fail-on, 2 bad usage, 3 error.""");
    }

    /** Tiny argument parser: {@code --key value}, {@code --flag}, and positionals. */
    private record Args(List<String> positional, Map<String, String> options) {

        static Args parse(List<String> args, int requiredPositional) {
            List<String> positional = new ArrayList<>();
            Map<String, String> options = new LinkedHashMap<>();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.startsWith("--")) {
                    String key = arg.substring(2);
                    int eq = key.indexOf('=');
                    if (eq > 0) {
                        options.put(key.substring(0, eq), key.substring(eq + 1));
                    } else if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        options.put(key, args.get(++i));
                    } else {
                        options.put(key, "true");
                    }
                } else {
                    positional.add(arg);
                }
            }
            if (positional.size() < requiredPositional) {
                throw new IllegalArgumentException("Expected " + requiredPositional
                        + " argument(s), got " + positional.size() + ". Run 'uxlens --help'.");
            }
            return new Args(positional, options);
        }

        boolean flag(String key) { return "true".equals(options.get(key)); }

        List<String> list(String key) {
            String v = options.get(key);
            if (v == null || v.isBlank() || v.equals("true")) return List.of();
            return List.of(v.split(","));
        }

        Path path(String key, Path fallback) {
            String v = options.get(key);
            return v == null || v.equals("true") ? fallback : Path.of(v);
        }

        double number(String key, double fallback) {
            String v = options.get(key);
            if (v == null || v.equals("true")) return fallback;
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " needs a number, got '" + v + "'.");
            }
        }

        Severity severity(String key, Severity fallback) {
            String v = options.get(key);
            if (v == null || v.equals("true")) return fallback;
            try {
                return Severity.valueOf(v.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("--" + key + " must be one of high, medium, low, "
                        + "info; got '" + v + "'.");
            }
        }

        java.util.List<com.microsoft.playwright.options.ColorScheme> colorSchemes() {
            List<String> specs = list("scheme");
            if (specs.isEmpty()) return List.of(com.microsoft.playwright.options.ColorScheme.LIGHT);
            return specs.stream().map(s -> switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "light" -> com.microsoft.playwright.options.ColorScheme.LIGHT;
                case "dark" -> com.microsoft.playwright.options.ColorScheme.DARK;
                default -> throw new IllegalArgumentException(
                        "--scheme must be light or dark; got '" + s + "'.");
            }).toList();
        }

        List<Viewport> viewports() {
            List<String> specs = list("viewport");
            if (specs.isEmpty()) return Viewport.defaults();
            return specs.stream().map(Viewport::parse).toList();
        }
    }
}
