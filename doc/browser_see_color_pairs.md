# browser_see_color_pairs

For every visible element with direct text content, finds the text color and its effective background color, computes the WCAG 2.2 contrast ratio, and returns all pairs with pass/fail flags.

No parameters.

## Behaviour

- Visits every visible element that has at least one direct text node, or SVG elements with a non-transparent `fill` (SVG elements inside `<svg>` are included even without `offsetParent`)
- **Text color**: `getComputedStyle(el).color`
- **Effective background**: walks up the DOM from the element until a non-transparent `background-color` is found; falls back to `rgb(255, 255, 255)` (browser default) if none is found
- Contrast ratio computed using the WCAG 2.2 relative luminance formula: `(L_hi + 0.05) / (L_lo + 0.05)`
- Pairs deduplicated; `usage` counts how many elements share that pair
- Result sorted by `usage` descending

## Output fields

| Field | Description |
|-------|-------------|
| `text` | Text color as hex (`#rrggbb`) |
| `background` | Effective background color as hex (`#rrggbb`) |
| `contrast` | WCAG contrast ratio (rounded to 2 decimal places) |
| `aa` | `true` if contrast ≥ 4.5:1 (WCAG AA) |
| `aaa` | `true` if contrast ≥ 7.0:1 (WCAG AAA) |
| `count` | Number of elements with this exact pair |

## Example output

```json
[
  {
    "text": "#b1b7b9",
    "background": "#2c2f30",
    "contrast": 5.12,
    "aa": true,
    "aaa": false,
    "count": 156
  },
  {
    "text": "#6ca6b0",
    "background": "#2c2f30",
    "contrast": 3.21,
    "aa": false,
    "aaa": false,
    "count": 40
  }
]
```

## Example

Site: `https://quotes.toscrape.com/`

```
browser_see_color_pairs
```

→ [browser_see_color_pairs-example.json](browser_see_color_pairs-example.json)

## Limitations

- Alpha-composited backgrounds (semi-transparent layers stacked over each other) are not composited — only the first non-transparent ancestor is used
- Pseudo-element backgrounds (`::before`, `::after`) are not walked
- Absolutely/fixed positioned elements may have a visual background that does not match their DOM parent chain
