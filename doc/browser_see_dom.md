# browser_see_dom

Returns a lens-filtered view of the live DOM as compact HTML, or — with `justCount` — a lightweight summary of what would be returned.

## Parameters

| param | type | default | description |
|-------|------|---------|-------------|
| `lens` | `string[]` | `null` | Active lenses. Omit for the full DOM. Any combination of `"text"`, `"media"`, `"layout"`, `"code"`. |
| `search` | `string[]` | `null` | Search terms. When provided, returns text matches with 3 lines of context instead of serialized HTML. Mutually exclusive with `lens`. |
| `custom` | `object` | `null` | Extra element/attribute classifications merged (union) with the built-ins. |
| `justCount` | `boolean` | `false` | Return counts only instead of the HTML. |
| `max_chars` | `number` | `null` | Character budget. Serializes starting at depth 1 and deepens one level at a time until the output would exceed this limit, then returns the last fitting depth. Depth reached is reported in `justCount` output. |
| `exclude` | `string` | `null` | Comma-separated CSS selectors. Matching elements and their entire subtrees are removed before lens filtering. Example: `".sidebar, #cookie-banner, script"` |

## Lenses

| lens | what it includes |
|------|-----------------|
| `text` | Visible text — headings, paragraphs, labels, button labels, form values, placeholders, tooltips |
| `media` | Non-text visuals — `<img>`, `<svg>`, `<path>`, `<canvas>` and their visual attributes (`src`, `d`, `fill`, …) |
| `layout` | Structure — `<div>`, `<table>`, `<tr>`, `<td>`, `class`, `style`, `colspan`, `viewBox`, … |
| `code` | Behavior — `<script>`, event handlers (`on*`), `data-*`, form wiring (`name`, `for`, `type`), `href`, `src` |

`id` and `class` are active in all lenses (they serve as anchors, CSS targets, and JS hooks simultaneously).

## Filtering behaviour

- **Matching elements** are serialized with only the attributes that also match an active lens.
- **Non-matching elements** are **transparent**: they are skipped but their children are still walked, surfacing to the nearest matching ancestor. Nesting among matched elements is preserved.
- `<script>` and `<style>` content is always stripped (their child nodes are never walked).

## justCount

When `justCount: true`, the full filtered serialization runs internally but only the following is returned:

```json
{
  "chars": 14823,
  "elements": { "div": 12, "span": 40, "input": 8 },
  "attributes": { "class": 50, "id": 10, "type": 8 },
  "depth": 9
}
```

`chars` is the character length of the HTML that would have been returned. `depth` is included when `max_chars` is set and shows which depth level was actually used. Use `justCount` to gauge token cost before committing to a full fetch.

## max_chars

Incrementally deepens the DOM traversal until the serialized output would exceed the budget:

1. Serialize root + depth 1 → count chars
2. If under budget, try depth 2, and so on
3. When the next depth would exceed `max_chars`, return the previous depth's result
4. If depth 1 already exceeds the budget, it is returned anyway (minimum possible output)

Text nodes inside elements at the deepest allowed depth are still included, so leaf labels and button text are never blank.

## exclude

Comma-separated CSS selectors. Each matched element and its entire subtree is removed before any lens filtering or depth counting. Useful for stripping known-heavy or irrelevant sections:

```
exclude=".provider-rows, footer, script, [data-hidden]"
```

## custom

Extends the built-in classification. Categories are merged as a union — you can add a tag to more categories but not remove it from existing ones.

```json
{
  "elements": {
    "my-widget": ["layout", "code"]
  },
  "attributes": {
    "global": {
      "data-testid": ["code"]
    },
    "by_element": {
      "div": {
        "x-bind": ["code"]
      }
    }
  }
}
```

## Built-in classification summary

The full classification lives in `src/see-dom.js` (`BUILTIN` constant) and is documented in detail in `doc/dom_classification.json`.

| category | example elements | example attributes |
|----------|------------------|--------------------|
| `text` | `h1`–`h6`, `p`, `label`, `span`, `button`, `input`, `option` | `placeholder`, `value`, `alt`, `title` |
| `media` | `img`, `svg`, `path`, `canvas` | `src`, `d`, `fill`, `stroke` |
| `layout` | `div`, `table`, `tr`, `td`, `header`, `footer`, `nav` | `class`, `style`, `colspan`, `viewBox`, `width`, `height` |
| `code` | `script`, `a`, `button`, `input`, `select` | `on*`, `data-*`, `href`, `name`, `for`, `type` |

## search

When `search` is provided, the tool reads `document.body.innerText`, splits it into lines, and returns every line that contains any of the search terms — surrounded by 3 lines of context above and below. Overlapping windows are merged. Each block is prefixed with the matched term and the matching line is marked `>>>`.

```
browser_see_dom search=["submit", "error"]
```

Output format:
```
[submit]
    You agree to the terms.
>>> Submit your application
    Thank you for applying.

---

[error]
    Please check your input.
>>> Error: field is required
    Try again below.
```

## Examples

Full DOM (no filtering):
```
browser_see_dom
```

Text content only:
```
browser_see_dom lens=["text"]
```

Layout skeleton:
```
browser_see_dom lens=["layout"]
```

Interaction map:
```
browser_see_dom lens=["code"]
```

Estimate token cost before fetching:
```
browser_see_dom lens=["text"] justCount=true
```

Find the deepest layout view that fits in ~20k chars:
```
browser_see_dom lens=["layout"] max_chars=20000 justCount=true
```

Fetch that view, excluding known-heavy subtrees:
```
browser_see_dom lens=["layout"] max_chars=20000 exclude=".data-rows, footer"
```

## Live examples

Site: `https://quotes.toscrape.com/`

```
browser_see_dom lens=["text"]
browser_see_dom lens=["code"]
browser_see_dom search=["Albert Einstein", "simile"]
```

→ [browser_see_dom-example.json](browser_see_dom-example.json)
