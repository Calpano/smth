package com.calpano.uxlens.rules;

import com.calpano.uxlens.browser.Viewport;

/**
 * Proof that a rule discriminates.
 *
 * <p>A rule is only a rule if it can tell a page that violates it from one that does not.
 * Without that, a plausible-sounding principle -- "establish a clear hierarchy", "respect
 * the grid" -- turns into a check that either fires on everything or fires on nothing, and
 * both are worse than having no check.
 *
 * <p>This matters most for rules that come from outside: a principle lifted from a book or
 * a style guide arrives as prose, and prose always sounds checkable. The contract is the
 * gate. Write the two pages first; if you cannot write a page that conforms, the rule is
 * unfalsifiable, and if you cannot write one that violates, it is unreachable. Either way
 * it does not get added.
 *
 * @param violates a minimal page the rule must fire on
 * @param conforms the same page with the defect fixed, which the rule must stay silent on
 * @param viewport the viewport both are rendered at
 */
public record RuleContract(String violates, String conforms, Viewport viewport) {

    public static RuleContract of(String violates, String conforms) {
        return new RuleContract(violates, conforms, Viewport.DESKTOP);
    }

    public static RuleContract of(String violates, String conforms, Viewport viewport) {
        return new RuleContract(violates, conforms, viewport);
    }
}
