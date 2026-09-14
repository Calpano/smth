# Changelog

Every release is a `v*` tag; pushing one builds `smth.mcpb` and publishes it as a
GitHub release. What each release changed is written down here, because the
generated release notes only see pull requests and most work lands straight on
`main`.

## 1.0.5 — 2026-09-14

### A dev server on `localhost` can finally be opened

`browser_launch url="http://localhost:5173/"` works, and the dev server sees the
`Host` header the caller wrote.

Chromium is launched with `--host-resolver-rules=MAP localhost
host.docker.internal, MAP 127.0.0.1 host.docker.internal`, so only the DNS answer
changes — the URL and the `Host` header stay as written. That is the opposite of
what the old fallback did: it rewrote the URL to `host.docker.internal` after a
connection was refused, and the rewritten header is what made vite,
webpack-dev-server and Next answer *"Blocked request. This host is not allowed."*
The fallback and its `(localhost unreachable; use host.docker.internal)` note are
gone. `docker-compose.yml` now sets `extra_hosts: host.docker.internal:host-gateway`
so the name also resolves on Linux, where Docker Desktop is not there to supply it.

### A page that streams no longer takes 30 seconds and fails

`gotoPage` no longer hands an idle wait to `page.goto`. It commits the navigation
with `domcontentloaded` and then waits for quiet separately, giving up on the wait
rather than on the page. A page that holds a connection open on purpose — server-sent
events, a websocket, a live-reloading dev server — never goes idle, so `networkidle0`
turned every one of them into a timeout and no page at all.

### Since 1.0.4, also

- `smth`, a host-side CLI wrapping the MCP server for shell use, with its own test
  suite. Subcommands match tool names, flags come from each tool's JSON schema, the
  container is started on demand, and the session id is kept in `~/.smth/session` so
  chained calls share one browser. See `doc/cli.md`.
- `browser_check_console`, `browser_check_imprint` and `browser_check_favicon` —
  single-call checks that load a URL and answer one question about it.
- Session leak fixed: an idle reaper, a cap on concurrent sessions, and a graceful
  shutdown that closes the browsers.
- `browser_see_visual` returned a broken image on newer puppeteer; the `Uint8Array`
  screenshot result is now wrapped before base64 encoding.
- express 5, puppeteer-core 24, `npm ci` against lockfiles in both Dockerfiles,
  dependabot enabled, and its security bumps applied.
- `package.json` and `manifest.json` carry the release version again — both had been
  left at `1.0.0` since the first tag.

## 1.0.4 — 2026-04-30

- `browser_select_option` and `browser_press_key`.
- SVG support across the seeing tools: `<text>` elements count as fonts, `fill` and
  `stroke` as colors and as contrast foregrounds, an `svg` lens for `browser_see_dom`,
  and SVG elements that behave like controls are listed as interactive.
- GitHub actions pinned by hash; dependency vulnerabilities cleared.

## 1.0.3 — 2026-04-05

- The `.mcpb` bundle builds in the release workflow.

## 1.0.2 — 2026-04-05

- Release workflow fixes.

## 1.0.1 — 2026-04-05

- Release workflow fixes.

## 1.0.0 — 2026-04-05

First public release: 16 MCP tools for seeing, driving and diffing a page, over a
persistent Puppeteer/Chromium session in Docker.
