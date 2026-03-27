# browser_doms — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — empty session

**Call (before any snapshots):**
```
browser_doms
```

**Expect:** Returns `(no snapshots)`.

---

## Test 2 — single snapshot

**Calls:**
```
browser_remember_dom name="alpha"
browser_doms
```

**Expect:**
- Returns one line.
- Line contains `alpha`, a char count, `lens:full`, and an ISO timestamp.
- Format: `alpha  <N> chars  lens:full  <timestamp>`.

---

## Test 3 — multiple snapshots with different lenses

**Calls:**
```
browser_remember_dom name="full-dom"
browser_remember_dom name="text-only" lens=["text"]
browser_remember_dom name="layout-only" lens=["layout"]
browser_doms
```

**Expect:**
- Three lines returned.
- `full-dom` shows `lens:full`.
- `text-only` shows `lens:text`.
- `layout-only` shows `lens:layout`.
- Char counts differ (full > text ≈ layout).

---

## Test 4 — snapshots persist across navigation

**Calls:**
```
browser_remember_dom name="page-a"
browser_goto url="test-b.html"
browser_remember_dom name="page-b"
browser_goto url="test.html"
browser_doms
```

**Expect:** Both `page-a` and `page-b` listed. Confirms snapshot map survives navigation.

---

## Test 5 — snapshot replaced shows updated timestamp

**Calls:**
```
browser_remember_dom name="snap"
# wait a moment or capture again immediately
browser_remember_dom name="snap"
browser_doms
```

**Expect:** Only one `snap` entry (no duplicates). Timestamp reflects the most recent capture.
