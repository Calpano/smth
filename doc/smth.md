# smth MCP tool

Persistent Docker container on `localhost:3000`. Browser interaction via Puppeteer + Chromium.

## Using smth in a project

Add `.mcp.json` to the project root:

```json
{
  "mcpServers": {
    "smth": { "type": "http", "url": "http://localhost:3000/mcp" }
  }
}
```

## Tools

All tools are stateful and share one Puppeteer session per MCP connection. Always call `browser_launch` first.

| Tool                      | Doc                                                      |
|---------------------------|----------------------------------------------------------|
| `browser_list_devices`    | [browser_list_devices.md](browser_list_devices.md)       |
| `browser_launch`          | [browser_launch.md](browser_launch.md)                   |
| `browser_goto`            | [browser_goto.md](browser_goto.md)                       |
| `browser_read_text`       | [browser_read_text.md](browser_read_text.md)             |
| `browser_see_fonts`       | [browser_see_fonts.md](browser_see_fonts.md)             |
| `browser_see_colors`      | [browser_see_colors.md](browser_see_colors.md)           |
| `browser_see_color_pairs` | [browser_see_color_pairs.md](browser_see_color_pairs.md) |
| `browser_see_dom`         | [browser_see_dom.md](browser_see_dom.md)                 |
| `browser_click`           | [browser_click.md](browser_click.md)                     |
| `browser_hover`           | [browser_hover.md](browser_hover.md)                     |
| `browser_type`            | [browser_type.md](browser_type.md)                       |
| `browser_see_visual`      | [browser_see_visual.md](browser_see_visual.md)           |
| `browser_remember_dom`    | [browser_remember_dom.md](browser_remember_dom.md)       |
| `browser_doms`            | [browser_doms.md](browser_doms.md)                       |
| `browser_dom_compare`     | [browser_dom_compare.md](browser_dom_compare.md)         |
| `fetch_dom_content`       | [fetch_dom_content.md](fetch_dom_content.md)             |

## Container management

```bash
docker compose up -d                                            # start
docker compose down                                            # stop
docker compose up -d --force-recreate                         # restart
docker compose build && docker compose up -d --force-recreate # rebuild image
```

After `--force-recreate`, the MCP SSE session token is invalidated. The first tool call will fail with `"Session not found"` (HTTP 404). Just retry — Claude Code re-establishes the session automatically.

## Tips & Gotchas

### Accessing a local dev server

Docker cannot reach the host's `localhost`. Use `host.docker.internal` instead:

```
# Wrong — connection refused
http://localhost:4000/page.html

# Correct
http://host.docker.internal:4000/page.html
```

### MCP tools only appear if the server was running at session start

The `.mcp.json` file is read when Claude Code starts the session. If the smth container was not running at that point, the `mcp__smth__*` tools will not be in the tool namespace. Start the container first, then start (or restart) Claude Code.

### Finding elements without an id

`browser_read_text` lists every interactive element with a CSS selector. Pass that selector directly to `browser_click`, `browser_hover`, or `browser_type` via the `selector` param when an element has no `id`.
