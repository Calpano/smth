## browser_check_console

Navigates the current browser session to a URL, waits for it to settle, and returns every console error / warning plus any uncaught page exceptions captured during the load. Deterministic single shot — no retry.

| Param | Description |
|-------|-------------|
| `url` | URL or file path to navigate to (required) |
| `wait_until` | Puppeteer wait condition: `load`, `domcontentloaded`, `networkidle0`, `networkidle2`. Default `networkidle2`. |
| `include` | Entry types to keep. Default `['error','warning','pageerror']`. Console types follow Puppeteer (`log`, `info`, `warning`, `error`, `debug`, …); `pageerror` is uncaught exceptions. |
| `timeout_ms` | Navigation timeout. Default `15000`. |

Returns JSON:

```json
{
  "url": "https://ddot.it/",
  "title": "ddot.it",
  "counts": { "error": 0, "warning": 1, "pageerror": 0 },
  "entries": [
    { "level": "warning", "text": "...", "source": "https://.../app.js:42" }
  ]
}
```

`source` is `null` when Chrome did not attach a location (typical for Node-side `pageerror` entries — use `stack` instead).

### Notes

* Buffered logs from earlier tool calls are cleared before navigation, so only this load's output is reported.
* SPAs that keep loading after `networkidle2` should pass a longer `timeout_ms`, or use `domcontentloaded` and a follow-up `browser_goto` to wait manually.
* For in-place checks (no navigation), use `browser_goto getConsoleLogs=true` — it returns text-formatted logs without filtering.

→ [browser_check_console-example.json](browser_check_console-example.json)
