# browser_launch — test script

---

## Test 1 — local file

**Call:**
```
browser_launch url="test.html"
```

**Expect:**
- Returns a string starting with `Launched:`.
- Title contains `smth test — home`.
- No error thrown.

---

## Test 2 — remote URL

**Call:**
```
browser_launch url="https://example.com"
```

**Expect:**
- Returns `Launched: Example Domain` (or similar).
- No error thrown.

---

## Test 3 — replaces existing session

**Calls (sequential):**
```
browser_launch url="test.html"
browser_launch url="test-b.html"
```

**Expect:**
- Second call succeeds and returns the title of `test-b.html` (`smth test — articles`).
- The browser was cleanly restarted (no error about existing session).

---

## Test 4 — invalid URL

**Call:**
```
browser_launch url="https://this-domain-does-not-exist-smth-test.invalid"
```

**Expect:** Throws an error (navigation timeout or DNS failure). Does not hang indefinitely (timeout ≤ 30 s).

---

## Test 5 — localhost reaches the host (requires a local server on port 9999 to NOT be running)

**Call:**
```
browser_launch url="http://localhost:9999"
```

**Expect:** A descriptive connection-refused error. `localhost` is resolved to the host rather than the URL being rewritten, so the failure is the host's own refusal — and with a server actually listening on 9999, the page loads with its `Host` header still reading `localhost:9999`.
