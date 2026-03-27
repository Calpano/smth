# browser_remember_dom — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — basic capture, full DOM

**Call:**
```
browser_remember_dom name="snap1"
```

**Expect:**
- Returns confirmation string: `Snapshot "snap1" saved (N chars, lens: full)`.
- `N` is a positive number (likely > 1000).

---

## Test 2 — capture with text lens

**Call:**
```
browser_remember_dom name="snap-text" lens=["text"]
```

**Expect:**
- Confirmation mentions `lens: text`.
- Char count is smaller than Test 1 (lens filters reduce output).

---

## Test 3 — capture with exclude

**Call:**
```
browser_remember_dom name="snap-no-footer" exclude="footer, nav"
```

**Expect:**
- Char count smaller than Test 1.
- Verify content by comparing:
  ```
  browser_dom_compare a="snap1" b="snap-no-footer"
  ```
  — diff should show nav and footer lines as `-` (removed in snap-no-footer).

---

## Test 4 — capture on different page

```
browser_goto url="test-b.html"
browser_remember_dom name="snap-b"
```

**Expect:**
- Confirmation saved as `snap-b`.
- Comparing with the test.html snapshot shows meaningful differences (different h1, different article content).

---

## Test 5 — overwrite existing snapshot

**Calls:**
```
browser_remember_dom name="snap1"
browser_remember_dom name="snap1"
```

**Expect:** Second call overwrites the first without error. `browser_doms` still shows only one `snap1`.

---

## Test 6 — max_chars limits output

**Call:**
```
browser_remember_dom name="snap-small" max_chars=300
```

**Expect:**
- Confirmation char count is ≤ 300 (or slightly over if minimum depth exceeds budget).
- Snapshot is stored and retrievable.
