## browser_type

Types text into a form field. Clears the field first by default.

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | — | HTML element id (without `#`). Preferred when available. |
| `selector` | string | — | CSS selector. Used when the element has no id. |
| `text` | string | required | Text to type. |
| `clear` | boolean | `true` | Clear the existing field value before typing. |

Provide either `id` or `selector`. Returns `"Typed into <selector>"`.

### Finding selectors

Use `browser_read_text` — the Interactive Elements section lists a CSS selector for every input, even those without an `id`.

### `:has-text()` selector extension

Selector supports the `:has-text('substring')` extension (case-insensitive substring match, innermost element). See `browser_click.md` for details. Note that `<input>` elements have no text content themselves, so `:has-text()` is of limited use for `browser_type`. Prefer `#id`, `[name=…]`, `[placeholder=…]`, or `[type=…]` for form fields.

### Example

Site: `https://quotes.toscrape.com/login`

```
browser_type id="username" text="testuser"
```

→ [browser_type-example.json](browser_type-example.json)
