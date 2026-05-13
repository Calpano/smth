# smth CLI

Shell wrapper for the smth MCP server. Each tool is a subcommand; arguments come from the tool's JSON schema. Auto-starts the docker container when it's down.

## Install

```bash
# From the repo root
npm install -g .
# or run directly
node bin/smth.js …
```

## Usage

```
smth <tool> [--key=value …]   # call a tool (auto-up if container is down)
smth list                     # list all tools
smth help <tool>              # show arguments for one tool
smth up | down | status       # container lifecycle
smth session reset            # forget the cached browser session
```

## Session continuity across invocations

Each MCP call needs a session ID. The CLI persists the server-assigned ID in `~/.smth/session` and reuses it on the next invocation, so this works as expected:

```bash
smth browser_launch --url=https://example.com
smth browser_read_text                          # reuses the same browser
smth browser_click --selector="a:has-text('More')"
smth browser_see_visual                         # screenshot, saved to /tmp/smth/
```

If the server reaps the session (default: 30s idle) or you restart the container, the next call detects the stale ID, drops it, and starts a fresh session automatically. Use `smth session reset` to force a new browser without restarting the container.

## Arguments

Flag parsing is driven by the tool's `inputSchema`:

| Schema type | Accepted forms |
|-------------|----------------|
| `string`    | `--url=https://…`, `--url https://…` |
| `number`    | `--timeout_ms=10000` |
| `boolean`   | `--getConsoleLogs`, `--no-getConsoleLogs`, `--flag=true` |
| `array`     | `--include error --include warning`, or `--include=error,warning` |

Positional arguments are not supported.

## Output

* Text content goes to stdout. JSON-shaped responses (e.g. `browser_check_console`) print as-is — pipe through `jq` for filtering.
* Image content (`browser_see_visual`) is written to `$TMPDIR/smth/` and the path is printed.
* Tool errors print to stderr and exit non-zero.

## Examples

```bash
# Site health check
smth browser_check_console --url=https://example.com --include=error,pageerror | jq

# Verify a German imprint
smth browser_check_imprint --url=https://ddot.it | jq '.ok, .reason'

# Multi-step interaction
smth browser_launch --url=https://example.com
smth browser_see_dom
smth browser_click --selector='a:has-text("More")'
smth browser_read_text
```

## Environment

| Var        | Default                  | Purpose |
|------------|--------------------------|---------|
| `SMTH_URL` | `http://localhost:3000`  | Override the server URL (e.g. point at a different port). |
