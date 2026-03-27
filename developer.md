# Developer Guide

## Setup

Copy `.env.example` to `.env` and set `PAGES_DIR` to the directory you want to expose as `/pages` inside the container (e.g. your project's root so you can open local HTML files):

```sh
cp .env.example .env
# edit .env
```

## Start

```sh
docker compose up -d
```

The server starts on port 3000 (or `SMTH_PORT` if set in `.env`). Check it's healthy:

```sh
curl http://localhost:3000/health
```

## Stop

```sh
docker compose down
```

## Restart (after code changes)

Source files are baked into the image, so a code change requires a rebuild:

```sh
docker compose up -d --build
```

To just restart without rebuilding (e.g. after an env change):

```sh
docker compose restart
```

## Rebuild from scratch

```sh
docker compose down
docker compose build --no-cache
docker compose up -d
```

## Logs

```sh
docker compose logs -f
```

## Connecting Claude Code

The repo ships a `.mcp.json` that points Claude Code at `http://localhost:3000/mcp`. Once the container is running, Claude Code picks it up automatically — no manual config needed.

To connect a different client, use the Streamable HTTP endpoint:

```
http://localhost:3000/mcp
```

For legacy clients that don't support Streamable HTTP, a fallback SSE endpoint is also available:

```
http://localhost:3000/sse
```

## Local pages

Files in `PAGES_DIR` (your `.env` value) are mounted read-only at `/pages` inside the container. Open them in the browser tool using their host path or a `file:///pages/...` URL — the server translates the path automatically.

## Project layout

```
src/
  server.js       — MCP server + Express app
  see-fonts.js    — browser-side font analysis script
  see-dom.js      — browser-side DOM tag/attribute counter
doc/
  smth.md         — full tool reference and tips
  browser_*.md    — per-tool reference pages
  fetch_dom_content.md
```