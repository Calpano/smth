# Changelog

Every release is a `v*` tag; pushing one builds `smth.mcpb` and publishes it as a
GitHub release. What each release changed is written down here, because the
generated release notes only see pull requests and most work lands straight on
`main`.

## Unreleased

### The action pins say what they are pinned to

Every `uses:` in both workflows is pinned by commit hash, and every comment
naming the version that hash stood for was wrong — by one to three major
versions. `actions/checkout` and `actions/upload-artifact` read `# v4` and were
v7.0.1; `actions/configure-pages` read `# v5` and was v6.0.0;
`upload-pages-artifact` read `# v3` and was v5.0.0; `deploy-pages` read `# v4`
and was v5.0.0; `action-gh-release` read `# v2` and was v3.0.2. Dependabot had
been keeping the hashes current and leaving the hand-written comments behind,
which is the failure mode hash pinning invites: the hash is the security
control, the comment is the only thing a reader can understand, and nothing
checked that they agreed. Each comment now names the exact release, and each
workflow says so once at the top.

`action-gh-release` moves to v3.0.3 and `deploy-pages` to v5.0.1 — both patch
bumps, and both hashes verified against the upstream tag list rather than taken
on trust.

### puppeteer-core 25.3.0 → 25.10.0

Among the fixes: a websocket connection that dies without a close is now
detected, which is the transport smth holds open for the lifetime of a session.

### The bundle no longer claims to run on Node 18

`manifest.json` advertised `node >=18.0.0` while puppeteer-core 25.x has
required `>=22.12.0` the whole time, so the compatibility line the installer
reads had been wrong since 1.0.0. Both it and a new `engines` field in
`package.json` now say `>=22.12.0`. The Docker image was never affected — it
runs node 26.

## 1.0.6 — 2026-09-14

### Every open security advisory cleared

`npm audit` reports zero vulnerabilities in both lockfiles, down from 38 open
Dependabot alerts. Every one was transitive — nothing smth depends on directly —
so only the lockfiles changed; no declared range in either `package.json` moved.
`hono` 4.12.33 → 4.13.7, `@hono/node-server` 1.19.13 → 2.1.1 (a range the MCP SDK
declares itself, as `^1.19.9 || ^2.0.5`), `fast-uri` 3.1.5 → 3.1.7, `ip-address`
10.2.0 → 10.7.0, `qs` 6.15.2 → 6.16.0, and in the test lockfile `vitest` 4.1.11,
`postcss` 8.5.28 and `nanoid` 3.3.19.

Of the 38, one was reachable in the shipped image: `qs`, which Express uses to
parse query strings. The rest were not — `hono` ships in the image but the server
never loads it (the SDK imports only `getRequestListener` from
`@hono/node-server`, which does not pull the framework), `fast-uri` is reached
through ajv, which never parses a URL because no tool schema declares a `uri`
format, and `ip-address` comes via `express-rate-limit`, which smth never
instantiates. The 21 alerts against `test/package-lock.json` were never in the
image at all: the Dockerfile installs with `npm ci --omit=dev` and the test deps
live in a separate `test/package.json`.

Dependabot now groups security updates, so the next advisory wave arrives as one
pull request per lockfile instead of the thirteen this one opened.

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
