# browser_see_visual — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — full page screenshot (default)

**Call:**
```
browser_see_visual
```

**Expect:**
- Returns an embedded base64 PNG image (content type `image/png`).
- Image is visible to the model — confirm the image shows the test page with nav bar, cards, and footer.
- Image height is taller than viewport (captures full scrollable page).

---

## Test 2 — viewport-only screenshot

**Call:**
```
browser_see_visual full_page=false
```

**Expect:**
- Image height ≈ 720 px (default viewport height).
- Shows only the top portion of the page (nav + first card visible).

---

## Test 3 — element-centered screenshot

**Call:**
```
browser_see_visual element_id="card-form" full_page=false
```

**Expect:**
- Image is scrolled to show the form card centered.
- Image height ≈ 720 px (viewport).
- Form inputs and submit button are visible.

---

## Test 4 — zoom

**Call:**
```
browser_see_visual zoom=2 full_page=false
```

**Expect:**
- Content is visibly larger than Test 1.
- Text is readable but magnified.
- After the call, zoom is reset (a subsequent `browser_see_visual` without zoom shows normal size).

---

## Test 5 — custom viewport

**Call:**
```
browser_see_visual width=400 height=300 full_page=false
```

**Expect:**
- Image dimensions are approximately 400 × 300 px.
- Page is rendered in mobile-narrow layout.

---

## Test 6 — device emulation

```
browser_see_visual device="iPhone 15"
```

**Expect:**
- Image shows mobile-width rendering.
- Width is visibly narrower than desktop screenshots.
- No error (device name is valid).

---

## Test 7 — invalid device name

**Call:**
```
browser_see_visual device="NotARealDevice999"
```

**Expect:** Throws `Unknown device "NotARealDevice999". Call browser_list_devices to see available options.`

---

## Test 8 — element not found

**Call:**
```
browser_see_visual element_id="ghost-element"
```

**Expect:** Throws error mentioning `ghost-element not found`.
