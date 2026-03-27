# browser_dom_compare — test script

---

## Setup

```
browser_launch url="test.html"
browser_remember_dom name="home" lens=["text"]
browser_goto url="test-b.html"
browser_remember_dom name="articles" lens=["text"]
browser_goto url="test.html"
```

---

## Test 1 — diff mode: two different pages

**Call:**
```
browser_dom_compare a="home" b="articles"
```

**Expect:**
- Returns a non-empty diff.
- `-` lines: content unique to home page (`Welcome to smth test page`, `Enter your name`, `alpha bravo charlie`, etc.).
- `+` lines: content unique to articles page (`Articles`, `sierra tango uniform`, `Xray yankee zulu`).
- Nav and footer lines shared by both pages are **absent** from the diff (they are background).

---

## Test 2 — diff mode: identical snapshots

**Calls:**
```
browser_remember_dom name="copy-a" lens=["text"]
browser_remember_dom name="copy-b" lens=["text"]
browser_dom_compare a="copy-a" b="copy-b"
```

**Expect:** Returns `(no diff)`.

---

## Test 3 — diff mode: before/after a DOM change

```
browser_launch url="test.html"
browser_remember_dom name="before" lens=["text"]
browser_type id="name-input" text="TestUser"
browser_remember_dom name="after" lens=["text"]
browser_dom_compare a="before" b="after"
```

**Expect:**
- `+` line shows `value="TestUser"` added to the input (or a text representation of the typed value).
- All other lines unchanged.

---

## Test 4 — subtract mode: foreground content

First, build a background snapshot by intersecting home and articles page:

```
browser_launch url="test.html"
browser_remember_dom name="pg-home" lens=["text"]
browser_goto url="test-b.html"
browser_remember_dom name="pg-articles" lens=["text"]
```

Manually store the background (or use `fetch_dom_content` — see that test). For this test, use the diff from Test 1 to identify background lines. Then:

```
browser_dom_compare a="home" b="articles"
```

The lines **absent** from this diff are background. To test subtract mode, capture a background snapshot explicitly by navigating twice and storing the intersection (this is what `fetch_dom_content` automates). Use the snapshots from `fetch_dom_content-test.md` if that test ran first:

```
browser_dom_compare a="fetch_page" subtract="fetch_background"
```

**Expect:**
- Returns only the home page's unique content: form, buttons, known text section.
- Does NOT contain nav links or footer copyright text.
- Content is meaningful and readable.

---

## Test 5 — subtract: empty result

```
browser_remember_dom name="empty-test" lens=["text"]
browser_dom_compare a="empty-test" subtract="empty-test"
```

**Expect:** Returns `(empty after background subtraction)` — subtracting a snapshot from itself leaves nothing.

---

## Test 6 — missing snapshot

**Call:**
```
browser_dom_compare a="no-such-snapshot" b="home"
```

**Expect:** Throws error mentioning `"no-such-snapshot" not found` and suggests `browser_doms`.

---

## Test 7 — missing b and subtract

**Call:**
```
browser_dom_compare a="home"
```

**Expect:** Throws `Provide b ... or subtract ...`.
