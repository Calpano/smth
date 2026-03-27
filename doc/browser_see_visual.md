## browser_see_visual

Screenshots the current page and returns the image as embedded base64 PNG — directly visible to the model.

| Param | Default | Description |
|-------|---------|-------------|
| `width` / `height` | current viewport | Viewport in pixels |
| `full_page` | `true` | Capture full scrollable page (`false` when `element_id` is set) |
| `zoom` | `1` | CSS zoom factor before screenshotting (`1.5` = 150%). Reverted after capture. |
| `element_id` | — | Scroll to and center element before capture (without `#`). Throws if not found. Implies `full_page: false`. |
| `device` | — | Emulate a named device (viewport + pixel ratio + user-agent). Overrides `width`/`height`. Call `browser_list_devices` for valid names. |

### Example

Site: `https://quotes.toscrape.com/`

```
browser_see_visual full_page=false
```

Returns an embedded base64 PNG displayed inline to the model.

→ [browser_see_visual-example.json](browser_see_visual-example.json)
