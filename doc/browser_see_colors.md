# browser_see_colors

Returns a JSON report of all computed colors in use on the current page — text, background, and border — with usage counts per color.

## Parameters

| Name     | Type       | Default | Description |
|----------|------------|---------|-------------|
| `only`   | `string[]` | all     | Restrict to one or more categories: `"text"`, `"background"`, `"border"`. Omit to include all three. |
| `colors` | `string[]` | all     | Filter to specific colors given as hex strings (`"#rrggbb"` or `"#rgb"`). Only matching colors appear in the result. |
| `where`  | `boolean`  | `false` | When `true`, each entry gains a `"where"` key listing the element selectors (`#id`, `.class`, or tag name) where that color was found, grouped by category. Selectors are deduplicated. |

## Behaviour

- Visits every visible DOM element (`offsetParent` check, same as `browser_see_fonts`)
- Reads fully resolved computed values via `getComputedStyle` (inheritance included)
- **Text**: `color` property, counted for every visible element
- **Background**: `backgroundColor`, transparent (`rgba(0,0,0,0)`) skipped
- **Border**: each side (`top/right/bottom/left`) where `border-*-width > 0`; duplicate colors on the same element counted once
- Different alpha values are treated as distinct colors (`rgba(255,255,255,0.18)` ≠ `rgba(255,255,255,0.3)`)
- Result sorted by total usage count descending; zero-count categories omitted from each entry
- `colors` hex values are matched against computed `rgb()`/`rgba()` strings after conversion

## Example

Site: `https://quotes.toscrape.com/`

```
browser_see_colors
```

→ [browser_see_colors-example.json](browser_see_colors-example.json)

## Example output

Default (no params):

```json
{
  "rgb(177, 183, 185)": { "text": 490 },
  "rgb(99, 108, 110)":  { "border": 169 },
  "rgb(108, 166, 176)": { "text": 40, "background": 1, "border": 1 },
  "rgb(73, 79, 80)":    { "background": 24 },
  "rgba(255, 255, 255, 0.18)": { "border": 2 }
}
```

With `only: ["border"]`:

```json
{
  "rgb(99, 108, 110)":  { "border": 169 },
  "rgb(108, 166, 176)": { "border": 1 },
  "rgba(255, 255, 255, 0.18)": { "border": 2 }
}
```

With `colors: ["#6ca6b0"], where: true`:

```json
{
  "rgb(108, 166, 176)": {
    "text": 40,
    "background": 1,
    "border": 1,
    "where": {
      "text":       ["#header", ".nav-link", "span"],
      "background": ["#hero"],
      "border":     [".card"]
    }
  }
}
```
