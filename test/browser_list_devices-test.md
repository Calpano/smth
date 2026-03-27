# browser_list_devices — test script

Stateless. No browser session required.

---

## Test 1 — basic output

**Call:**
```
browser_list_devices
```

**Expect:**
- Returns a newline-separated list of device name strings.
- List contains at least 50 entries.
- Known entries present: `iPhone 15`, `iPad`, `Pixel 7`, `Galaxy S9+`.
- No duplicates.
- Alphabetically sorted.

---

## Test 2 — call before browser_launch

**Call:**
```
browser_list_devices
```
(with no active session — i.e. at the very start of a conversation)

**Expect:** Same output as Test 1. Confirms the tool is truly stateless and does not require a browser session.
