## browser_press_key

Press a single key, optionally with modifiers. Use this for keys that have no character (Enter, Escape, Tab, arrow keys, Backspace, function keys) or for keyboard shortcuts (Ctrl+S, Cmd+A).

For typing words or sentences into a form field, use `browser_type`. For picking an option from a `<select>`, use `browser_select_option`.

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `key` | string | required | Key name. Examples: `Enter`, `Escape`, `Tab`, `ArrowUp`, `ArrowDown`, `Backspace`, `Delete`, `PageDown`, `F5`, or a single character like `a`, `1`, `?`. Case-sensitive. |
| `modifiers` | string[] | `[]` | Held while pressing. Allowed: `Control`, `Shift`, `Alt`, `Meta`. `Meta` is Cmd on macOS, Win key elsewhere. |
| `id` | string | — | Optional. Focus this element id before pressing. |
| `selector` | string | — | Optional. Focus this CSS selector before pressing. Supports `:has-text('substring')`. |
| `count` | integer | `1` | Press the key this many times in a row (1–50). |

If neither `id` nor `selector` is given, the key is sent to whatever element currently has focus (often `<body>`).

If the press triggers navigation (e.g. Enter on a form field that submits), the tool waits up to 5s for `networkidle0` before returning.

Returns `Pressed <combo>[ ×N][ (focused <selector>)]. Page: <title>`.

### Examples

```
browser_press_key key="Enter" id="search-input"
browser_press_key key="Escape"
browser_press_key key="ArrowDown" count=3
browser_press_key key="a" modifiers='["Control"]'      # Ctrl+A
browser_press_key key="s" modifiers='["Meta"]'         # Cmd+S on macOS
```

### Key names

Use the names from Puppeteer's [KeyInput list](https://pptr.dev/api/puppeteer.keyinput) — common ones: `Enter`, `Escape`, `Tab`, `Space`, `Backspace`, `Delete`, `Home`, `End`, `PageUp`, `PageDown`, `ArrowUp`, `ArrowDown`, `ArrowLeft`, `ArrowRight`, `F1`–`F12`. For letters and digits, use the literal character (`a`, `b`, `1`).
