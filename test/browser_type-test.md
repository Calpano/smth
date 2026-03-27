# browser_type — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — type into input by id

**Call:**
```
browser_type id="name-input" text="Alice"
```

**Expect:**
- Returns `Typed into #name-input`.
- Verify by reading the DOM:
  ```
  browser_see_dom lens=["code"] search=["Alice"]
  ```
  — output should show `value="Alice"` on the input.

---

## Test 2 — clear=true replaces previous value

**Calls (sequential):**
```
browser_type id="name-input" text="Bob"
browser_type id="name-input" text="Charlie" clear=true
```

**Expect:**
- After second call, DOM shows `value="Charlie"`, not `valueBobCharlie` or `value="BobCharlie"`.

---

## Test 3 — clear=false appends

**Calls (sequential):**
```
browser_type id="name-input" text="Hello"
browser_type id="name-input" text=" World" clear=false
```

**Expect:**
- DOM shows `value="Hello World"` (concatenated).

---

## Test 4 — type by CSS selector (no id)

First use `browser_read_text` to get the selector for `email-input`. Alternatively:

**Call:**
```
browser_type selector="#email-input" text="test@example.com"
```

**Expect:** Returns confirmation. DOM shows `value="test@example.com"` on the email input.

---

## Test 5 — type and submit form (triggers navigation)

```
browser_type id="name-input" text="Dave"
browser_click id="submit-btn"
```

**Expect:**
- Second call waits for navigation.
- Page title is now `smth test — articles`.
- URL query string contains `name=Dave` (form GET action).

---

## Test 6 — element not found

**Call:**
```
browser_type id="ghost-input" text="hello"
```

**Expect:** Throws an error (focus fails on missing element).

---

## Test 7 — missing text param

**Call:**
```
browser_type id="name-input"
```

**Expect:** Schema validation error or throws about missing `text` parameter.
