# browser_click — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — click by id (no navigation)

**Call:**
```
browser_click id="btn-primary"
```

**Expect:**
- Returns `Clicked #btn-primary. Page: smth test — home` (or similar).
- No navigation occurred (same page title).
- No error.

---

## Test 2 — click by CSS selector (element without id)

First, get the selector from `browser_read_text` output for the no-ID button (e.g. `button.btn-secondary` or `button:nth-of-type(2)`).

**Call:**
```
browser_click selector="button.btn-secondary"
```

**Expect:**
- Returns confirmation with `Clicked button.btn-secondary`.
- No error (element found by selector).

---

## Test 3 — click triggers navigation and tool waits

Clicking the submit button on the form navigates to `test-b.html`.

**Call:**
```
browser_click id="submit-btn"
```

**Expect:**
- Returns `Clicked #submit-btn. Page: smth test — articles` (title of test-b.html).
- Confirms navigation completed before return — next `browser_read_text` call should immediately show test-b.html content.

Verify by calling:
```
browser_read_text
```
— output must contain `Articles`, not `Welcome to smth test page`.

---

## Test 4 — click a nav link (navigation)

```
browser_launch url="test.html"
browser_click selector="nav a[href='test-b.html']"
```

**Expect:**
- Tool waits for navigation and returns test-b.html title.

---

## Test 5 — element not found

**Call:**
```
browser_click id="nonexistent-element"
```

**Expect:** Throws an error mentioning the element was not found. Does not hang.

---

## Test 6 — missing id and selector

**Call:**
```
browser_click
```

**Expect:** Throws `Provide id or selector.`
