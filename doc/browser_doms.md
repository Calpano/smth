## browser_doms

Lists all named DOM snapshots stored in the current session.

No parameters.

Returns one line per snapshot: `name  chars  lens  timestamp`.

Snapshots are session-scoped and lost when the session ends.

### Example

After calling `browser_remember_dom` for `home` (page 1) and `page2` (page 2):

```
browser_doms
```

→ [browser_doms-example.json](browser_doms-example.json)
