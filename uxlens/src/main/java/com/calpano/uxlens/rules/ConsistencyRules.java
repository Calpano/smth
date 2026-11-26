package com.calpano.uxlens.rules;

import com.calpano.uxlens.analysis.Analysis;
import com.calpano.uxlens.analysis.Consistency;
import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Inconsistency within the page's own vocabulary.
 *
 * <p>These rules know nothing about design. They know that things playing the same part
 * should look the same, and that the page itself says what that look is. One engine
 * ({@link Consistency}) mines every near-miss; these rules are the reporting faces of it,
 * split by property family so a team can silence corner radii without silencing colour.
 */
public final class ConsistencyRules {

    private ConsistencyRules() { }

    public static List<Rule> all() {
        return List.of(
                new Family("consistency-shape", "Shape differs between things that match",
                        "Corner radius and border weight are the vocabulary of a surface. One "
                                + "card rounded 6px among 8px siblings is not a variant, it is a "
                                + "typo -- and it is exactly the kind nobody spots by eye."),
                new Family("consistency-typography", "Type differs between things that match",
                        "A cohort set at 15px among 16px siblings, or one label in a different "
                                + "face, reads as carelessness rather than as emphasis. Real "
                                + "emphasis is a clear step, and a clear step is left alone here."),
                new Family("consistency-color", "Colour differs between things that match",
                        "Two shades of the same grey are one colour as far as any reader is "
                                + "concerned, and two values as far as anyone maintaining it is. "
                                + "A genuinely different colour -- an active tab, a danger button "
                                + "-- is a decision and is not reported."),
                new Family("consistency-spacing", "Padding differs between things that match",
                        "Uneven internal padding makes a row of cards look subtly broken while "
                                + "every individual card looks fine."),
                new Family("consistency-size", "Icon size differs between things that match",
                        "Icons in a set are read as one alphabet. One drawn two pixels larger "
                                + "pulls the eye for no reason."),
                new Family("consistency-motion", "Transition timing differs between controls",
                        "Controls that respond at different speeds feel like different software. "
                                + "Timing is part of the design system, even though it never "
                                + "appears in a mock-up."));
    }

    /** Reports the outliers the engine found for one family of properties. */
    public static final class Family implements Rule {

        /** Cap per family: a page that fails this wholesale needs a conversation, not a list. */
        private static final int MAX_REPORTED = 10;

        private final String id;
        private final String title;
        private final String rationale;

        Family(String id, String title, String rationale) {
            this.id = id;
            this.title = title;
            this.rationale = rationale;
        }

        @Override public String id() { return id; }

        @Override public String title() { return title; }

        @Override public String rationale() { return rationale; }

        @Override public List<Finding> check(Analysis a) {
            List<Consistency.Outlier> mine = a.consistencyOutliers().stream()
                    .filter(o -> o.property().ruleId().equals(id))
                    .toList();
            if (mine.isEmpty()) return List.of();

            // Several properties drifting on one element is one mistake, not four.
            Map<Integer, List<Consistency.Outlier>> byElement = mine.stream()
                    .collect(Collectors.groupingBy(o -> o.elem().id,
                            java.util.LinkedHashMap::new, Collectors.toList()));

            List<Finding> out = new ArrayList<>();
            for (List<Consistency.Outlier> group : byElement.values()) {
                if (out.size() >= MAX_REPORTED) break;
                Consistency.Outlier first = group.get(0);

                String drift = group.stream()
                        .map(o -> String.format("%s is %s where %d of its peers use %s",
                                o.property().name(), o.value(), o.majorityCount(), o.majority()))
                        .collect(Collectors.joining("; "));

                Finding f = finding(group.size() > 1 ? Severity.MEDIUM : Severity.LOW, String.format(
                        "%s is the odd one out among %s: %s.",
                        first.elem().label(), first.peers().signature(), drift))
                        .about(first.elem())
                        .with("peerKind", first.peers().kind())
                        .with("peerCount", first.peers().size())
                        .with("properties", group.stream().map(o -> Map.of(
                                "property", o.property().name(),
                                "value", o.value(),
                                "majority", o.majority(),
                                "majorityCount", o.majorityCount())).toList())
                        .suggest(group.stream().map(o -> o.property().suggest(o.majority()))
                                .distinct().collect(Collectors.joining(" ")));
                out.add(f);
            }
            return out;
        }
    }
}
