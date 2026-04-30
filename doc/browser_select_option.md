## browser_select_option

Picks an option from a `<select>` element. Dispatches `input` and `change` events so framework listeners (React, Vue, Svelte, vanilla onChange) see the update.

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | — | HTML element id of the `<select>` (without `#`). Preferred when available. |
| `selector` | string | — | CSS selector for the `<select>`. Used when no id. Supports the `:has-text('...')` extension. |
| `value` | string | — | The option's `value` attribute. Use when known. |
| `label` | string | — | The option's visible text. Case-insensitive: exact match preferred, otherwise substring. |

Provide either `id` or `selector`, and either `value` or `label`. Returns `Selected "<label>" (value="<value>") in <selector>`.

If no option matches, the error lists every available option's value and label so you can pick a different one.

If the resolved element isn't a `<select>` (e.g. a custom dropdown built from `<div>`s), this tool throws — use `browser_click` to open the menu and `browser_click` again to pick an item instead.

### Example

```
browser_select_option id="country-select" value="de"
browser_select_option id="country-select" label="Germany"
browser_select_option selector="select[name='size']" label="Large"
```
