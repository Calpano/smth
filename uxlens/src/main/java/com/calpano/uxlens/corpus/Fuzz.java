package com.calpano.uxlens.corpus;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Config;
import com.calpano.uxlens.analysis.Consistency;
import com.calpano.uxlens.analysis.PageProfile;
import com.calpano.uxlens.browser.Viewport;
import com.calpano.uxlens.model.Elem;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.PageSnapshot;
import com.calpano.uxlens.rules.LayoutRules;
import com.calpano.uxlens.rules.RuleSet;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Deforming the input on purpose, to find out whether the measurements are measuring.
 *
 * <p>The census proves a measurement produces a live number. It cannot prove the number
 * has anything to do with the page. A function that returns the element count would pass
 * every census check: finite, varying, never dead. The only way to tell a measurement from
 * a coincidence is to change the input in a way whose effect is known in advance, and see
 * whether the number moves as it must.
 *
 * <p>So the mutations here are not random bytes. Each one is a change with a stated
 * consequence -- double every coordinate and ink coverage must not budge, because it is a
 * ratio; nudge every box three pixels off the grid and adherence to the spacing scale must
 * fall. A measurement that fails such a claim is either wrong or measuring something other
 * than what its name says. A measurement that passes none of them is decoration.
 *
 * <p>The second family is cruder and catches a different fault. Real pages are well
 * formed because browsers make them so; a snapshot read from disk, hand-edited, produced
 * by an older version of the probe or built by a caller is not. These mutations hand the
 * tool input no browser would ever produce -- no elements, every id the same, a parent
 * that is its own child, coordinates of a billion, text a megabyte long -- and ask only
 * that nothing throws, nothing hangs and no measurement comes back as NaN. A NaN that
 * escapes here becomes a threshold comparison that is silently false, and a rule that
 * never fires again.
 */
public final class Fuzz {

    private static final Gson GSON = new Gson();

    private Fuzz() { }

    // ------------------------------------------------------------ expectations

    /** What a mutation must do to a measurement. */
    public enum Relation {
        /** The number must not move. The mutation changed nothing this measures. */
        UNCHANGED,
        /** The number must go up. */
        INCREASES,
        /** The number must go down. */
        DECREASES,
        /** The number must move, in either direction. */
        CHANGES,
        /** The number may rise or hold, but must not fall. Many real invariants are one-sided. */
        NOT_DECREASES,
        /** The number may fall or hold, but must not rise. */
        NOT_INCREASES
    }

    /**
     * One claim: deform the page this way, and this measurement must respond that way.
     *
     * <p>{@code appliesWhen} is the guard against a vacuous claim. "Set every element to one
     * font family, and the family count must fall" is a real claim on a page with three
     * families and nonsense on a page with one, where the count was already at the floor and
     * no mutation on earth could lower it. Thirty of the first run's thirty broken claims
     * were that: the mutation had nothing to act on, or the measure had nowhere to go. A
     * guard says so instead of reporting a failure that is really a tautology.
     *
     * <p>Skipped claims are counted and reported, because a claim skipped on every page is
     * untested, and an untested claim is the same silence this whole exercise is about.
     */
    public record Claim(String measure, Relation relation, String why,
                        java.util.function.Predicate<Analysis> appliesWhen) {

        public Claim(String measure, Relation relation, String why) {
            this(measure, relation, why, a -> true);
        }

        public Claim when(java.util.function.Predicate<Analysis> guard) {
            return new Claim(measure, relation, why, guard);
        }
    }

    /** A deformation of a snapshot, with the claims it makes about the measurements. */
    public record Mutation(String name, String what, BiConsumer<PageSnapshot, Random> apply,
                           List<Claim> claims) {

        /** True for the crude mutations, which claim only that nothing breaks. */
        public boolean isChaos() { return claims.isEmpty(); }
    }

    /** What one claim did, or why it was not asked. */
    public record ClaimResult(String mutation, Claim claim, double before, double after,
                              boolean held, boolean skipped) { }

    /**
     * What one mutation did overall.
     *
     * @param timedOut true when the analysis never came back. A hang is a result, not a
     *                 failure of the harness: the first run of this fuzzer consumed ten
     *                 minutes of CPU in an unguarded ancestor walk and printed nothing at
     *                 all, which told us less than a one-line row would have.
     */
    public record MutationResult(Mutation mutation, boolean survived, boolean timedOut,
                                 String error, int nonFiniteMeasures,
                                 List<String> nonFiniteNames, int findingsBefore,
                                 int findingsAfter, List<ClaimResult> claims) {

        public boolean isClean() {
            return survived && !timedOut && nonFiniteMeasures == 0
                    && claims.stream().allMatch(c -> c.held() || c.skipped());
        }
    }

    public record Result(String baseline, List<MutationResult> mutations) {

        public List<MutationResult> timedOut() {
            return mutations.stream().filter(MutationResult::timedOut).toList();
        }

        public List<MutationResult> broken() {
            return mutations.stream().filter(m -> !m.isClean()).toList();
        }

        public List<ClaimResult> brokenClaims() {
            return mutations.stream().flatMap(m -> m.claims().stream())
                    .filter(c -> !c.held() && !c.skipped()).toList();
        }

        public List<ClaimResult> skippedClaims() {
            return mutations.stream().flatMap(m -> m.claims().stream())
                    .filter(ClaimResult::skipped).toList();
        }

        public int claimCount() {
            return mutations.stream().mapToInt(m -> m.claims().size()).sum();
        }

        public boolean isHealthy() { return broken().isEmpty(); }
    }

    // ------------------------------------------------------------ measurements

    /**
     * Everything a claim can be made about, read off one analysis in a single pass.
     *
     * <p>Besides the page-level figures there is one entry per rule, {@code rule:<id>}, and
     * those are the sharp instruments. A claim about the total finding count is nearly
     * useless: shrinking every button to twelve pixels made mdn-article report two findings
     * *fewer*, because the shrunken boxes stopped overlapping each other even as they
     * became unhittable. The count is not monotonic in how bad a page is and never will be.
     * "touch-target must fire more" is a claim with teeth; "the page must look worse" is
     * not.
     */
    static Map<String, Double> readings(Analysis a) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("elements", (double) a.snapshot().elements.size());
        PageProfile.Profile profile = PageProfile.of(a);
        m.put("ink coverage", profile.inkCoverage());
        m.put("balance", profile.balance());
        m.put("alignment points", (double) profile.alignmentPoints());
        m.put("grid tightness", profile.gridTightness());
        m.put("spacing base unit", (double) a.spacing().baseUnit());
        m.put("spacing adherence", a.spacing().adherence());
        m.put("type steps", (double) a.type().scale().size());
        m.put("type families", (double) a.type().families().size());
        m.put("largest to body", a.type().largestToBodyRatio());
        m.put("peer groups", (double) Consistency.peerGroups(a.snapshot()).size());
        m.put("consistency outliers", (double) a.consistencyOutliers().size());
        var ranked = a.prominence().ranked();
        m.put("prominence spread", ranked.size() < 2 ? 0
                : ranked.get(0).score - ranked.get(ranked.size() - 1).score);
        m.put("worst text contrast", a.snapshot().textElements().stream()
                .mapToDouble(Elem::textContrast).filter(v -> v > 0).min().orElse(0));
        m.put("mean text contrast", a.snapshot().textElements().stream()
                .mapToDouble(Elem::textContrast).filter(v -> v > 0).average().orElse(0));
        m.put("interactive", (double) a.snapshot().interactiveElements().size());

        List<Finding> findings = RuleSet.standard().run(a);
        m.put("findings", (double) findings.size());
        Map<String, Double> byRule = new LinkedHashMap<>();
        for (Finding f : findings) {
            // Read the uncapped total where the report carries it. A rule that fires more
            // than Config.maxFindingsPerRule times has its tail replaced by a summary
            // finding, so the reported count sticks at the cap and stops responding -- which
            // looked exactly like a rule that had stopped noticing. Shrinking every button
            // on nine different pages to twelve pixels left touch-target at 26 both before
            // and after, because 26 is the ceiling, not an answer.
            Object total = f.data.get("totalFindings");
            if (total instanceof Number n) {
                byRule.put(f.ruleId, n.doubleValue());
            } else {
                byRule.merge(f.ruleId, 1.0, Double::sum);
            }
        }
        for (com.calpano.uxlens.rules.Rule rule : RuleSet.standard().rules()) {
            m.put("rule:" + rule.id(), byRule.getOrDefault(rule.id(), 0.0));
        }
        return m;
    }

    // --------------------------------------------------------------- mutations

    /** Every mutation, claims first and chaos after. */
    public static List<Mutation> mutations() {
        List<Mutation> out = new ArrayList<>(metamorphic());
        out.addAll(chaos());
        return out;
    }

    /**
     * Mutations with a stated consequence.
     *
     * <p>Each claim is an argument about what a measurement means, written so a machine can
     * check it. They are the part of this file worth disagreeing with.
     */
    public static List<Mutation> metamorphic() {
        List<Mutation> out = new ArrayList<>();

        out.add(new Mutation("scale-geometry-2x",
                "every coordinate, size, margin and padding doubled",
                (s, r) -> {
                    s.requestedWidth *= 2;
                    s.requestedHeight *= 2;
                    s.innerWidth *= 2;
                    s.innerHeight *= 2;
                    s.scrollWidth *= 2;
                    s.scrollHeight *= 2;
                    for (Elem e : s.elements) {
                        e.x *= 2; e.y *= 2; e.w *= 2; e.h *= 2;
                        e.marginTop *= 2; e.marginRight *= 2;
                        e.marginBottom *= 2; e.marginLeft *= 2;
                        e.padTop *= 2; e.padRight *= 2; e.padBottom *= 2; e.padLeft *= 2;
                        e.gap *= 2; e.columnGap *= 2;
                    }
                },
                // No claim of scale invariance for ink coverage or balance, though both are
                // ratios and both look like they should be. What decides them is the set of
                // content elements, and that set is chosen with absolute thresholds -- a
                // painted leaf block counts as content above 400 square pixels, an image
                // counts as icon-sized below 3600. Those are judgements about what a person
                // can see at real size, so they are right to be absolute, and the
                // consequence is that quadrupling every area moves elements across them. On
                // the Arabic Wikipedia coverage went from 0.583 to 0.593 for exactly that
                // reason. The measurement is sound and the invariance was never there.
                List.of(
                        // NOT_DECREASES, not UNCHANGED. Edges are counted after rounding to
                        // the whole pixel, which is a deliberate tolerance -- two edges
                        // within half a pixel are the same edge -- and that tolerance is
                        // absolute, so it does not scale with the page. Doubling can
                        // therefore separate edges the tolerance had merged. It must never
                        // merge ones it had separated, which is what this still forbids.
                        new Claim("alignment points", Relation.NOT_DECREASES,
                                "a half-pixel tolerance can stop merging edges as they move "
                                        + "apart, but cannot start"),
                        // Not INCREASES. The base unit is chosen from a fixed candidate set
                        // {16,12,8,6,5,4,3,2}, so a page whose rhythm doubles from 5 to 10 is
                        // still reported as 5 -- every multiple of ten is a multiple of five.
                        // True, and less informative than it could be: the set has no 10, 24
                        // or 32, so those rhythms are always described by a factor of
                        // themselves. The fuzzer found that, not a bug.
                        // No claim about the spacing scale here, and the reason is worth
                        // keeping. The obvious one -- that doubling every distance cannot
                        // give a page a finer rhythm -- is false, and chasing it was
                        // instructive. xam.de spaces things at 10, 5, 4 and 5.5px; the 5.5
                        // counts towards a 5px grid only because the tolerance for sub-pixel
                        // rounding is half a pixel wide. Double it to 11 and it is off every
                        // grid, as is 29.5 doubled to 58.5, and a 2px unit really does then
                        // explain more of the page than a 5px one. The detector was right
                        // and the claim was wrong. What the chase did find is below, in
                        // impose-a-grid and destroy-the-grid, which test the detector on the
                        // job it actually has.
                        new Claim("elements", Relation.UNCHANGED,
                                "scaling a page does not add or remove elements"))));

        out.add(new Mutation("translate-everything",
                "the whole page shifted 137px right and 91px down",
                (s, r) -> {
                    for (Elem e : s.elements) { e.x += 137; e.y += 91; }
                },
                // The obvious claim here -- that a grid is a set of relations and survives
                // being carried -- is false, and finding out why was worth the mutation. The
                // page profile measures the first viewport, by design: a page is judged on
                // what a visitor meets before scrolling. Shift the content down and some of
                // it leaves that viewport, so there is genuinely less to be aligned. The
                // measurement is not translation invariant and should not be.
                List.of(new Claim("elements", Relation.UNCHANGED,
                                "moving a page does not add or remove elements"),
                        new Claim("ink coverage", Relation.DECREASES,
                                "content pushed below the fold is no longer in the viewport "
                                        + "the coverage is measured against")
                                // A full-bleed map covers the viewport completely, and a
                                // ceiling cannot be pushed through.
                                .when(a -> PageProfile.of(a).inkCoverage() > 0.01
                                        && PageProfile.of(a).inkCoverage() < 0.99),
                        new Claim("alignment points", Relation.NOT_INCREASES,
                                "the same edges, shifted together, minus whatever left the "
                                        + "fold"))));

        out.add(new Mutation("jitter-off-the-grid",
                "every box nudged by one to three pixels, independently",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.x += 1 + r.nextInt(3);
                        e.y += 1 + r.nextInt(3);
                        e.marginTop += 1 + r.nextInt(3);
                        e.marginLeft += 1 + r.nextInt(3);
                        e.padTop += 1 + r.nextInt(3);
                    }
                },
                List.of(new Claim("alignment points", Relation.INCREASES,
                                "edges that were shared are now each their own"),
                        new Claim("spacing adherence", Relation.DECREASES,
                                "the measure of how much spacing sits on a scale, after "
                                        + "taking it off the scale")
                                .when(a -> hasSpacing().test(a)
                                        && a.spacing().adherence() > 0.01))));

        out.add(new Mutation("impose-a-grid",
                "every margin, padding and gap snapped to the nearest multiple of 8",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.marginTop = snap(e.marginTop);
                        e.marginRight = snap(e.marginRight);
                        e.marginBottom = snap(e.marginBottom);
                        e.marginLeft = snap(e.marginLeft);
                        e.padTop = snap(e.padTop);
                        e.padRight = snap(e.padRight);
                        e.padBottom = snap(e.padBottom);
                        e.padLeft = snap(e.padLeft);
                        e.gap = snap(e.gap);
                        e.columnGap = snap(e.columnGap);
                    }
                },
                List.of(new Claim("spacing base unit", Relation.NOT_DECREASES,
                                "a page put entirely on an eight-pixel grid cannot be "
                                        + "described by a finer unit than before")
                                .when(hasSpacing()),
                        new Claim("spacing adherence", Relation.INCREASES,
                                "the share of spacing values sitting on a scale, after "
                                        + "putting all of them on one")
                                .when(a -> hasSpacing().test(a)
                                        && a.spacing().adherence() < 0.99))));

        out.add(new Mutation("destroy-the-grid",
                "every margin, padding and gap set to a value on no scale at all",
                (s, r) -> {
                    double[] awkward = {7.3, 11.7, 13.1, 19.9, 23.3, 31.7};
                    for (Elem e : s.elements) {
                        if (e.marginTop > 0) e.marginTop = awkward[r.nextInt(awkward.length)];
                        if (e.marginBottom > 0) e.marginBottom = awkward[r.nextInt(awkward.length)];
                        if (e.marginLeft > 0) e.marginLeft = awkward[r.nextInt(awkward.length)];
                        if (e.marginRight > 0) e.marginRight = awkward[r.nextInt(awkward.length)];
                        if (e.padTop > 0) e.padTop = awkward[r.nextInt(awkward.length)];
                        if (e.padBottom > 0) e.padBottom = awkward[r.nextInt(awkward.length)];
                        if (e.padLeft > 0) e.padLeft = awkward[r.nextInt(awkward.length)];
                        if (e.padRight > 0) e.padRight = awkward[r.nextInt(awkward.length)];
                        if (e.gap > 0) e.gap = awkward[r.nextInt(awkward.length)];
                        if (e.columnGap > 0) e.columnGap = awkward[r.nextInt(awkward.length)];
                    }
                },
                List.of(new Claim("spacing adherence", Relation.DECREASES,
                        "values chosen to sit on no scale must not be reported as sitting "
                                + "on one")
                        .when(a -> hasSpacing().test(a) && a.spacing().adherence() > 0.01))));

        out.add(new Mutation("one-font-size",
                "every element set to 16px",
                (s, r) -> {
                    for (Elem e : s.elements) e.fontSize = 16;
                },
                List.of(new Claim("type steps", Relation.DECREASES,
                                "a page with one size has a scale of one step")
                                .when(a -> a.type().scale().size() > 1),
                        new Claim("largest to body", Relation.DECREASES,
                                "nothing is larger than the body any more")
                                .when(a -> a.type().largestToBodyRatio() > 1.01))));

        out.add(new Mutation("one-font-family",
                "every element set to the same family",
                (s, r) -> {
                    for (Elem e : s.elements) e.fontFamily = "Inter, sans-serif";
                },
                List.of(new Claim("type families", Relation.DECREASES,
                        "counting families, after there is one")
                        .when(a -> a.type().families().size() > 1))));

        out.add(new Mutation("wash-out-the-text",
                "every text colour moved nine tenths of the way to its background",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        var fg = com.calpano.uxlens.model.Rgba.parse(e.color);
                        var bg = e.bg();
                        if (fg == null || bg == null) continue;
                        e.color = String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,1)",
                                (int) Math.round(fg.r() + (bg.r() - fg.r()) * 0.9),
                                (int) Math.round(fg.g() + (bg.g() - fg.g()) * 0.9),
                                (int) Math.round(fg.b() + (bg.b() - fg.b()) * 0.9));
                    }
                },
                // Against the mean, not the worst. A real page often already contains a
                // pair at ratio 1.0 -- decorative text the same colour as what is behind it
                // -- and nothing can push the worst case below the floor.
                List.of(new Claim("mean text contrast", Relation.DECREASES,
                                "text nine tenths of the way to its background is harder to "
                                        + "read, and the number has to agree")
                                .when(a -> a.snapshot().textElements().stream()
                                        .anyMatch(e -> e.textContrast() > 1.5)),
                        new Claim("rule:text-contrast", Relation.INCREASES,
                                "text washed out to nothing must be reported as unreadable")
                                .when(a -> a.snapshot().textElements().stream()
                                        .anyMatch(e -> e.textContrast() > 4.5)))));

        out.add(new Mutation("reverse-document-order",
                "the element list reversed, geometry untouched",
                (s, r) -> java.util.Collections.reverse(s.elements),
                // The strongest claims here, because nothing about them is a tolerance.
                // Document order is not a visual property: a reader sees the page, not the
                // markup. Any measurement that moves under this is order-dependent by
                // accident, and the grouping pass in particular assembles clusters by
                // walking the list, which is exactly the shape of code that would be.
                List.of(new Claim("ink coverage", Relation.UNCHANGED,
                                "what is painted does not depend on the order it is listed in"),
                        new Claim("alignment points", Relation.UNCHANGED,
                                "nor do the edges"),
                        new Claim("prominence spread", Relation.UNCHANGED,
                                "how loud the loudest thing is cannot depend on where it sits "
                                        + "in a list"),
                        new Claim("peer groups", Relation.UNCHANGED,
                                "things that look alike look alike in any order"))));

        out.add(new Mutation("shrink-the-targets",
                "every touch target reduced to 12 by 12",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        if (!LayoutRules.TouchTarget.isTarget(s, e)) continue;
                        e.w = 12;
                        e.h = 12;
                    }
                },
                // The guard needs a target that currently *passes*, and passing is measured
                // against the rule's own minimum -- 44px on a phone, not 24. Asking for 24
                // let the guard succeed on controls the rule was already reporting, where
                // shrinking changes the severity and not the count, and the claim failed on
                // a page where it had nothing new to find.
                List.of(new Claim("rule:touch-target", Relation.INCREASES,
                        "twelve pixels is below every touch target minimum ever published")
                        .when(a -> {
                            double min = a.viewport().mobile()
                                    ? a.config().minTouchTargetPx
                                    : a.config().minPointerTargetPx;
                            return a.snapshot().elements.stream()
                                    .anyMatch(e -> LayoutRules.TouchTarget.isTarget(a.snapshot(), e)
                                            && Math.min(e.w, e.h) >= min - 0.5);
                        }))));

        out.add(new Mutation("hide-everything",
                "every element given opacity 0",
                (s, r) -> {
                    for (Elem e : s.elements) e.opacity = 0;
                },
                // Against prominence, not the finding count. Setting every element to
                // opacity 0 left the count identical on Grafana and on diagrams.net, which
                // says plainly that the rules do not consult opacity -- a page painted
                // entirely in nothing draws the same report as the page itself. Arguable
                // either way: a rule about contrast or spacing is about the CSS, and an
                // invisible page is still badly specified. Prominence is different. It
                // claims to rank what the eye is drawn to, so it has no excuse.
                List.of(new Claim("prominence spread", Relation.DECREASES,
                        "nothing can be louder than anything else when none of it is "
                                + "painted")
                        .when(a -> {
                            var ranked = a.prominence().ranked();
                            return ranked.size() >= 2
                                    && ranked.get(0).score - ranked.get(ranked.size() - 1).score
                                            > 0.01;
                        }))));

        out.add(new Mutation("strip-the-names",
                "every accessible name, label and alt text removed",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.accessibleName = null;
                        e.ariaLabel = null;
                        e.nameSource = null;
                        e.title = null;
                        if (e.img != null) e.img.alt = null;
                    }
                },
                // The guard has to find a control the rule could newly complain about: one
                // that is pushable, carries no words of its own, and currently has a name to
                // take away. Without the pushable part the guard passed on TodoMVC, whose
                // only nameless-after-stripping control is a text input -- and that rule was
                // deliberately scoped to pushable elements, because a field is named by its
                // label and not by what is printed inside it.
                List.of(new Claim("rule:unlabelled-icon-control", Relation.INCREASES,
                        "a control with no words on it and no name left is exactly what "
                                + "that rule is for")
                        .when(a -> a.snapshot().elements.stream()
                                .anyMatch(e -> e.actionable && e.isIconOnly()
                                        && !e.hasNoAccessibleName()
                                        && ("button".equals(e.tag) || "a".equals(e.tag)
                                                || "summary".equals(e.tag)
                                                || "button".equals(e.role)
                                                || "link".equals(e.role)))))));

        return out;
    }

    /**
     * Input no browser would produce, which is the input a tool eventually gets.
     *
     * <p>These claim nothing about any measurement. They ask only that the tool does not
     * throw, does not hang and returns no NaN -- because a NaN propagates into a threshold
     * comparison, every comparison against it is false, and the rule goes quiet for good.
     */
    public static List<Mutation> chaos() {
        List<Mutation> out = new ArrayList<>();

        out.add(chaos("no-elements", "an empty element list",
                (s, r) -> s.elements = new ArrayList<>()));
        out.add(chaos("body-only", "one element and nothing else",
                (s, r) -> s.elements = new ArrayList<>(s.elements.subList(0, 1))));
        out.add(chaos("null-colours", "every colour string null",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.color = null; e.backgroundColor = null;
                        e.borderColor = null; e.effectiveBackground = null;
                    }
                    s.documentBackground = null;
                }));
        out.add(chaos("junk-colours", "colour strings that parse as nothing",
                (s, r) -> {
                    String[] junk = {"not-a-colour", "oklch(", "#gg", "rgb(", "", "   ",
                            "color(display-p3", "var(--undefined)"};
                    for (Elem e : s.elements) {
                        e.color = junk[r.nextInt(junk.length)];
                        e.backgroundColor = junk[r.nextInt(junk.length)];
                        e.effectiveBackground = junk[r.nextInt(junk.length)];
                    }
                }));
        out.add(chaos("negative-geometry", "negative widths, heights and offsets",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.w = -e.w - 1; e.h = -e.h - 1;
                        e.x = -e.x; e.y = -e.y;
                        e.marginTop = -8; e.padLeft = -4;
                    }
                }));
        out.add(chaos("zero-geometry", "every box collapsed to nothing",
                (s, r) -> {
                    for (Elem e : s.elements) { e.w = 0; e.h = 0; }
                }));
        out.add(chaos("astronomical-geometry", "coordinates and sizes of a billion",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.x = 1e9; e.y = 1e9; e.w = 1e9; e.h = 1e9;
                    }
                    s.scrollHeight = Integer.MAX_VALUE;
                }));
        out.add(chaos("non-finite-geometry", "NaN and infinite coordinates",
                (s, r) -> {
                    for (int i = 0; i < s.elements.size(); i++) {
                        Elem e = s.elements.get(i);
                        switch (i % 4) {
                            case 0 -> e.x = Double.NaN;
                            case 1 -> e.w = Double.POSITIVE_INFINITY;
                            case 2 -> e.h = Double.NEGATIVE_INFINITY;
                            default -> e.fontSize = Double.NaN;
                        }
                    }
                }));
        out.add(chaos("every-id-the-same", "all element ids zero",
                (s, r) -> {
                    for (Elem e : s.elements) e.id = 0;
                }));
        out.add(chaos("self-parenting", "every element its own parent",
                (s, r) -> {
                    for (Elem e : s.elements) e.parentId = e.id;
                }));
        out.add(chaos("parent-cycle", "a two-element loop in the tree",
                (s, r) -> {
                    if (s.elements.size() < 2) return;
                    Elem a = s.elements.get(0), b = s.elements.get(1);
                    a.parentId = b.id;
                    b.parentId = a.id;
                }));
        out.add(chaos("parents-that-do-not-exist", "every parent id pointing nowhere",
                (s, r) -> {
                    for (Elem e : s.elements) e.parentId = 999_999;
                }));
        out.add(chaos("deep-chain", "a nesting chain two thousand deep",
                (s, r) -> {
                    List<Elem> chain = new ArrayList<>(s.elements.subList(0, 1));
                    for (int i = 1; i <= 2000; i++) {
                        Elem e = GSON.fromJson(GSON.toJson(chain.get(0)), Elem.class);
                        e.id = 10_000 + i;
                        e.parentId = i == 1 ? chain.get(0).id : 10_000 + i - 1;
                        e.selector = "div:nth-of-type(" + i + ")";
                        e.x = i; e.y = i; e.w = 10; e.h = 10;
                        chain.add(e);
                    }
                    s.elements = chain;
                }));
        out.add(chaos("no-tags", "tag and selector null on every element",
                (s, r) -> {
                    for (Elem e : s.elements) { e.tag = null; e.selector = null; }
                }));
        out.add(chaos("zero-viewport", "a viewport of no width and no height",
                (s, r) -> {
                    s.requestedWidth = 0; s.requestedHeight = 0;
                    s.innerWidth = 0; s.innerHeight = 0;
                }));
        out.add(chaos("absurd-type", "font sizes, weights and leading at the extremes",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.fontSize = r.nextBoolean() ? 1e6 : 1e-6;
                        e.fontWeight = r.nextBoolean() ? -400 : 100_000;
                        e.lineHeight = -1e6;
                        e.letterSpacing = 1e6;
                    }
                }));
        out.add(chaos("a-megabyte-of-text", "one element holding a million characters",
                (s, r) -> {
                    Elem e = s.elements.get(s.elements.size() - 1);
                    e.text = "x".repeat(1_000_000);
                    e.textLength = e.text.length();
                }));
        out.add(chaos("awkward-unicode", "bidi overrides, combining marks and surrogate pairs",
                (s, r) -> {
                    String nasty = "‮override‬ é́́ "
                            + "👩‍💻 \u0000 �";
                    for (Elem e : s.elements) {
                        if (e.hasOwnText()) e.text = nasty;
                        e.ariaLabel = nasty;
                        e.selector = nasty;
                    }
                }));
        out.add(chaos("no-text-anywhere", "every text string null",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        e.text = null; e.visibleText = null; e.textLength = 0;
                    }
                }));
        out.add(chaos("every-element-fixed", "the whole page in a fixed layer",
                (s, r) -> {
                    for (Elem e : s.elements) e.fixedLayer = true;
                }));
        out.add(chaos("line-boxes-that-lie", "line metrics contradicting the geometry",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        if (e.lines == null) continue;
                        e.lines.lineCount = -5;
                        e.lines.maxLineWidth = 1e9;
                        e.lines.firstLineWidth = Double.NaN;
                        e.clientRectCount = -1;
                    }
                }));
        out.add(chaos("images-with-no-dimensions", "natural sizes of zero and negative",
                (s, r) -> {
                    for (Elem e : s.elements) {
                        if (e.img == null) continue;
                        e.img.naturalWidth = 0;
                        e.img.naturalHeight = -10;
                        e.img.src = null;
                    }
                }));

        return out;
    }

    /** True when the page has enough spacing values for a scale to be inferred at all. */
    private static java.util.function.Predicate<Analysis> hasSpacing() {
        return a -> a.spacing().sampleCount() >= 10;
    }

    /** Nearest multiple of eight, leaving zero alone so a gap-less box stays gap-less. */
    private static double snap(double v) {
        return v <= 0 ? v : Math.max(8, Math.round(v / 8.0) * 8.0);
    }

    private static Mutation chaos(String name, String what, BiConsumer<PageSnapshot, Random> f) {
        return new Mutation(name, what, f, List.of());
    }

    // --------------------------------------------------------------------- run

    /** Deep copy, so a mutation cannot leak into the baseline or into the next mutation. */
    static PageSnapshot copy(PageSnapshot snapshot) {
        return GSON.fromJson(GSON.toJson(snapshot), PageSnapshot.class);
    }

    /** Applies every mutation to one capture and checks what it claimed. */
    public static Result run(CorpusStore.Entry entry, Config config, long seed) {
        return run(entry.key(), entry.snapshot(), entry.viewport(), config, seed);
    }

    /** How long one mutation may take before it is called a hang. */
    public static final long TIMEOUT_MS = 60_000;

    public static Result run(String baseline, PageSnapshot snapshot, Viewport viewport,
                             Config config, long seed) {
        Analysis original = Analysis.ofSnapshotOnly(copy(snapshot), viewport, config);
        Map<String, Double> before = readings(original);
        int findingsBefore = (int) (double) before.get("findings");

        List<MutationResult> results = new ArrayList<>();
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                    Thread t = new Thread(runnable, "fuzz");
                    t.setDaemon(true);      // a hung mutation must not keep the JVM alive
                    return t;
                });
        try {
            for (Mutation mutation : mutations()) {
                results.add(attempt(mutation, snapshot, viewport, config, seed, before,
                        findingsBefore, pool));
            }
        } finally {
            pool.shutdownNow();
        }
        return new Result(baseline, results);
    }

    /**
     * One mutation, on its own thread with a clock on it.
     *
     * <p>The thread is not interrupted on timeout -- a tight numeric loop does not answer
     * interrupts -- so the row is reported and the runaway is left to the daemon flag and
     * the end of the process. Honest, and better than printing nothing for ten minutes.
     */
    private static MutationResult attempt(Mutation mutation, PageSnapshot snapshot,
                                          Viewport viewport, Config config, long seed,
                                          Map<String, Double> before, int findingsBefore,
                                          java.util.concurrent.ExecutorService pool) {
        var task = pool.submit(() -> measure(mutation, snapshot, viewport, config, seed,
                before));
        try {
            Attempt attempt = task.get(TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            return new MutationResult(mutation, attempt.error() == null, false, attempt.error(),
                    attempt.nonFinite().size(), attempt.nonFinite(), findingsBefore,
                    attempt.findingsAfter(), attempt.claims());
        } catch (java.util.concurrent.TimeoutException e) {
            task.cancel(true);
            return new MutationResult(mutation, false, true,
                    "did not finish in " + TIMEOUT_MS / 1000 + "s", 0, List.of(),
                    findingsBefore, -1, List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fuzzing " + mutation.name(), e);
        } catch (java.util.concurrent.ExecutionException e) {
            return new MutationResult(mutation, false, false,
                    String.valueOf(e.getCause()), 0, List.of(), findingsBefore, -1, List.of());
        }
    }

    private record Attempt(String error, List<String> nonFinite, int findingsAfter,
                           List<ClaimResult> claims) { }

    private static Attempt measure(Mutation mutation, PageSnapshot snapshot, Viewport viewport,
                                   Config config, long seed, Map<String, Double> before) {
        PageSnapshot mutated = copy(snapshot);
        List<String> nonFinite = new ArrayList<>();
        List<ClaimResult> claims = new ArrayList<>();
        try {
            mutation.apply().accept(mutated, new Random(seed));
            Analysis analysis = Analysis.ofSnapshotOnly(mutated, viewport, config);

            Map<String, Double> after = readings(analysis);
            after.forEach((name, value) -> {
                if (!Double.isFinite(value)) nonFinite.add(name);
            });
            Analysis baseline = Analysis.ofSnapshotOnly(copy(snapshot), viewport, config);
            for (Claim claim : mutation.claims()) {
                double was = before.get(claim.measure());
                double is = after.get(claim.measure());
                boolean applies = claim.appliesWhen().test(baseline);
                claims.add(new ClaimResult(mutation.name(), claim, was, is,
                        applies && holds(claim.relation(), was, is), !applies));
            }
            return new Attempt(null, nonFinite, (int) (double) after.get("findings"), claims);
        } catch (RuntimeException | StackOverflowError | OutOfMemoryError e) {
            return new Attempt(e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + firstLine(e.getMessage())),
                    nonFinite, -1, claims);
        }
    }

    private static boolean holds(Relation relation, double before, double after) {
        if (!Double.isFinite(before) || !Double.isFinite(after)) return false;
        // A hair of tolerance: doubling coordinates and halving them back is not bit-exact,
        // and a claim about meaning should not fail on the last bit of a mantissa.
        double epsilon = Math.max(1e-9, Math.abs(before) * 1e-9);
        return switch (relation) {
            case UNCHANGED -> Math.abs(after - before) <= epsilon;
            case INCREASES -> after > before + epsilon;
            case DECREASES -> after < before - epsilon;
            case CHANGES -> Math.abs(after - before) > epsilon;
            case NOT_DECREASES -> after >= before - epsilon;
            case NOT_INCREASES -> after <= before + epsilon;
        };
    }

    private static String firstLine(String message) {
        int cut = message.indexOf('\n');
        return cut > 0 ? message.substring(0, cut) : message;
    }

    /** Runs over several captures and keeps one row per mutation, worst case first. */
    public static List<Result> runAll(List<CorpusStore.Entry> corpus, Config config, long seed) {
        return corpus.stream()
                .sorted(Comparator.comparing(CorpusStore.Entry::key))
                .map(entry -> run(entry, config, seed))
                .toList();
    }

    /** Findings are compared by count alone; this keeps the type honest about that. */
    static int countFindings(List<Finding> findings) { return findings.size(); }
}
