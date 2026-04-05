# smth — Browser MCP Server

**[https://calpano.github.io/smth/](https://calpano.github.io/smth/)**

A local MCP server that gives Claude a full browser: screenshots, DOM inspection, WCAG color analysis, and page interaction — all via a persistent Puppeteer/Chromium session running in Docker.

## What it does

smth exposes 16 MCP tools across three areas:

**See** — read and analyze any web page without interacting with it
`browser_see_visual` · `browser_read_text` · `browser_see_dom` · `browser_see_fonts` · `browser_see_colors` · `browser_see_color_pairs`

w**Act** — drive the browser like a user
`browser_launch` · `browser_goto` · `browser_click` · `browser_hover` · `browser_type`

**Compare** — capture and diff DOM snapshots
`browser_remember_dom` · `browser_doms` · `browser_dom_compare` · `fetch_dom_content` · `browser_list_devices`

## Requirements

- Docker (Desktop or Engine)
- Claude Code (or any MCP client supporting Streamable HTTP or SSE transport)

## Installation

```bash
git clone https://github.com/maxvolkel/smth.git
cd smth
docker compose up -d
```

The container starts on port 3000. Add `.mcp.json` to any project that should use it:

```json
{
  "mcpServers": {
    "smth": { "type": "http", "url": "http://localhost:3000/mcp" }
  }
}
```

Then restart Claude Code. The `mcp__smth__*` tools will be available immediately.

### Optional: expose local files

To let smth open local HTML files from a project directory:

```bash
PAGES_DIR=/path/to/your/project docker compose up -d --force-recreate
```

Files are mounted read-only at `/pages`. Open them with their host path or `file:///pages/filename.html`.

## Usage examples

### Example 1 — Audit a page for accessibility contrast issues

```
browser_launch url="https://example.com"
browser_see_color_pairs
```

Returns every text/background color pair on the page with WCAG 2.2 contrast ratios and AA/AAA pass/fail flags. Instantly shows which combinations fail accessibility requirements.

### Example 2 — Extract page content without nav/footer noise

```
fetch_dom_content url="https://example.com/article/123"
```

Opens the URL, automatically finds a peer page on the same site, computes what's shared (navigation, header, footer, sidebar), and returns only the unique content of the target page. No manual CSS exclusions needed.

### Example 3 — Check what a page looks like on mobile

```
browser_launch url="https://example.com"
browser_list_devices
browser_see_visual device="iPhone 15 Pro"
```

Emulates an iPhone 15 Pro (viewport, pixel ratio, user-agent) and returns an embedded screenshot directly visible to the model.

### Example 4 — Inspect a form before filling it

```
browser_launch url="https://example.com/login"
browser_see_dom lens=["code"]
browser_type id="username" text="myuser"
browser_type id="password" text="mypass"
browser_click selector="input[type=submit]"
browser_read_text
```

The `code` lens shows form fields, names, actions, and event handlers without layout noise. After filling and submitting, `browser_read_text` reads the resulting page.

### Example 5 — Diff a page before and after an interaction

```
browser_launch url="https://example.com/settings"
browser_remember_dom name="before" lens=["text"]
browser_click selector="#enable-toggle"
browser_remember_dom name="after" lens=["text"]
browser_dom_compare a="before" b="after"
```

Shows exactly which text appeared or disappeared after clicking the toggle — useful for verifying that a UI action had the expected effect.

## Tool reference

Full documentation for every tool lives in [`doc/smth.md`](doc/smth.md).

| Tool | Read-only | Description |
|------|-----------|-------------|
| `browser_list_devices` | yes | Lists device names for `browser_see_visual` |
| `browser_launch` | no | Starts a session, opens a URL |
| `browser_goto` | no | Navigates to a new URL in the existing session |
| `browser_read_text` | yes | Page text as Markdown + interactive element selectors |
| `browser_see_fonts` | yes | All fonts grouped by family, size, weight |
| `browser_see_colors` | yes | All computed colors with usage counts |
| `browser_see_color_pairs` | yes | Text/background pairs with WCAG contrast ratios |
| `browser_see_dom` | yes | Lens-filtered compact HTML or text search |
| `browser_click` | no | Clicks an element by id or CSS selector |
| `browser_hover` | no | Hovers and returns a before/after diff |
| `browser_type` | no | Types text into a form field |
| `browser_remember_dom` | no | Saves a named DOM snapshot |
| `browser_doms` | yes | Lists all saved snapshots |
| `browser_dom_compare` | yes | Diffs two snapshots or subtracts a background |
| `fetch_dom_content` | no | Opens a URL and returns only its foreground content |
| `browser_see_visual` | yes | Screenshot (viewport or full page, any device) |

## Container management

```bash
docker compose up -d                              # start
docker compose down                               # stop
docker compose up -d --build                      # restart after code changes
docker compose logs -f                            # tail logs
```

After a `--force-recreate`, the MCP session token is invalidated. The first tool call will get a `Session not found` error — just retry once; Claude Code re-establishes the session automatically.

## Accessing a local dev server

Docker cannot reach the host's `localhost`. Use `host.docker.internal` instead:

```
# Wrong
browser_launch url="http://localhost:4000/"

# Correct
browser_launch url="http://host.docker.internal:4000/"
```

## Privacy

smth runs entirely on your local machine. It does not transmit any data to external services. The browser session is isolated inside a Docker container. No telemetry, no analytics, no network calls beyond what the pages you visit make themselves.
