## browser_goto

Navigates the existing browser session to a new URL. Unlike `browser_launch`, it does not restart the browser — the existing page, cookies, and session state are preserved.

| Param | Description |
|-------|-------------|
| `url` | Local or remote URL (required) |

Returns: page title string.

Accepts the same URL formats as `browser_launch`.

### Example

Site: `https://quotes.toscrape.com/`

```
browser_goto url="https://quotes.toscrape.com/page/2/"
```

→ [browser_goto-example.json](browser_goto-example.json)
