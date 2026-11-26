package com.calpano.uxlens.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * What of the professional body of knowledge this tool covers, and what it does not.
 *
 * <p>The second half matters more than the first. Most of what a UX professional knows
 * cannot be read off a rendered page at a single moment: whether the system keeps the user
 * informed, whether an error can be undone, whether the vocabulary matches the user's,
 * whether the task could be done in fewer steps. Those need a task, a user, or both. A tool
 * that quietly implemented weak proxies for them would produce confident findings about
 * questions it cannot see, which is worse than leaving them alone.
 *
 * <p>So the out-of-scope list is published beside the coverage, and each entry says what
 * would actually be needed to answer it. The honest summary is that this tool addresses the
 * presentation layer thoroughly and the interaction layer barely at all.
 */
public final class StandardsCoverage {

    private StandardsCoverage() { }

    /** One body of knowledge and the rules resting on it. */
    public record SourceCoverage(Reference.Source source, List<String> ruleIds) {

        public int ruleCount() { return ruleIds.size(); }
    }

    /**
     * Something the body of knowledge asks for that this tool deliberately does not check.
     *
     * @param area       what it is, in the language practitioners use
     * @param source     where it comes from
     * @param why        why a rendered page cannot answer it
     * @param insteadUse what would answer it
     */
    public record OutOfScope(String area, Reference.Source source, String why, String insteadUse) { }

    /** Coverage by source, most-covered first. */
    public static List<SourceCoverage> coverage() {
        List<SourceCoverage> out = new ArrayList<>();
        for (Reference.Source source : Reference.Source.values()) {
            List<String> rules = RuleReferences.bySource(source);
            if (!rules.isEmpty()) out.add(new SourceCoverage(source, rules));
        }
        out.sort((x, y) -> Integer.compare(y.ruleCount(), x.ruleCount()));
        return out;
    }

    /** Rules whose only authority is this project's judgement. */
    public static List<String> houseOnly() { return RuleReferences.houseOnly(); }

    /**
     * What this tool does not check, and why not.
     *
     * <p>Every entry here was considered and rejected rather than overlooked. Most are
     * rejected for the same reason: a static render shows what a page looks like, not what
     * it does, what it means, or what happens next.
     */
    public static List<OutOfScope> outOfScope() {
        return List.of(
                new OutOfScope("Visibility of system status",
                        Reference.Source.ISO_9241_110,
                        "Whether the system tells the user what is going on is a property of a "
                                + "sequence over time. A single render shows one frame of it, and "
                                + "the frame that matters is usually the one during the wait.",
                        "Drive the flow and assert on the states, or instrument the application."),

                new OutOfScope("Error prevention, recovery and undo",
                        Reference.Source.ISO_9241_110,
                        "Requires committing the error. Nothing about the resting page says "
                                + "whether the action can be taken back. destructive-adjacency "
                                + "catches one narrow geometric case and claims nothing more.",
                        "Scripted interaction tests, or a design review of the flows."),

                new OutOfScope("Match between the system and the real world",
                        Reference.Source.CPUX_F,
                        "Whether the words match the user's vocabulary is a question about the "
                                + "users, and this tool has never met them. It would also be the "
                                + "easiest thing here to fake convincingly with a language model.",
                        "User research. There is no substitute and no shortcut."),

                new OutOfScope("Suitability for the task, and efficiency of use",
                        Reference.Source.ISO_9241_110,
                        "Needs to know what the task is and how often it is done. A screen that "
                                + "is wrong for an expert doing it fifty times a day can be right "
                                + "for a novice doing it once.",
                        "Task analysis, then usability testing against those tasks."),

                new OutOfScope("Recognition rather than recall; learnability",
                        Reference.Source.CPUX_F,
                        "Both are claims about what the user remembers between encounters, which "
                                + "is not visible in any number of renders.",
                        "Usability testing with first-time and returning participants."),

                new OutOfScope("Keyboard operability and focus order",
                        Reference.Source.WCAG,
                        "Partly checkable, and deliberately not claimed: tab order, traps and "
                                + "shortcuts need the page driven, and a half-check would read as "
                                + "a pass. focus-not-visible checks visibility only, by actually "
                                + "focusing each control.",
                        "axe-core or similar for the semantic half, plus scripted keyboard walks."),

                new OutOfScope("Information architecture and navigation structure",
                        Reference.Source.CPUX_F,
                        "Whether things are in the right place is a question about a whole site "
                                + "and its users, not about one page's geometry.",
                        "Card sorting and tree testing."),

                new OutOfScope("Content quality, microcopy and tone",
                        Reference.Source.CPUX_F,
                        "A language model would happily produce opinions here, and they would "
                                + "carry the same confident tone as the measured findings while "
                                + "resting on nothing. Keeping the two apart is the point.",
                        "Editorial review against a content style guide."),

                new OutOfScope("Perceived performance and layout stability",
                        Reference.Source.PLATFORM,
                        "Genuinely measurable, and genuinely a different tool's job: it needs the "
                                + "loading timeline rather than the settled page this measures.",
                        "Lighthouse or the browser's own Core Web Vitals instrumentation."),

                new OutOfScope("Where a visitor will actually look",
                        Reference.Source.HOUSE,
                        "The saliency map here is a bottom-up model derived on photographs, and "
                                + "the literature on attention in graphic designs finds that such "
                                + "models transfer poorly to interfaces: a page is dominated by "
                                + "text and flat regions, and attention in one is largely governed "
                                + "by learned convention that no bottom-up model represents. It is "
                                + "a weak prior on pop-out, not a prediction of gaze.",
                        "Eye tracking, or a saliency model trained on interfaces rather than on "
                                + "natural scenes."),

                new OutOfScope("Whether the page is any good",
                        Reference.Source.HOUSE,
                        "Every measure here is a proxy. The literature that quantifies layout "
                                + "aesthetics -- balance, symmetry, density, regularity and the "
                                + "rest -- reports modest correlations with judged quality, which "
                                + "is worth having and is not the same as measuring quality. A page "
                                + "can satisfy every rule in this tool and be the wrong page.",
                        "People, looking at it, with a task."),

                new OutOfScope("The human-centred design process itself",
                        Reference.Source.CPUX_F,
                        "ISO 9241-210 describes how to run a project, not what a page should look "
                                + "like. No artifact check can tell you whether users were "
                                + "involved.",
                        "Nothing in this category is automatable. It is a way of working."));
    }
}
