package com.calpano.uxlens.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One thing a designer would circle in red pen.
 *
 * <p>A finding is not an opinion: it names the elements, states the measurement that
 * triggered it, and says what to change. That is what makes it usable by an agent.
 */
public class Finding {

    public String ruleId;
    public Severity severity;
    /** One sentence, stating the measurement. */
    public String message;
    /** What to change, in terms a code-editing agent can act on. */
    public String suggestion;
    /** Element ids this finding is about, most relevant first. */
    public List<Integer> elements = new ArrayList<>();
    /** CSS selectors for the same elements, so the finding survives serialization. */
    public List<String> selectors = new ArrayList<>();
    /** The region to highlight in an overlay image. */
    public Rect focus;
    /** The raw numbers behind the message. */
    public Map<String, Object> data = new LinkedHashMap<>();

    public Finding() { }

    public Finding(String ruleId, Severity severity, String message) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
    }

    public Finding about(Elem... es) {
        for (Elem e : es) {
            elements.add(e.id);
            selectors.add(e.selector);
            focus = focus == null ? e.rect() : focus.union(e.rect());
        }
        return this;
    }

    public Finding at(Rect r) { this.focus = r; return this; }

    public Finding with(String key, Object value) { data.put(key, value); return this; }

    public Finding suggest(String s) { this.suggestion = s; return this; }

    @Override public String toString() {
        return "[" + severity + "] " + ruleId + ": " + message;
    }
}
