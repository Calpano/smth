package com.calpano.uxlens.rules;

/**
 * Where a rule's authority comes from.
 *
 * <p>A design team arguing with a threshold deserves to know whether they are arguing with
 * a standard, with a century of typographic practice, or with this project's opinion. All
 * three are legitimate; pretending the third is one of the first two is not. So {@link
 * Source#HOUSE} exists and is used honestly, and a rule carrying only a HOUSE reference is
 * saying plainly that the number is a judgement call.
 *
 * <p>Clause numbers are given only where they can be given exactly -- WCAG success criteria,
 * which are stable and precisely numbered. ISO parts are cited by part and by the named
 * principle they address, because citing a clause number approximately is worse than not
 * citing one at all.
 *
 * @param source  the body of knowledge
 * @param clause  the exact identifier within it, or null when none can be given exactly
 * @param says    what it says, in one line, in this project's words
 */
public record Reference(Source source, String clause, String says) {

    public static Reference of(Source source, String clause, String says) {
        return new Reference(source, clause, says);
    }

    public static Reference of(Source source, String says) {
        return new Reference(source, null, says);
    }

    /** A recognised body of knowledge, or the absence of one. */
    public enum Source {

        /** Web Content Accessibility Guidelines 2.2. Precisely numbered, so cited precisely. */
        WCAG("WCAG 2.2", "https://www.w3.org/TR/WCAG22/"),

        /** ISO 9241-112, principles for the presentation of information. */
        ISO_9241_112("ISO 9241-112 (presentation of information)", null),

        /** ISO 9241-125, guidance on the visual presentation of information. */
        ISO_9241_125("ISO 9241-125 (visual presentation)", null),

        /** ISO 9241-143, forms. */
        ISO_9241_143("ISO 9241-143 (forms)", null),

        /** ISO 9241-110, interaction principles -- chiefly conformity with user expectations. */
        ISO_9241_110("ISO 9241-110 (interaction principles)", null),

        /** The Gestalt grouping principles: proximity, similarity, common region, figure/ground. */
        GESTALT("Gestalt grouping principles", null),

        /** Fitts's law: acquisition time grows with distance and shrinks with target size. */
        FITTS("Fitts's law", null),

        /** The typographic canon: Bringhurst, Tschichold, and what every type shop teaches. */
        TYPOGRAPHIC_CANON("Typographic canon (Bringhurst and others)", null),

        /** Platform human-interface guidance: Apple HIG, Material, Fluent, GOV.UK. */
        PLATFORM("Platform guidelines (HIG / Material / GOV.UK)", null),

        /** The UXQB CPUX-F body of knowledge, the European foundation-level UX curriculum. */
        CPUX_F("UXQB CPUX-F", "https://uxqb.org/en/documents/"),

        /**
         * No external authority: a judgement this project makes.
         *
         * <p>Used deliberately rather than avoided. A rule resting on nothing but our own
         * taste should say so, so that it is the first one a team argues with.
         */
        HOUSE("uxlens (no external authority)", null);

        private final String title;
        private final String url;

        Source(String title, String url) {
            this.title = title;
            this.url = url;
        }

        public String title() { return title; }

        /** A public link, where the source has one that is free to read. */
        public java.util.Optional<String> url() { return java.util.Optional.ofNullable(url); }

        /** True when the rule's authority is nothing but this project's opinion. */
        public boolean isHouse() { return this == HOUSE; }
    }

    @Override public String toString() {
        return source.title() + (clause == null ? "" : " " + clause) + ": " + says;
    }
}
