# browser_see_color_pairs — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — basic output

**Call:**
```
browser_see_color_pairs
```

**Expect:**
- Valid JSON array.
- Each entry has: `text`, `background`, `contrast` (number), `aa` (boolean), `aaa` (boolean), `count` (number).
- `contrast` is a positive number (WCAG ratio, e.g. `4.5`).
- `aa` is `true` when `contrast >= 4.5`, `false` otherwise.
- `aaa` is `true` when `contrast >= 7.0`, `false` otherwise.
- Array is sorted by `count` descending.

---

## Test 2 — known high-contrast pair

The nav contains white/light text (`#eee`) on dark background (`#1a1a2e`). This is a high-contrast pair.

**Expect in output:**
- An entry where `text` ≈ `#eeeeee` and `background` ≈ `#1a1a2e`.
- `contrast` > 10 (very high contrast dark-on-light).
- `aa: true`, `aaa: true`.

---

## Test 3 — known low-contrast pair (if any)

The `.err` text (`#e74c3c`) on white background (`#fff`) has moderate contrast.

**Expect:**
- An entry with `text` ≈ `#e74c3c` and `background` ≈ `#ffffff`.
- `contrast` should be around 3.5–4.5 (fails or borderline AA).

---

## Test 4 — empty page

```
browser_launch url="data:text/html,<p>hi</p>"
browser_see_color_pairs
```

**Expect:** Returns a small array (at least one pair for the default browser text/background). Does not crash.
