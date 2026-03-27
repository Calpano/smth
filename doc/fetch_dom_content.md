# fetch_dom_content

All-in-one tool that opens a URL, auto-detects site chrome (navigation, header, footer, sidebars), and returns only the unique content of that page.

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `url` | string | required | URL to fetch content from. |
| `lens` | string[] | `["text","layout"]` | Lenses for DOM capture. |
| `prefix` | string | `"fetch"` | Prefix for the stored snapshot names. |

Returns the foreground content as text, preceded by a metadata header.

## How it works

1. **Launch** — opens the URL in a fresh browser session.
2. **Capture page A** — stores DOM as `<prefix>_page`.
3. **Find peer** — collects all `<a href>` links on the page, filters to the same host, and picks the one with the most similar URL path (longest common path prefix, similar depth). This heuristic targets a page that shares the same template (nav, header, footer) but has different body content.
4. **Navigate to peer** — loads the peer URL and captures its DOM as `<prefix>_peer`.
5. **Compute background** — finds lines present in both page A and the peer. These represent repeated chrome (navigation, footers, sidebars). Stored as `<prefix>_background`.
6. **Navigate back** to the original URL.
7. **Return foreground** — page A DOM minus background lines.

## Stored snapshots

After the call, three snapshots are available in the session:

| Name | Contents |
|------|----------|
| `<prefix>_page` | Full DOM of the target page |
| `<prefix>_peer` | Full DOM of the comparison page |
| `<prefix>_background` | Lines common to both (the chrome) |

You can use these with `browser_dom_compare` for further analysis:

```
# Get foreground content of page A
browser_dom_compare a="fetch_page" subtract="fetch_background"

# Get foreground content of peer page
browser_dom_compare a="fetch_peer" subtract="fetch_background"

# Diff the two pages' unique content
browser_dom_compare a="fetch_page" b="fetch_peer"
```

## Limitations

- Background detection is heuristic — it works well for templated sites but may over-strip on pages with few links or very similar structure.
- If no same-site peer link is found, the full DOM is returned without stripping.

## Example

Site: `https://quotes.toscrape.com/`

```
fetch_dom_content url="https://quotes.toscrape.com/" prefix="quotes"
```

Auto-detected peer: `/login`. Stripped 19 lines of shared chrome (nav, footer), returning 105 lines of quote content.

→ [fetch_dom_content-example.json](fetch_dom_content-example.json)
