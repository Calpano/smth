## browser_hover

Hovers over an element and returns a before/after diff of visible text and interactive attributes (text + code lens).

| Param | Description |
|-------|-------------|
| `id` | HTML element id without `#`. Preferred when available. |
| `selector` | CSS selector. Used when the element has no id. |

Hovers the element, waits 300 ms for CSS transitions to settle, then returns a line-based diff. Lines prefixed `+` were added, `-` were removed.

The diff uses the `text` + `code` lens, so it surfaces meaningful changes (tooltip appearance, label changes, class toggles on interactive attributes) without noise from structural layout churn.

For a full before/after comparison of the entire DOM, use `browser_remember_dom` + `browser_dom_compare` instead.

### `:has-text()` selector extension

Append `:has-text('substring')` to a selector to match the innermost element whose visible text contains the substring (case-insensitive). Example: `span:has-text('hover over me')`. See `browser_click.md` for full details.

### Example

Site: `https://quotes.toscrape.com/login`

```
browser_hover id="username"
```

→ [browser_hover-example.json](browser_hover-example.json)
