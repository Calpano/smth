package com.calpano.uxlens.report;

import com.calpano.uxlens.model.Finding;
import com.calpano.uxlens.model.Report;
import com.calpano.uxlens.model.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The report rewritten for an LLM agent that is about to edit the CSS.
 *
 * <p>Three things make this different from the JSON report. It states the measurements as
 * facts rather than asking the model to perceive them. It names the images to look at and
 * says what each one is for, so a multimodal model is not left squinting at a raw
 * screenshot. And it ends with a checklist that constrains the critique to questions with
 * answers -- "check whether the left edges of these three elements agree" rather than
 * "does this look good".
 */
public final class AgentBriefing {

    private AgentBriefing() { }

    public static Path write(Report report, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, render(report), StandardCharsets.UTF_8);
        return file;
    }

    public static String render(Report report) { return render(report, Severity.LOW); }

    public static String render(Report report, Severity min) {
        StringBuilder md = new StringBuilder();
        md.append("# Visual QA briefing\n\n");
        md.append("**Page:** ").append(report.url).append("  \n");
        md.append("**Measured:** ").append(report.generatedAt).append("\n\n");
        md.append("Everything below was measured in a real browser. The numbers are exact: "
                + "geometry comes from `getBoundingClientRect`, contrast from composited "
                + "colours, prominence and saliency from the rendered pixels. Do not re-derive "
                + "them from the screenshots -- use them.\n\n");

        for (Report.ViewportResult v : report.viewports) {
            md.append("---\n\n## Viewport: ").append(v.viewportName)
                    .append(" (").append(v.width).append("x").append(v.height).append(")\n\n");

            appendArtifacts(md, v);
            appendFindings(md, v, min);
            appendProminence(md, v);
            appendScanpath(md, v);
            appendTokens(md, v);
            appendProfile(md, v);
        }

        appendSchemeParity(md, report);
        appendChecklist(md);
        return md.toString();
    }

    private static void appendArtifacts(StringBuilder md, Report.ViewportResult v) {
        if (v.artifacts.isEmpty()) return;
        md.append("### Images to look at\n\n");
        for (Map.Entry<String, String> e : v.artifacts.entrySet()) {
            md.append("- `").append(e.getValue()).append("` -- ").append(purposeOf(e.getKey())).append('\n');
        }
        md.append('\n');
    }

    private static String purposeOf(String kind) {
        return switch (kind) {
            case "screenshot" -> "the whole scrollable page as rendered.";
            case "fold" -> "the first viewport: what a visitor sees before scrolling.";
            case "squint" -> "blurred and desaturated. Judge hierarchy and grouping from this one, "
                    + "not from the screenshot -- if a region is invisible here it is invisible in "
                    + "the first half-second.";
            case "saliency" -> "predicted visual attention as a heat map. Red is where the eye goes.";
            case "scanpath" -> "numbered gaze order, 1 first. Check that 1-3 land on what the page "
                    + "is for.";
            case "grid" -> "a measuring grid with labelled axes. Use it to read off coordinates "
                    + "instead of estimating them.";
            case "set-of-mark" -> "every element outlined and numbered with the ids used below, so "
                    + "a finding about element 14 can be located on screen.";
            case "findings" -> "each finding boxed and labelled with its rule id.";
            default -> "rendered artifact.";
        };
    }

    private static void appendFindings(StringBuilder md, Report.ViewportResult v, Severity min) {
        List<Finding> findings = v.findings.stream().filter(f -> f.severity.atLeast(min)).toList();
        md.append("### Findings (").append(findings.size()).append(")\n\n");
        if (findings.isEmpty()) {
            md.append("None at or above ").append(min).append(".\n\n");
            return;
        }
        for (Severity s : List.of(Severity.HIGH, Severity.MEDIUM, Severity.LOW, Severity.INFO)) {
            List<Finding> group = findings.stream().filter(f -> f.severity == s).toList();
            if (group.isEmpty()) continue;
            md.append("#### ").append(s).append('\n').append('\n');
            for (Finding f : group) {
                md.append("- **").append(f.ruleId).append("** -- ").append(f.message).append('\n');
                if (!f.selectors.isEmpty()) {
                    md.append("  - elements: ");
                    for (int i = 0; i < f.selectors.size(); i++) {
                        if (i > 0) md.append(", ");
                        md.append('`').append(f.selectors.get(i)).append('`');
                        if (i < f.elements.size()) md.append(" (#").append(f.elements.get(i)).append(')');
                    }
                    md.append('\n');
                }
                if (f.suggestion != null) md.append("  - fix: ").append(f.suggestion).append('\n');
                List<com.calpano.uxlens.rules.Reference> refs =
                        com.calpano.uxlens.rules.RuleReferences.forRule(f.ruleId);
                if (!refs.isEmpty()) {
                    md.append("  - grounded in: ");
                    for (int i = 0; i < refs.size(); i++) {
                        if (i > 0) md.append("; ");
                        md.append(refs.get(i));
                    }
                    md.append('\n');
                }
                if (!f.data.isEmpty()) md.append("  - measured: `").append(f.data).append("`\n");
            }
            md.append('\n');
        }
    }

    private static void appendProminence(StringBuilder md, Report.ViewportResult v) {
        if (v.prominence.isEmpty()) return;
        md.append("### Measured visual prominence\n\n");
        md.append("Ranked by how loudly each element shouts, computed from type weight, area, "
                + "contrast, saturation, surrounding whitespace, position and pixel saliency. "
                + "Compare this against the hierarchy the page is *supposed* to have.\n\n");
        md.append("| rank | element | text | score | size/weight | contrast | isolation | y% | fold |\n");
        md.append("|---:|---|---|---:|---|---:|---:|---:|---|\n");
        for (Report.Prominence p : v.prominence) {
            md.append("| ").append(p.rank)
                    .append(" | `").append(p.selector).append("` (#").append(p.elementId).append(')')
                    .append(" | ").append(truncate(p.text))
                    .append(" | ").append(String.format("%.3f", p.score))
                    .append(" | ").append(String.format("%.0f/%d", p.fontSizePx, p.fontWeight))
                    .append(" | ").append(String.format("%.2f", p.contrastRatio))
                    .append(" | ").append(String.format("%.0fpx", p.whitespaceMarginPx))
                    .append(" | ").append(String.format("%.0f%%", p.viewportYPercent))
                    .append(" | ").append(p.aboveFold ? "yes" : "no")
                    .append(" |\n");
        }
        md.append('\n');
    }

    private static void appendScanpath(StringBuilder md, Report.ViewportResult v) {
        if (v.focalPoints.isEmpty()) return;
        md.append("### Predicted gaze order\n\n");
        for (Report.FocalPoint f : v.focalPoints) {
            md.append(f.order).append(". (").append((int) f.x).append(", ").append((int) f.y).append(") ")
                    .append(String.format("strength %.2f", f.strength));
            if (f.selector != null) {
                md.append(" -- `").append(f.selector).append('`');
                if (f.text != null && !f.text.isBlank()) md.append(" \"").append(truncate(f.text)).append('"');
            } else {
                md.append(" -- no element at this point");
            }
            md.append('\n');
        }
        md.append('\n');
    }

    private static void appendTokens(StringBuilder md, Report.ViewportResult v) {
        if (v.inferredTokens.isEmpty()) return;
        md.append("### The design system this page actually uses\n\n");
        for (Map.Entry<String, Object> e : v.inferredTokens.entrySet()) {
            md.append("- **").append(e.getKey()).append("**: `").append(e.getValue()).append("`\n");
        }
        md.append("\nSpacing and type findings are measured against *this* inferred system, not "
                + "an external ideal -- a page built on 5px units is judged on 5px units.\n\n");
    }

    private static void appendProfile(StringBuilder md, Report.ViewportResult v) {
        if (v.profile.isEmpty()) return;
        md.append("### What kind of page this is\n\n");
        md.append("Measures, not faults. Nothing here passes or fails; the quantitative "
                + "literature on interface aesthetics reports that figures of this kind "
                + "correlate only modestly with judged quality. They are worth having for "
                + "comparison -- against this page's siblings, its previous version, or a "
                + "house norm -- and not worth treating as a score.\n\n");
        for (Map.Entry<String, Object> e : v.profile.entrySet()) {
            md.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append('\n');
        }
        md.append('\n');
    }

    private static void appendSchemeParity(StringBuilder md, Report report) {
        List<Report.SchemeParity> parity = report.schemeParity();
        if (parity.isEmpty()) return;
        md.append("---\n\n## Light against dark\n\n");
        md.append("A rule that fires in one scheme and not the other is a defect the other "
                + "scheme's reviewer cannot see.\n\n");
        for (Report.SchemeParity p : parity) {
            md.append("- **").append(p.viewport).append("**: ");
            if (p.isClean()) {
                md.append("the same rules fire in both schemes.\n");
            } else {
                if (!p.onlyInDark.isEmpty()) {
                    md.append("only in dark: `").append(String.join("`, `", p.onlyInDark)).append("`. ");
                }
                if (!p.onlyInLight.isEmpty()) {
                    md.append("only in light: `").append(String.join("`, `", p.onlyInLight)).append("`.");
                }
                md.append('\n');
            }
        }
        md.append('\n');
    }

    private static void appendChecklist(StringBuilder md) {
        md.append("---\n\n## What to check, and how\n\n");
        md.append("Work through these in order. Each is a question with an answer, not a matter "
                + "of taste. For anything the findings above already measured, trust the number.\n\n");
        md.append("""
                1. **Hierarchy.** From the squint image alone: what is the single loudest region?
                   Is it the thing the page is for? Does the prominence table agree with the
                   answer you give from the image? Where they disagree, the table is right about
                   weight and you are right about intent -- say which element should lead.
                2. **Entry point.** Do gaze positions 1-3 land on the headline and the primary
                   action? If a decorative image or a banner takes position 1, say what to quiet.
                3. **Alignment.** For each group in the findings, check the reported edge offsets.
                   Use the grid overlay to confirm; do not estimate from the plain screenshot.
                4. **Rhythm.** Are gaps between like items identical? Do spacing values sit on the
                   inferred scale? Report the container that should own the spacing.
                5. **Grouping.** Does every label sit nearer the field it labels? Does every heading
                   sit nearer the section it heads? In the squint image, do related items merge
                   into one blob and unrelated ones stay separate?
                6. **Typography.** Is the largest-to-body ratio enough to read as hierarchy? Any
                   line over 85 characters? Any multi-line body text tighter than 1.4 leading?
                   Any orphaned last lines?
                7. **Colour.** Every contrast failure is already measured with a passing colour
                   suggested -- apply those. Then ask whether the palette is a palette.
                8. **Breakage.** Clipped text, overlaps, horizontal overflow and small touch
                   targets are listed as HIGH. These are bugs, not opinions; fix them first.
                9. **Across viewports.** A finding present at one viewport and absent at another
                   is a responsive bug. Compare the viewport sections above before concluding.
               10. **Weigh the authority.** Each finding says what it is grounded in. A WCAG
                   criterion is not negotiable; a threshold marked as this project's own
                   judgement is, and if it does not suit the design, say so rather than
                   complying.

                ### How to report back

                For each change: the selector, the property, the old value, the new value, and
                which finding or checklist item it answers. If you decide a finding is wrong --
                the threshold does not suit this design -- say so and say why, rather than
                silently skipping it.
                """);
    }

    private static String truncate(String s) {
        if (s == null || s.isBlank()) return "-";
        String t = s.replace('|', '/').replace('\n', ' ').trim();
        return t.length() > 40 ? t.substring(0, 40) + "..." : t;
    }
}
