## browser_dom_compare

Two modes depending on which params are provided:

### Diff mode — `a` + `b`

Returns a line-based diff between two snapshots. Lines prefixed `+` were added in b, lines prefixed `-` were removed.

| Param | Description |
|-------|-------------|
| `a` | Name of the before snapshot (required) |
| `b` | Name of the after snapshot |

### Subtract mode — `a` + `subtract`

Returns the lines of snapshot `a` that are **absent** from the `subtract` snapshot — i.e. the foreground content of `a` with the background stripped out.

| Param | Description |
|-------|-------------|
| `a` | Name of the content snapshot (required) |
| `subtract` | Name of the background snapshot to subtract |

Use `browser_doms` to list available snapshot names.

### Example

Site: `https://quotes.toscrape.com/` — diffing page 1 vs page 2 (both captured with `lens=["text"]`):

```
browser_dom_compare a="home" b="page2"
```

→ [browser_dom_compare-example.json](browser_dom_compare-example.json)

### Tip

Use the same lens when capturing snapshots you plan to compare or subtract. The `fetch_dom_content` tool builds the background snapshot automatically.
