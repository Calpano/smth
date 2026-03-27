## browser_launch

Launches a new browser session and opens a URL. **Always call this before any other `browser_` tool.** Calling it again replaces the existing session (browser is restarted).

Use `browser_goto` to navigate within an existing session without relaunching.

| Param | Description |
|-------|-------------|
| `url` | Local or remote URL (required) |

Returns: page title string.

### URL formats accepted

- `https://` URL — fetched directly
- `http://host.docker.internal:<port>/path` — local dev server (use this instead of `localhost`)
- Absolute host path: `/Users/you/project/index.html`
- `file://` URL: `file:///Users/you/project/index.html`
- Relative path (resolved under the `/pages` mount): `index.html`, `dist/app/index.html`

### File path translation

The container mounts one host directory read-only as `/pages`. Set `PAGES_DIR` before starting:

```bash
PAGES_DIR=/path/to/project docker compose up -d --force-recreate
```

Host paths are translated to container paths automatically.

### Example

Site: `https://quotes.toscrape.com/`

```
browser_launch url="https://quotes.toscrape.com/"
```

→ [browser_launch-example.json](browser_launch-example.json)
