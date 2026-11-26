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

50 rules across thirteen families, plus two interaction-state checks that drive the page.
`uxlens rules` prints each with its rationale and the body of knowledge it rests on.

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
| **Consistency** | `consistency-shape` `consistency-typography` `consistency-color` `consistency-spacing` `consistency-size` `consistency-motion` |
| **Affordance** | `link-not-distinguishable` `false-affordance` `destructive-adjacency` |
| **Optical** | `optical-centering` |
| **System** | `flat-grouping-hierarchy` `no-tonal-anchor` `centred-body-copy` `cramped-prose` |
| **Body of knowledge** | `invisible-surface` `minimum-text-size` `mixed-label-placement` `leading-for-measure` |

Plus `focus-not-visible` and `hover-not-visible`, which drive the page rather than reading
it — see below.

### Four things worth calling out

**The page is its own specification.** The consistency family needs no outside knowledge at
all: nobody has to decide what a card's corner radius *ought* to be, because if seven cards
use 8px and the eighth uses 6px, the page already said. It mines sibling groups (the cards
in a row, the links in a nav) and role cohorts (every button on the page) for radius,
border, type, colour, padding, icon size and transition timing.

What keeps it quiet is the same discrimination the alignment rules use: **a defect is a
near miss**. A nav link in a *different* colour from its siblings is the active one; a nav
link two shades off is a mistake. So a clearly different value is read as a decision and
left alone — and, importantly, a legitimate variant does not consume the outlier budget, or
one red danger button in a row of blue ones would suppress the real finding beside it.

**But a consistent page can still be a bad one.** Consistency mining has a hard ceiling: it
finds deviations from the page's own vocabulary, and can never find a *bad* vocabulary. A
page can be flawlessly self-consistent and still be the one a designer looks at and says
*nothing leads*, *the sections don't read as sections*, *there's no black on this page*,
*stop centring the body copy*. Each of those is advice about the system, and each is
invisible to a check that measures variance.

So the **system** family measures *level* and *range* where the consistency family measures
variance — the gap between groups against the gap inside them, the strongest contrast
anywhere on the page, the share of running text that is centred, whitespace against type
size. These are necessarily more opinionated, so each states the number it used and is
easy to switch off.


**Spacing and type are judged against the page's own system, not an external ideal.** The
base unit is *inferred* from what was rendered: a page built on 5px units is judged on 5px
units and reports nothing. Only values that break the page's own scale are findings.

**Contrast is measured against what is actually painted.** Backgrounds are composited
through transparent ancestors, so a 35%-opacity label on a tinted card is measured as the
colour it really is — not as either declaration says. 50% black text on white is 3.98:1 and
fails AA; reading the stylesheet tells you 21:1.

**Near-misses only.** A 2px offset is a bug; a 40px offset is a layout decision. Every
alignment rule reports offsets *inside* a tolerance window and stays silent outside it.

**Pinned layers are not part of the document.** A `position:fixed` bar scrolls over every
paragraph on the page and means nothing by it, so the probe marks every element in a fixed
or sticky subtree — including the `static` children of a fixed ancestor, which report their
own position as `static` — and the document-space rules leave them alone.

**A wrapped inline element's bounding box is a shape it never paints.** `<a>` text that
wraps across two lines returns the *union* of its line boxes, a rectangle that swallows the
neighbours sharing those lines. Overlap comparisons skip those boxes rather than believe
them.

**Opacity counts.** A 30%-opacity watermark the size of a headline is not competing with
the headline, so prominence is scaled by rendered opacity — the product down the ancestor
chain, since `opacity` is not inherited as a value but is cumulative in rendering.

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
| **Optical centring** | Centre of *painted mass* against geometric centre, read from the render. CSS centres bounding boxes; eyes centre mass, so a play triangle centred by arithmetic looks pushed left. Nothing in the DOM can reveal this. |

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

## Interaction states

Whether a focus ring or a hover style is actually *visible* cannot be answered from a
stylesheet: either can be declared and then painted outside a clipping ancestor, overridden
three stylesheets later, or removed by a reset nobody noticed. So each control is focused
and hovered for real, and the pixels around it are compared before and after.

```bash
java -jar target/uxlens-*.jar states http://localhost:3000 --states focus,hover
```

```java
List<Finding> findings = StateAudit.run(probe, url, Viewport.DESKTOP);
```

Hover is only demanded of a pointer viewport — a phone has no pointer, so it cannot have a
hover affordance.

---

## Light against dark

Dark mode is usually bolted onto a light design, and the defects it introduces — a border
that vanishes into the surface, a muted grey that drops below contrast, a shadow that stops
separating anything — are invisible to anyone reviewing the light version.

```bash
java -jar target/uxlens-*.jar audit http://localhost:3000 --scheme light,dark
```

```
Colour scheme parity at desktop:
   only in dark:  text-contrast
```

Each viewport is audited once per scheme, and the report says which rules fired in one and
not the other. That difference is the bolted-on part.

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

## What it is grounded in

Every rule names its authority, and `uxlens standards` prints the map:

```
COVERED

uxlens (no external authority)                 22 rules
ISO 9241-112 (presentation of information)     14 rules
Gestalt grouping principles                    14 rules
Typographic canon (Bringhurst and others)      12 rules
WCAG 2.2                                        9 rules
ISO 9241-125 (visual presentation)              7 rules
ISO 9241-143 (forms)                            2 rules
ISO 9241-110 (interaction principles)           2 rules
Platform guidelines (HIG / Material / GOV.UK)   2 rules
Fitts's law                                     1 rule

RESTING ON NOTHING BUT THIS PROJECT'S JUDGEMENT (5)

   near-duplicate-colors, upscaled-image, distorted-image, broken-image, no-tonal-anchor

   These are the thresholds to argue with first.
```

Those numbers are worth reading carefully. **22 of 50 rules cite `HOUSE` as one of their
sources** — meaning that even where a rule rests on a standard, the *predicate* or the
*threshold* is usually ours. ISO 9241-112 says like information should be presented in a
like way; it does not say that a 6px corner radius among 8px siblings is a defect while a
red button among blue ones is not. That second part is the engineering, and it is ours.
Only five rules rest on nothing else at all, and those are named so they can be argued with
first.

A team arguing with a threshold deserves to know whether they are arguing with WCAG, with a
century of typographic practice, or with this project's opinion. All three are legitimate;
dressing the third as one of the first two is not — so `Source.HOUSE` exists and is used,
and a test fails if *no* rule admits to it, since that could not be true.

WCAG is cited by exact criterion number, because those are stable and a test enforces it.
ISO parts are cited by part and named principle rather than clause number: citing a clause
approximately is worse than not citing one.

### What it does *not* check

The second half of `uxlens standards` is the more useful one. Most of what a UX
professional knows cannot be read off a rendered page at one moment — whether the system
keeps the user informed, whether an error can be undone, whether the vocabulary matches the
user's, whether the task could take fewer steps. Each needs a task, a user, or both.

Ten such areas are listed with *why a render cannot answer it* and *what would*. For
example, on matching the system to the real world: **"whether the words match the user's
vocabulary is a question about the users, and this tool has never met them. It would also
be the easiest thing here to fake convincingly with a language model."**

That last point is the reason the list is published rather than quietly skipped. A model
will happily produce confident opinions about tone, information architecture and task flow,
in the same register as the measured findings and resting on nothing. Keeping the two apart
is the whole value of measuring in the first place.

The honest summary: this tool addresses the **presentation layer** thoroughly and the
**interaction layer** barely at all.

---

## Proving a rule discriminates

Every rule carries a **contract**: a pair of minimal pages, one it must fire on and one it
must stay silent on. `RuleContractTest` runs all 42 pairs, so a rule that quietly stops
working fails the build — and so does a rule added without a pair.

```java
RuleContracts.forRule("edge-alignment");   // the two pages, in RuleContracts
```

This is the gate that makes it safe to mine rules from outside sources. A principle lifted
from a style guide or a textbook always sounds checkable in prose; **writing the conforming
page is what reveals whether it is.** If no page can conform, the rule is unfalsifiable. If
none can violate, it is unreachable. Neither gets added.

Writing the 42 contracts immediately caught five problems — four fixtures that did not
demonstrate what they claimed, and one real bug (see the note on animation freezing below).

---

## Calibrating against real pages

Every threshold in `Config` starts life as somebody's taste. The only way to find out
whether a rule earns its place is to run it over pages nobody wrote for it and have a human
say, finding by finding, whether it was right.

```bash
uxlens corpus capture --dir corpus     # pin the measurements (not the URLs)
uxlens corpus run     --dir corpus     # list findings with stable keys
uxlens corpus score   --dir corpus --min-precision 0.8
```

```
rule                            fired  right  wrong  unsure  precision
touch-target                       33     19      0      14       100%
leading                            24      0      0       0          -
text-contrast                       8      8      0       0       100%
competing-focal-points              1      1      0       0       100%
```

A live site rewrites the answer every deploy, so the corpus stores the **snapshot**, not
the URL: gzipped JSON, enough for every rule working from geometry and computed style.
(A measured page compresses about thirtyfold — eleven pages come to under a megabyte
instead of eighteen. Nothing is lost: a 3.5MB JSON blob was never reviewable as a diff, and
what a pinned capture is for is reproducibility, not review.) Screenshots are optional
(`--with-images`) because they are far larger.

Pages you do not own are captured with **`--redact`**, which replaces letters case for case
and digits with zeros, leaving length, case, punctuation and all geometry untouched. A run
of capitals stays a run of capitals, so the rule that cares keeps working.
`destructive-adjacency` is the one rule reading literal words; it declares
`needsLiteralText()` and is *skipped* on a redacted capture rather than silently finding
nothing and looking like a pass.

Verdicts live in `labels.json`, keyed by capture + rule + selector so that rewording a
finding does not throw away the judgement somebody spent time forming. `UNSURE` is a
first-class verdict rather than being quietly rounded either way, and an unjudged rule never
fails the floor — nothing judged means nothing proven, which is not the same as something
failing.

`--min-precision` makes a precision regression fail a build the way a broken test does. This
matters more as the rule count grows: **the marginal rule has negative value if it fires
wrongly**, because it buries the findings that mattered and teaches the team to skim.

The seeded corpus is one site, which is a weak measurement — the mechanism and the ratchet
are the point. Add your own in `corpus/sites.json`.

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

From the CLI: `--aaa`, `--only id,id`, `--skip id,id`, `--fail-on`, `--grid N`,
`--scheme light,dark`.

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

**No rule may flood the report.** Volume is a correctness property of its own. A dense
application page produced 2998 findings from one rule and 2799 from another — individually
defensible, collectively unreadable, because the forty-ninth undersized tap target buries
the one clipped heading. Past `Config.maxFindingsPerRule` a rule reports its worst and says
how many more there were.

**No finding without a fix.** Tested: every finding carries a message stating its
measurement and a suggestion naming what to change. Contrast findings go further and
compute a replacement colour that would pass.

**Horizontal overflow is asked, not inferred.** Neither `scrollWidth` nor
`window.innerWidth` answers "does this fit the device width": in mobile emulation Chrome
widens *both* rather than letting the page pan, and a fixed element wider than the viewport
inflates `scrollWidth` while being clipped and costing the reader nothing. The rule measures
the widest element that actually lives in the document, and the probe separately measures
how far the viewport really moves by trying to scroll it.

**Luma is not relative luminance.** `Gray.luminanceOf` applies the Rec.709 weights to sRGB
values *as stored*; `Rgba.relativeLuminance()` linearises them per WCAG. A mid blue is 0.29
as luma and 0.10 as relative luminance. Mixing the two made an entire button surface
register as ink and silently defeated the optical-centring measurement. Pixels are measured
in luma, so declared colours are compared with `Rgba.luma()`.

**Freezing animations must not erase what the page declared.** The probe pauses animations
before measuring, and the obvious `transition: none !important` resets
`transition-duration` to `0s` — so the page's declared timing, which is part of its design
system, became unmeasurable. `transition-property: none` stops transitions just as dead and
leaves the timing readable.

**A caveat on full-page screenshots.** Chromium paints a fixed element once, at its
scroll-0 position, so in a full-page capture a pinned footer appears partway down the image
rather than at the bottom of every screen. The `fold.png` capture is the accurate one for
pinned UI; the geometry rules do not read either image for this, they read the fixed-layer
flag.

**False positives are the thing to fear.** A tool that cries wolf gets ignored, so
`src/test/resources/pages/clean.html` — a deliberately well-built page — must produce
**zero** findings at every severity and every viewport, and a test enforces it. Its sibling
`broken.html` is annotated block by block with the rule each defect exists to trigger, and
31 of the 32 rules are covered by it (the 32nd is covered by direct unit tests).

```bash
PLAYWRIGHT_BROWSERS_PATH=/opt/pw-browsers mvn test
```

---

## Layout

```
com.calpano.uxlens
├── Auditor               orchestrates: measure → analyse → check → report
├── browser/              Playwright, viewports, and probe.js
├── model/                Rect, Rgba, Elem, PageSnapshot, Finding, Report
├── perception/           Gray, Fft, Saliency, SquintTest, Overlays, VisualDiff
├── analysis/             Prominence, Consistency, SpacingSystem, TypeSystem, VisualGroups, Config
├── rules/                the 42 checks, by family, plus their contracts
├── corpus/               pinned captures, human labels, precision scoring
├── interactive/          StateAudit — drives the page rather than reading it
├── report/               JSON, HTML, AgentBriefing
├── junit/                SharedProbe, UxAssert
└── cli/                  Main
```

`browser/probe.js` runs inside the page and returns one record per visually relevant
element. Everything derivable from pure geometry is deliberately *not* computed there —
Java does that, where it is testable.
