## browser_click

Clicks an element on the current page. Waits for any navigation triggered by the click to complete (up to 5 s) before returning.

| Param | Description |
|-------|-------------|
| `id` | HTML element id without `#`. Preferred when available. |
| `selector` | CSS selector. Used when the element has no id. |

Provide either `id` or `selector`. Returns `"Clicked <selector>. Page: <title>"`.

If the click triggers navigation, the tool waits for `networkidle0` before returning, so subsequent DOM reads see the new page immediately.

### Finding selectors

Use `browser_read_text` — the Interactive Elements section lists a CSS selector for every clickable element, even those without an `id`.

### `:has-text()` selector extension

When an element has no stable id/class but has distinctive visible text, append `:has-text('substring')` to any CSS selector. Matching is case-insensitive substring and picks the **innermost** element that contains the text.

```
browser_click selector="button:has-text('Submit')"
browser_click selector="a:has-text('Learn more')"
browser_click selector=":has-text('Accept all')"     # any element
```

Supports both single and double quotes. Only one `:has-text()` per selector, always at the end. Also available in `browser_hover` and `browser_type`.

### Example

Site: `https://quotes.toscrape.com/login`

```
browser_click id="username"
```

→ [browser_click-example.json](browser_click-example.json)
