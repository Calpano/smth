# browser_goto — test script

Requires an active session. Call `browser_launch` first.

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — navigate to peer page

**Call:**
```
browser_goto url="test-b.html"
```

**Expect:**
- Returns `Navigated to: smth test — articles` (or similar).
- No browser restart (session preserved — snapshots stored before goto should still exist).

---

## Test 2 — navigate back

**Call:**
```
browser_goto url="test.html"
```

**Expect:** Returns title of `test.html`.

---

## Test 3 — session persists across goto (snapshot survives)

**Calls (sequential):**
```
browser_remember_dom name="before-goto"
browser_goto url="test-b.html"
browser_doms
```

**Expect:** `browser_doms` still lists `before-goto`. Confirms the session (and its snapshot map) was not replaced by `browser_goto`.

---

## Test 4 — remote URL

**Call:**
```
browser_goto url="https://example.com"
```

**Expect:** Returns `Navigated to: Example Domain`.

---

## Test 5 — call without prior browser_launch

Start a fresh session (no `browser_launch` called):

**Call:**
```
browser_goto url="test.html"
```

**Expect:** Throws `No open browser session. Call browser_launch first.`
