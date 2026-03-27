# browser_read_text — test script

---

## Setup

```
browser_launch url="test.html"
```

---

## Test 1 — text content

**Call:**
```
browser_read_text
```

**Expect in output:**
- Heading `# Welcome to smth test page` (h1 → markdown heading).
- Heading `## Buttons`, `## Form`, `## Known text — alpha bravo charlie`.
- Paragraph containing `The quick brown fox`.
- Text `Error: something went wrong` and `Success: operation completed`.

**Must NOT contain:**
- Raw HTML tags (no `<div>`, `<p>`, etc.).
- Script or style content.

---

## Test 2 — Interactive Elements section

**Expect in same output from Test 1:**
- Section header `## Interactive Elements`.
- Entry for `[a]` links (nav links to `test.html`, `test-b.html`).
- Entry for `[button] #btn-primary: Click me`.
- Entry for `[button]` with a CSS selector (the no-id button), e.g. `button:nth-of-type(2)` or similar.
- Entry for `[input] #name-input` with placeholder label `Enter your name`.
- Entry for `[input] #email-input` with placeholder label `Enter email`.
- All entries show either `#id` or a CSS path selector — never an empty ref.

---

## Test 3 — selectors are usable

Take a selector from the Interactive Elements output for the no-ID button and pass it to `browser_click`:

```
browser_click selector="<selector from output>"
```

**Expect:** No error thrown (click succeeded).

---

## Test 4 — on test-b.html

```
browser_goto url="test-b.html"
browser_read_text
```

**Expect:**
- Contains `# Articles`.
- Contains unique content from test-b.html (`sierra tango uniform`, `Xray yankee zulu`).
- Does NOT contain `Welcome to smth test page`.
