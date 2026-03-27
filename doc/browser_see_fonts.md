## browser_see_fonts

Returns a JSON report of all fonts in use on the current page, grouped by family.

No parameters.

### Example

Site: `https://quotes.toscrape.com/`

```
browser_see_fonts
```

→ [browser_see_fonts-example.json](browser_see_fonts-example.json)

Each entry lists computed `size` (px), `weight`, and `usage` count. `bold` and `italic` flags are included only when `true`.

### Example output

```json
{
  "Arial": [
    { "size": 14.08,   "weight": 400, "usage": 7 },
    { "size": 13.12,   "weight": 400, "usage": 2 },
    { "size": 14.08,   "weight": 600, "usage": 1 }
  ],
  "Open Sans": [
    { "size": 14.08, "weight": 700, "bold": true, "usage": 3 },
    { "size": 14.08, "weight": 400, "italic": true, "usage": 1 }
  ],
  "sans-serif": [
    { "size": 12.16, "weight": 400, "usage": 156 }
  ]
}
```
