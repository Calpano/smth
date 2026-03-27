# browser_hover — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — hover reveals tooltip (by id)

The `#tip-host` element has a child `.tip-text` with `display: none` that becomes `display: block` on hover.

**Call:**
```
browser_hover id="tip-host"
```

**Expect:**
- Returns a non-empty diff.
- A `+` line containing `Tooltip appeared!` (the tooltip text is now visible).
- No `-` line for the same text (it wasn't visible before).
- Diff is compact — only meaningful text/code changes, not the full DOM.

---

## Test 2 — hover with no visible change

**Call:**
```
browser_hover id="page-title"
```

**Expect:**
- Returns `(no diff)` — hovering a plain heading changes nothing in the text+code lens.

---

## Test 3 — hover by CSS selector

**Call:**
```
browser_hover selector="#tip-host"
```

**Expect:** Same result as Test 1 (id and `#id` selector are equivalent).

---

## Test 4 — hover button (style change, no text change)

**Call:**
```
browser_hover id="btn-primary"
```

**Expect:**
- The button background color changes on hover (CSS `button:hover`), but this is a style attribute change.
- Whether diff is empty or shows the style change depends on lens coverage. Either outcome is acceptable — verify it does not crash and returns within 1 s.

---

## Test 5 — element not found

**Call:**
```
browser_hover id="ghost-element"
```

**Expect:** Throws an error (Puppeteer `hover` fails on missing selector). Error message is descriptive.

---

## Test 6 — missing id and selector

**Call:**
```
browser_hover
```

**Expect:** Throws `Provide id or selector.`
