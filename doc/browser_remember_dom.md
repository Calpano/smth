## browser_remember_dom

Captures a named snapshot of the current DOM for later diffing with `browser_dom_compare`.

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | string | required | Name for this snapshot. |
| `lens` | string[] | `null` | Lenses applied at capture time. Same options as `browser_see_dom`. |
| `exclude` | string | `null` | CSS selectors to exclude before capturing. |
| `max_chars` | number | `null` | Character budget (same as `browser_see_dom`). |

Returns a confirmation with char count, lens, and timestamp.

### Usage pattern

```
browser_remember_dom name="before" lens=["text","code"]
// … perform some action …
browser_remember_dom name="after"  lens=["text","code"]
browser_dom_compare a="before" b="after"
```

### Tip

Use the same lens params for snapshots you plan to compare — the diff operates on the stored strings directly.

### Example

Site: `https://quotes.toscrape.com/`

```
browser_remember_dom name="home" lens=["text"]
```

→ [browser_remember_dom-example.json](browser_remember_dom-example.json)
