## browser_check_favicon

Navigates to a URL, scans every `<link rel="icon">` / `apple-touch-icon` / `mask-icon` declaration, fetches each, and additionally probes `/favicon.ico` if no link declared it. Reports whether at least one resolves to a real image.

| Param | Description |
|-------|-------------|
| `url`           | URL or file path to navigate to (required) |
| `check_default` | If true (default), probe `/favicon.ico` when no `<link>` already points at it. |

Returns JSON:

```json
{
  "url": "https://ddot.it",
  "favicons": [
    { "rel": "icon", "href": "https://ddot.it/assets/images/favicon.svg",
      "type": "image/x-icon", "sizes": null,
      "status": 200, "contentType": "image/svg+xml", "bytes": 113, "ok": true }
  ],
  "default": {
    "url": "https://ddot.it/favicon.ico",
    "status": 404, "contentType": "text/html; charset=utf-8", "bytes": 9379,
    "ok": false
  },
  "ok": true,
  "reason": null
}
```

### Pass criteria

`ok` is true iff at least one probed favicon (declared or fallback) returned a 2xx response with an `image/*` content-type and a non-zero body. The check does not require the declared `type` attribute to match the actual `Content-Type` — only the real response header counts.

### Schemes

Each favicon `href` is followed regardless of scheme:

| Scheme    | Probe behaviour |
|-----------|-----------------|
| `http(s):` | Node `fetch` (follows redirects, 10s timeout) — server status, content-type, body size. |
| `file:`    | `fs.stat` plus extension-to-MIME guess. |
| `data:`    | Parsed inline; size is decoded base64 length (or raw length for non-base64). |

### Edge cases

* Many sites return an HTML 404 page for `/favicon.ico`. That registers as `status: 200`/non-image content-type → `ok: false`. The overall result still passes as long as a declared `<link rel="icon">` worked.
* `apple-touch-icon` declared but missing: counts as a fail for that entry. The overall result still passes if another favicon worked.
* Cross-origin favicons (CDN-hosted) are fetched from Node, not the page, so there's no CORS restriction.

### Out of scope

* Image format validation beyond content-type + size (we don't decode the bytes).
* Web-manifest `icons` entries (`<link rel="manifest">` → JSON parse).

→ [browser_check_favicon-example.json](browser_check_favicon-example.json)
