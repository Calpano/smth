# browser_see_fonts — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — known font families

**Call:**
```
browser_see_fonts
```

**Expect:**
- Valid JSON array.
- Contains an entry with `family` including `"Georgia"` — used for body text.
- Contains an entry with `family` including `"Arial"` — used for nav links, buttons, footer.
- Each entry has `sizes` (array of sizes with counts) and `weights` (array of weights with counts).
- Each entry has a `count` (total element usage count) > 0.

---

## Test 2 — structure

In the same output:

- `sizes` entries each have a `size` (CSS value string) and a `count`.
- `weights` entries each have a `weight` and a `count`.
- No duplicate family names in the top-level array.

---

## Test 3 — on a page with system fonts

```
browser_goto url="https://example.com"
browser_see_fonts
```

**Expect:** Returns valid JSON. At least one font family entry. No crash.

---

## Test 4 — empty-ish page

```
browser_launch url="test-b.html"
browser_see_fonts
```

**Expect:**
- Still returns Georgia and Arial (same stylesheet as test.html).
- No error.
