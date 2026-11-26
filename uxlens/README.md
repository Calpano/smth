# uxlens

Automated visual and UX review of rendered web pages, in Java.

A browser renders the page; uxlens measures what it painted and reports what a graphics
professional would circle in red pen — misaligned edges, uneven rhythm, a flat type scale,
unreadable contrast, a call to action nobody's eye lands on.

It is **not** a screenshot-diff tool and **not** an accessibility linter, though it
overlaps with both. The question it answers is *"would a designer sign this off?"*, and it
answers it with measurements rather than opinions.

---

## Why this exists

Two things are hard to automate about visual quality, and uxlens addresses both.

**Near-miss defects are invisible to a vision model and obvious to arithmetic.** A
multimodal model cannot reliably tell 120px from 122px in a screenshot — it has no
reference points. `getBoundingClientRect` can, every time. So alignment, rhythm, measure
and contrast are computed, not judged.

**Perceived hierarchy is invisible to the DOM and obvious to the pixels.** The markup says
`<h1>` before `<p>`; it does not say that the orange promo banner out-shouts the headline,
or that the primary action is the twelfth loudest thing on the page. So uxlens computes a
saliency map, a predicted gaze path, a blurred "squint test" render, and a per-element
visual-prominence ranking from the rendered image.

Those two halves meet in the output: a set of findings that each name an element, state the
measurement that triggered them, and propose a change — plus a set of images designed to be
handed to an LLM agent alongside them.

---

## Quick start

```bash
mvn -q package

# review a page at two viewports, write every artifact, fail CI on anything serious
java -jar target/uxlens-0.1.0-SNAPSHOT.jar audit https://example.com \
     --viewport desktop,mobile --out target/uxlens --fail-on medium
```

```
=== desktop (1440x900) -- 42 elements, 6 finding(s) ===

[HIGH  ] text-contrast     p "Save 20% on annual plans" has a contrast ratio of 1.92:1
                           against its background (#bbbbbb on #ffffff); 4.5:1 is required
                           for normal text at 16px/400.
           at: body > main > p:nth-of-type(2)
           fix: Use #707070 (4.94:1) or darker. Alternatively set it at 24px or bold 19px,
                where 3.0:1 suffices.

[MEDIUM] edge-alignment    Left edge of div "Second row" is 3.0px off the 232px line that
                           3 siblings share.
           at: body > div > div:nth-of-type(2)
           fix: Set the left edge to 232px. A stray padding, border or margin of 3.0px is
                the usual cause -- let the shared container own the inset instead.
```

Exit codes: `0` clean, `1` findings at or above `--fail-on`, `2` bad usage, `3` error.

### As a test

```java
@ExtendWith(SharedProbe.class)   // one Chromium for the whole run
class LandingPageVisualTest {

    @Test
    void landing_page_survives_design_review(Auditor auditor) {
        Report report = auditor
                .viewports(Viewport.DESKTOP, Viewport.MOBILE)
                .audit("http://localhost:8080/");

        UxAssert.that(report)
                .hasNoBrokenLayout()
                .allTextMeetsContrast()
                .hasClearVisualHierarchy()
                .hasNothingAtOrAbove(Severity.MEDIUM);
    }
}
```

A failure prints the measurement and the fix, not `expected true but was false`:

```
Visual QA: 2 finding(s) at or above MEDIUM.

  [MEDIUM] gap-consistency
    Horizontal gaps in this group of 4 run [24, 24, 17]; the gap between div "Card three"
    and div "Card four" is 17px against a median of 24px.
    at: body > div > div:nth-of-type(3), body > div > div:nth-of-type(4)
    fix: Set one gap:24px on body > div and drop the per-item margins.
    measured: gaps=[24.0, 24.0, 17.0] median=24.0 worstDeviationPx=7.0
```

Checking one component in isolation needs no server:

```java
Report report = auditor.viewports(Viewport.MOBILE).auditHtml("""
        <div class="card">…</div>
        """);
```

---

## What it measures

32 rules across eight families. `uxlens rules` prints each with its rationale.

| Family | Rules |
|---|---|
| **Layout** | `horizontal-overflow` `clipped-text` `off-viewport` `text-overlap` `touch-target` |
| **Alignment** | `edge-alignment` `baseline-drift` `row-center-drift` `page-margin` |
| **Rhythm** | `gap-consistency` `spacing-scale` |
| **Typography** | `flat-type-hierarchy` `noisy-type-scale` `font-family-sprawl` `measure` `leading` `orphan-line` `all-caps-run` |
| **Colour** | `text-contrast` `control-border-contrast` `palette-sprawl` `near-duplicate-colors` |
| **Hierarchy** | `competing-focal-points` `inverted-hierarchy` `primary-action-not-salient` `squint-cohesion` |
| **Proximity** | `label-proximity` `orphaned-heading` |
| **Images** | `upscaled-image` `distorted-image` `broken-image` `missing-alt` |

Plus `focus-not-visible`, which drives the page rather than reading it — see below.

### Three things worth calling out

**Spacing and type are judged against the page's own system, not an external ideal.** The
base unit is *inferred* from what was rendered: a page built on 5px units is judged on 5px
units and reports nothing. Only values that break the page's own scale are findings.

**Contrast is measured against what is actually painted.** Backgrounds are composited
through transparent ancestors, so a 35%-opacity label on a tinted card is measured as the
colour it really is — not as either declaration says. 50% black text on white is 3.98:1 and
fails AA; reading the stylesheet tells you 21:1.

**Near-misses only.** A 2px offset is a bug; a 40px offset is a layout decision. Every
alignment rule reports offsets *inside* a tolerance window and stays silent outside it.

---

## The perceptual layer

This is the part that cannot be done from the DOM, and the part an LLM cannot do by looking
at a screenshot.

| | |
|---|---|
| **Saliency** | Spectral-residual saliency (Hou & Zhang, CVPR 2007) over the rendered pixels: what is *statistically surprising* in the image, which is what pre-attentive vision is drawn to. No model weights, a handful of FFTs. |
| **Gaze path** | Winner-take-all over the saliency map with inhibition of return, suppressing each element once it has been looked at — so the path visits distinct things rather than re-reading one wide headline. |
| **Squint test** | Grayscale and heavily blurred. Strips the vision encoder of text it would otherwise read instead of looking, leaving Gestalt grouping and contrast islands. A model that is bad at judging a crisp screenshot is markedly better at judging this one. |
| **Prominence** | Every element ranked by measured visual weight — type weight, area, contrast, saturation, surrounding whitespace, position and pixel saliency, each normalised across the page. This ranking is then compared against the hierarchy the markup claims. |
| **Grid & Set-of-Mark** | A labelled measuring grid, and every element outlined and numbered against a JSON table of exact rectangles — so a model reads "#14 starts at x=122, #12 at x=120" instead of estimating. |

> **A note on the saliency implementation.** The textbook formulation takes
> `log(amplitude + 1e-9)`. UI screenshots have large flat regions, so many frequency bins
> are *exactly* zero, and that epsilon turns each into a `-20.7` outlier that swamps every
> real value — the resulting map describes nothing but the floor. Photographs, which the
> method was derived on, never contain exact zeros. uxlens uses a floor relative to the
> spectrum and expresses the residual multiplicatively, so a zero bin stays zero.

---

## Artifacts

With `--out DIR`, each viewport gets a folder:

```
target/uxlens/
├── report.html          findings beside the images they are about
├── report.json          the machine-readable form
├── briefing.md          written for an LLM agent (see below)
└── desktop/
    ├── screenshot.png   the whole scrollable page
    ├── fold.png         the first viewport
    ├── squint.png       blurred and desaturated — judge hierarchy from this
    ├── saliency.png     predicted attention as a heat map
    ├── scanpath.png     numbered gaze order, 1 first
    ├── grid.png         labelled measuring grid
    ├── set-of-mark.png  every element outlined and numbered
    └── findings.png     each finding boxed and labelled with its rule id
```

### briefing.md

The report rewritten for an agent that is about to edit the CSS. It states the
measurements as facts rather than asking the model to perceive them, names each image and
says what it is for, gives the prominence table and the gaze order, and ends with a
checklist that constrains the critique to questions with answers — *"check whether the left
edges of these three elements agree"* rather than *"does this look good"*.

Hand it to a coding agent together with the images:

```bash
java -jar target/uxlens-*.jar audit http://localhost:3000 --out .uxlens --quiet
claude -p "Fix what $(pwd)/.uxlens/briefing.md reports. Re-run uxlens when done."
```

---

## Focus visibility

Whether a keyboard focus ring is actually *visible* cannot be answered from a stylesheet: a
ring can be declared and then painted outside a clipping ancestor, or overridden three
stylesheets later, or removed by a reset nobody noticed. So each control is focused for
real and the pixels around it are compared before and after.

```bash
java -jar target/uxlens-*.jar focus http://localhost:3000
```

```java
List<Finding> findings = FocusAudit.run(probe, url, Viewport.DESKTOP);
```

---

## Visual regression

```bash
java -jar target/uxlens-*.jar diff http://localhost:3000 https://production.example.com \
     --viewport desktop --out target/diff
```

Reports a pixel-change ratio with a perceptual tolerance, an SSIM score, and a
*layout similarity* — SSIM after a heavy blur, which drops only when blocks actually move.
That separates "the copy changed" from "the layout broke".

---

## Configuration

Every threshold is a judgement call a design team may disagree with, so every threshold is
configurable.

```java
Config config = Config.defaults();
config.alignmentTolerancePx = 2;        // only report offsets under 2px
config.minHeadingToBodyRatio = 1.6;     // demand a louder headline
config.aaa();                           // WCAG AAA instead of AA
config.disable("all-caps-run", "palette-sprawl");

auditor.config(config);
```

From the CLI: `--aaa`, `--only id,id`, `--skip id,id`, `--fail-on`, `--grid N`.

To ratchet an existing codebase instead of fixing it all at once:

```java
UxAssert.that(report).hasAtMost(12, Severity.MEDIUM);
```

---

## Requirements

- Java 21
- Chromium, via Playwright for Java

The browser is downloaded on first use. In a container that already has one, point at it:

```bash
export PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers
```

Otherwise:

```bash
mvn exec:java -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

---

## Design notes

**The browser is launched once.** Launching Chromium costs about a second; everything else
costs milliseconds. `SharedProbe` holds one browser for the whole JUnit run.

**The analysis is computed once per viewport** — an FFT, a blur, an O(n²) isolation pass —
and shared by all 32 rules, which are each cheap.

**A rule that throws becomes a finding**, not a failed audit. One broken check must not
hide the other thirty-one.

**No finding without a fix.** Tested: every finding carries a message stating its
measurement and a suggestion naming what to change. Contrast findings go further and
compute a replacement colour that would pass.

**False positives are the thing to fear.** A tool that cries wolf gets ignored, so
`src/test/resources/pages/clean.html` — a deliberately well-built page — must produce
**zero** findings at every severity and every viewport, and a test enforces it. Its sibling
`broken.html` is annotated block by block with the rule each defect exists to trigger, and
31 of the 32 rules are covered by it (the 32nd is covered by direct unit tests).

```bash
PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers mvn test     # 140 tests
```

---

## Layout

```
com.calpano.uxlens
├── Auditor               orchestrates: measure → analyse → check → report
├── browser/              Playwright, viewports, and probe.js
├── model/                Rect, Rgba, Elem, PageSnapshot, Finding, Report
├── perception/           Gray, Fft, Saliency, SquintTest, Overlays, VisualDiff
├── analysis/             Prominence, SpacingSystem, TypeSystem, VisualGroups, Config
├── rules/                the 32 checks, by family
├── interactive/          FocusAudit — drives the page rather than reading it
├── report/               JSON, HTML, AgentBriefing
├── junit/                SharedProbe, UxAssert
└── cli/                  Main
```

`browser/probe.js` runs inside the page and returns one record per visually relevant
element. Everything derivable from pure geometry is deliberately *not* computed there —
Java does that, where it is testable.
