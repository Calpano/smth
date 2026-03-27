# browser_see_colors — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — all colors, known values present

**Call:**
```
browser_see_colors
```

**Expect:**
- Valid JSON array.
- Contains entries for known colors used in test.html:
  - `#1a1a2e` — nav and footer background.
  - `#4a90d9` — button background.
  - `#e74c3c` — `.err` text color.
  - `#27ae60` — `.ok` text color.
- Each entry has `color`, `count`, and `categories` (array containing at least one of `"text"`, `"background"`, `"border"`).

---

## Test 2 — `only` filter

**Call:**
```
browser_see_colors only=["background"]
```

**Expect:**
- All returned entries have `categories` containing only `"background"`.
- No text-only colors in results.
- `#1a1a2e` still present (it's a background).

---

## Test 3 — `colors` filter

**Call:**
```
browser_see_colors colors=["#4a90d9", "#e74c3c"]
```

**Expect:**
- Returns exactly 2 (or fewer) entries, only for the given colors.
- Any other color is absent.

---

## Test 4 — `where` flag

**Call:**
```
browser_see_colors where=true colors=["#1a1a2e"]
```

**Expect:**
- The `#1a1a2e` entry has a `where` key.
- `where.background` lists selectors including `nav` or `footer`.

---

## Test 5 — no results

**Call:**
```
browser_see_colors colors=["#badbad"]
```

**Expect:** Returns an empty array `[]` or a result with no matching entries. Does not throw.
