## browser_read_text

Returns the visible text of the current page as Markdown, plus a list of interactive elements each annotated with a CSS selector.

No parameters.

Headings become `#`/`##`/etc., list items become `- `, paragraphs are separated by blank lines. Elements with an `id` get a `{#id}` annotation.

Appends an **Interactive Elements** section listing every `a[href]`, `button`, `input`, `select`, `textarea`, `[onclick]`, `[onmouseover]` with tag, selector, and label. Elements with an `id` show `#id`; elements without one show a generated CSS path selector. These selectors can be passed directly to `browser_click`, `browser_hover`, and `browser_type`.

### Example

Site: `https://quotes.toscrape.com/`

```
browser_read_text
```

→ [browser_read_text-example.json](browser_read_text-example.json)
