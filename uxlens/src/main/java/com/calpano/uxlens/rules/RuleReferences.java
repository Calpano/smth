package com.calpano.uxlens.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each rule gets its authority, in one place.
 *
 * <p>A team arguing with a threshold deserves to know what they are arguing with. Most of
 * these rules rest on a standard, on the Gestalt principles or on the typographic canon;
 * some rest on nothing but this project's judgement, and those say {@link
 * Reference.Source#HOUSE} rather than dressing themselves up.
 *
 * <p>{@code RuleReferenceTest} requires every rule to be listed, so a rule cannot be added
 * without someone deciding what it is grounded in -- which, for a rule mined out of a book,
 * is the question worth forcing.
 */
public final class RuleReferences {

    private static final Map<String, List<Reference>> BY_RULE = new LinkedHashMap<>();

    private RuleReferences() { }

    static {
        BY_RULE.put("horizontal-overflow", List.of(
                Reference.of(Reference.Source.WCAG, "1.4.10 Reflow", "Content must not require scrolling in two directions.")));
        BY_RULE.put("clipped-text", List.of(
                Reference.of(Reference.Source.ISO_9241_112, null, "Information the user needs must be detectable; painting it outside a hidden box is not presenting it."),
                Reference.of(Reference.Source.WCAG, "1.4.4 Resize Text", "Content must survive being resized without loss.")));
        BY_RULE.put("off-viewport", List.of(
                Reference.of(Reference.Source.WCAG, "1.4.10 Reflow", "Content must not require scrolling in two directions.")));
        BY_RULE.put("text-overlap", List.of(
                Reference.of(Reference.Source.ISO_9241_125, null, "Legibility requires that characters not be obscured by other content.")));
        BY_RULE.put("touch-target", List.of(
                Reference.of(Reference.Source.WCAG, "2.5.8 Target Size (Minimum)", "Pointer targets are at least 24 by 24 CSS pixels."),
                Reference.of(Reference.Source.WCAG, "2.5.5 Target Size (Enhanced)", "At AAA the figure rises to 44 by 44 CSS pixels."),
                Reference.of(Reference.Source.PLATFORM, null, "Apple HIG asks for 44pt and Material for 48dp, which is where the touch figure comes from.")));
        BY_RULE.put("edge-alignment", List.of(
                Reference.of(Reference.Source.ISO_9241_125, null, "Aligning related items on a common edge is one of the standard grouping devices."),
                Reference.of(Reference.Source.GESTALT, "continuity", "The eye follows the line formed by aligned edges, and notices where it breaks.")));
        BY_RULE.put("baseline-drift", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Type aligns on its baseline, not on the box around it.")));
        BY_RULE.put("row-center-drift", List.of(
                Reference.of(Reference.Source.ISO_9241_125, null, "Items presented as a row are aligned on a common axis.")));
        BY_RULE.put("page-margin", List.of(
                Reference.of(Reference.Source.GESTALT, "continuity", "A consistent margin reads as the edge of the page; one section inset differently breaks it.")));
        BY_RULE.put("gap-consistency", List.of(
                Reference.of(Reference.Source.GESTALT, "proximity", "Equal spacing is what makes a set read as a set."),
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way.")));
        BY_RULE.put("spacing-scale", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way."),
                Reference.of(Reference.Source.HOUSE, null, "Inferring the scale from the page rather than imposing one is this project’s approach.")));
        BY_RULE.put("flat-type-hierarchy", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Hierarchy needs a clear step between levels; a modular scale is the usual device."),
                Reference.of(Reference.Source.ISO_9241_125, null, "Coding by size is only coding if the sizes differ perceptibly.")));
        BY_RULE.put("noisy-type-scale", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "A scale is a few deliberate steps, not every size somebody reached for.")));
        BY_RULE.put("font-family-sprawl", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "A text face and a display face is the working limit for one page.")));
        BY_RULE.put("measure", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "The comfortable measure is roughly 45 to 75 characters a line; past about 85 the return sweep fails.")));
        BY_RULE.put("leading", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Body copy needs 1.4 to 1.6 leading to be trackable."),
                Reference.of(Reference.Source.WCAG, "1.4.12 Text Spacing", "Content must survive a line height of 1.5 times the font size.")));
        BY_RULE.put("orphan-line", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "A single short word alone on a last line is the first thing a typesetter fixes.")));
        BY_RULE.put("all-caps-run", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Capitals remove the word shapes readers recognise, so reading slows markedly past a few words.")));
        BY_RULE.put("text-contrast", List.of(
                Reference.of(Reference.Source.WCAG, "1.4.3 Contrast (Minimum)", "4.5:1 for normal text, 3:1 for large."),
                Reference.of(Reference.Source.WCAG, "1.4.6 Contrast (Enhanced)", "7:1 and 4.5:1 at AAA.")));
        BY_RULE.put("control-border-contrast", List.of(
                Reference.of(Reference.Source.WCAG, "1.4.11 Non-text Contrast", "Parts of a control that convey its shape need 3:1.")));
        BY_RULE.put("palette-sprawl", List.of(
                Reference.of(Reference.Source.ISO_9241_125, null, "Colour coding only works while the number of codes stays small."),
                Reference.of(Reference.Source.HOUSE, null, "The threshold of sixteen distinct colours is ours.")));
        BY_RULE.put("near-duplicate-colors", List.of(
                Reference.of(Reference.Source.HOUSE, null, "Two values a reader cannot tell apart are one colour; the distance threshold is ours.")));
        BY_RULE.put("competing-focal-points", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "detectability", "The user must be able to find what matters; two equal focal points means neither is found first."),
                Reference.of(Reference.Source.HOUSE, null, "Scoring visual weight from the render, and the 92% threshold, are ours.")));
        BY_RULE.put("inverted-hierarchy", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "detectability", "What the page emphasises should be what matters."),
                Reference.of(Reference.Source.HOUSE, null, "Comparing measured prominence against the document outline is ours.")));
        BY_RULE.put("primary-action-not-salient", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "detectability", "The primary action must be findable without reading the page."),
                Reference.of(Reference.Source.HOUSE, null, "Saliency modelling and the thresholds are ours.")));
        BY_RULE.put("squint-cohesion", List.of(
                Reference.of(Reference.Source.GESTALT, "figure/ground", "A page needs figures that separate from their ground, and groups that read as groups."),
                Reference.of(Reference.Source.HOUSE, null, "Automating the squint test, and the blob counts, are ours.")));
        BY_RULE.put("label-proximity", List.of(
                Reference.of(Reference.Source.GESTALT, "proximity", "Readers group by distance before they read; a label belongs to whatever it sits nearest."),
                Reference.of(Reference.Source.ISO_9241_143, null, "A form label must be unambiguously associated with its field, visually as well as programmatically.")));
        BY_RULE.put("orphaned-heading", List.of(
                Reference.of(Reference.Source.GESTALT, "proximity", "A heading is a lid, not a floor: the space above it must exceed the space below.")));
        BY_RULE.put("upscaled-image", List.of(
                Reference.of(Reference.Source.HOUSE, null, "The upscale threshold is ours; the underlying point is that soft imagery lowers perceived quality.")));
        BY_RULE.put("distorted-image", List.of(
                Reference.of(Reference.Source.HOUSE, null, "Aspect distortion is obvious on anything with a known shape. The 2% tolerance is ours.")));
        BY_RULE.put("broken-image", List.of(
                Reference.of(Reference.Source.HOUSE, null, "A broken image undoes more perceived quality than any amount of polish adds.")));
        BY_RULE.put("missing-alt", List.of(
                Reference.of(Reference.Source.WCAG, "1.1.1 Non-text Content", "Non-text content has a text alternative, or is marked decorative.")));
        BY_RULE.put("consistency-shape", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way, throughout."),
                Reference.of(Reference.Source.GESTALT, "similarity", "Things that look alike are read as belonging together, so things that belong together must look alike."),
                Reference.of(Reference.Source.HOUSE, null, "Shape drift is judged against the page’s own majority, and only near misses are reported. Both choices are ours.")));
        BY_RULE.put("consistency-typography", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way, throughout."),
                Reference.of(Reference.Source.GESTALT, "similarity", "Things that look alike are read as belonging together, so things that belong together must look alike."),
                Reference.of(Reference.Source.HOUSE, null, "Type drift is judged against the page’s own majority, and only near misses are reported. Both choices are ours.")));
        BY_RULE.put("consistency-color", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way, throughout."),
                Reference.of(Reference.Source.GESTALT, "similarity", "Things that look alike are read as belonging together, so things that belong together must look alike."),
                Reference.of(Reference.Source.HOUSE, null, "Colour drift is judged against the page’s own majority, and only near misses are reported. Both choices are ours.")));
        BY_RULE.put("consistency-spacing", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way, throughout."),
                Reference.of(Reference.Source.GESTALT, "similarity", "Things that look alike are read as belonging together, so things that belong together must look alike."),
                Reference.of(Reference.Source.HOUSE, null, "Padding drift is judged against the page’s own majority, and only near misses are reported. Both choices are ours.")));
        BY_RULE.put("consistency-size", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way, throughout."),
                Reference.of(Reference.Source.GESTALT, "similarity", "Things that look alike are read as belonging together, so things that belong together must look alike."),
                Reference.of(Reference.Source.HOUSE, null, "Icon size drift is judged against the page’s own majority, and only near misses are reported. Both choices are ours.")));
        BY_RULE.put("consistency-motion", List.of(
                Reference.of(Reference.Source.ISO_9241_112, "consistency", "Like information is presented in a like way, throughout."),
                Reference.of(Reference.Source.GESTALT, "similarity", "Things that look alike are read as belonging together, so things that belong together must look alike."),
                Reference.of(Reference.Source.HOUSE, null, "Transition timing drift is judged against the page’s own majority, and only near misses are reported. Both choices are ours.")));
        BY_RULE.put("link-not-distinguishable", List.of(
                Reference.of(Reference.Source.WCAG, "1.4.1 Use of Color", "Colour must not be the only visual means of conveying information."),
                Reference.of(Reference.Source.WCAG, "F73", "Named failure: links in text distinguishable by colour alone need 3:1 against that text.")));
        BY_RULE.put("false-affordance", List.of(
                Reference.of(Reference.Source.ISO_9241_110, "conformity with user expectations", "A control must behave as its appearance leads the user to expect.")));
        BY_RULE.put("destructive-adjacency", List.of(
                Reference.of(Reference.Source.FITTS, null, "Acquisition is imprecise; the cost of a miss should set the spacing."),
                Reference.of(Reference.Source.ISO_9241_110, "use error robustness", "The design should make serious use errors hard to commit.")));
        BY_RULE.put("optical-centering", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Optical centring, not geometric: an asymmetric shape needs an asymmetric inset to look centred.")));
        BY_RULE.put("flat-grouping-hierarchy", List.of(
                Reference.of(Reference.Source.GESTALT, "proximity", "Grouping is conveyed by relative distance, so the levels must differ."),
                Reference.of(Reference.Source.ISO_9241_112, null, "Structure in the information should be visible in its presentation."),
                Reference.of(Reference.Source.HOUSE, null, "The 1.5x ratio is ours.")));
        BY_RULE.put("no-tonal-anchor", List.of(
                Reference.of(Reference.Source.HOUSE, null, "Entirely ours. Every element can clear the contrast minimum and the page still be tonally dead; the 7:1 peak is a judgement call.")));
        BY_RULE.put("centred-body-copy", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Centred running text has no left edge for the return sweep to find.")));
        BY_RULE.put("cramped-prose", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null, "Space between blocks should scale with the type, not be a number chosen once."),
                Reference.of(Reference.Source.HOUSE, null, "The half-the-type-size floor is ours.")));
        BY_RULE.put("invisible-surface", List.of(
                Reference.of(Reference.Source.GESTALT, "figure/ground",
                        "A figure must separate from its ground to be seen as a figure at all."),
                Reference.of(Reference.Source.ISO_9241_125, null,
                        "Grouping by a common region requires the region to be perceptible."),
                Reference.of(Reference.Source.HOUSE, null,
                        "The 1.08:1 separation threshold is ours.")));
        BY_RULE.put("minimum-text-size", List.of(
                Reference.of(Reference.Source.PLATFORM, null,
                        "Platform guidance converges on about 11pt as a floor for text meant to be read."),
                Reference.of(Reference.Source.ISO_9241_125, null,
                        "Legibility depends on character size as well as on contrast."),
                Reference.of(Reference.Source.HOUSE, null,
                        "The 10px and 12px floors are ours.")));
        BY_RULE.put("mixed-label-placement", List.of(
                Reference.of(Reference.Source.ISO_9241_143, null,
                        "Label placement within a form should be consistent so the association is learned once."),
                Reference.of(Reference.Source.ISO_9241_112, "consistency",
                        "Like information is presented in a like way.")));
        BY_RULE.put("leading-for-measure", List.of(
                Reference.of(Reference.Source.TYPOGRAPHIC_CANON, null,
                        "Leading and measure are one decision: the longer the line, the more leading the return sweep needs."),
                Reference.of(Reference.Source.HOUSE, null,
                        "The 60-character and 1.5 thresholds are ours.")));
    }

    /** What this rule is grounded in. Empty when the rule is not registered. */
    public static List<Reference> forRule(String ruleId) {
        return BY_RULE.getOrDefault(ruleId, List.of());
    }

    /** Every rule id with registered references. */
    public static java.util.Set<String> covered() { return BY_RULE.keySet(); }

    /** Rules grounded in nothing but this project's judgement. */
    public static List<String> houseOnly() {
        return BY_RULE.entrySet().stream()
                .filter(e -> e.getValue().stream().allMatch(ref -> ref.source().isHouse()))
                .map(Map.Entry::getKey).toList();
    }

    /** Rule ids grounded in a given source, in registration order. */
    public static List<String> bySource(Reference.Source source) {
        return BY_RULE.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(ref -> ref.source() == source))
                .map(Map.Entry::getKey).toList();
    }
}
