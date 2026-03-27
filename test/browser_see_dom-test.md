# browser_see_dom — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — full DOM (no lens)

**Call:**
```
browser_see_dom
```

**Expect:**
- Returns a single HTML string starting with `<html`.
- Contains `<nav>`, `<form`, `<input`, `<button`, `<footer>`.
- Contains all attribute values (id, class, style, href, etc.).
- Script/style element content is stripped (tag present but empty).

---

## Test 2 — text lens

**Call:**
```
browser_see_dom lens=["text"]
```

**Expect:**
- Contains text-bearing elements: `<h1>`, `<p>`, `<button>`, `<label>`, `<input>` (for placeholder/value).
- Does NOT contain pure layout elements without text (bare `<div>` with no matching child text).
- Does NOT contain style attribute values (style is a layout attribute, not text).
- Contains `id` and `class` attributes on matched elements (always included as anchors).

---

## Test 3 — layout lens

**Call:**
```
browser_see_dom lens=["layout"]
```

**Expect:**
- Contains `<div>`, `<nav>`, `<footer>`, `<form>` structural tags.
- Contains `class` and `style` attributes.
- Does NOT contain `placeholder`, `value` (text-only attrs).
- Does NOT contain `onclick`, `onmouseover` (code-only attrs).

---

## Test 4 — code lens

**Call:**
```
browser_see_dom lens=["code"]
```

**Expect:**
- Contains `<a>` with `href`, `<input>` with `type` and `name`, `<form>` with `action` and `method`.
- Contains `<button>` with `type`.
- Does NOT surface layout-only attributes (`style`, `colspan`).

---

## Test 5 — justCount

**Call:**
```
browser_see_dom lens=["text"] justCount=true
```

**Expect:**
- Returns JSON object, not HTML.
- Has keys: `chars` (number), `elements` (object), `attributes` (object).
- `chars` > 0.
- `elements` contains `h1`, `p`, `button`.

---

## Test 6 — max_chars

**Call:**
```
browser_see_dom max_chars=500
```

**Expect:**
- Output is shorter than 500 characters (or only slightly over if depth 1 is unavoidable).
- Still valid, parseable HTML fragment.

---

## Test 7 — exclude

**Call:**
```
browser_see_dom lens=["text"] exclude="nav, footer"
```

**Expect:**
- Nav link text (Home, Articles, About) is absent.
- Footer text (`© 2024 smth test site`) is absent.
- Body content (headings, paragraphs) still present.

---

## Test 8 — search (single term)

**Call:**
```
browser_see_dom search=["alpha"]
```

**Expect:**
- Returns text blocks (not HTML).
- Contains `>>> ` marker on the line with `alpha`.
- Shows 3 lines of context above and below the match.
- Does NOT return HTML tags.

---

## Test 9 — search (multiple terms)

**Call:**
```
browser_see_dom search=["Error", "Success"]
```

**Expect:**
- Two separate blocks separated by `---`.
- Each block has the correct `[term]` header.
- `Error` block marks the `.err` line with `>>>`.
- `Success` block marks the `.ok` line with `>>>`.

---

## Test 10 — search no match

**Call:**
```
browser_see_dom search=["zzznomatch"]
```

**Expect:** Returns `(no matches)`.
