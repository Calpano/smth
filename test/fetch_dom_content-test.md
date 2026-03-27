# fetch_dom_content — test script

---

## Test 1 — basic flow with local test pages

The local test pages are designed for this: `test.html` and `test-b.html` share nav and footer (background) but have different body content (foreground).

**Call:**
```
fetch_dom_content url="test.html"
```

**Expect:**
- Returns a metadata header:
  ```
  # fetch_dom_content: <resolved url for test.html>
  # peer: <resolved url for test-b.html>
  # background: N lines stripped, M lines remain
  # snapshots: fetch_page, fetch_peer, fetch_background
  ```
- `peer` URL points to `test-b.html` (the most similar same-site link from the nav).
- `background: N lines stripped` > 0 (nav and footer were detected and stripped).
- Returned content contains:
  - `Welcome to smth test page` (home page h1).
  - Form field labels (`Name:`, `Email:`).
  - `alpha bravo charlie` (unique text from card-text).
  - `Error: something went wrong` and `Success: operation completed`.
- Returned content does NOT contain:
  - `Home`, `Articles`, `About` (nav links — background).
  - `© 2024 smth test site` (footer — background).

---

## Test 2 — snapshots stored correctly

After Test 1, call:

```
browser_doms
```

**Expect:** Lists `fetch_page`, `fetch_peer`, `fetch_background`.

```
browser_dom_compare a="fetch_page" subtract="fetch_background"
```

**Expect:** Same foreground content as the body of Test 1 output.

```
browser_dom_compare a="fetch_peer" subtract="fetch_background"
```

**Expect:** Articles-page unique content (`sierra tango uniform`, `Xray yankee zulu`, `Article one`, `About this site`). No nav/footer.

---

## Test 3 — custom lens

**Call:**
```
fetch_dom_content url="test.html" lens=["layout"]
```

**Expect:**
- Background detection still works (same nav/footer structure present in both pages).
- Returned content shows layout structure of the home page body (div hierarchy, class names).
- Stored snapshots use `lens:layout`.

---

## Test 4 — custom prefix

**Call:**
```
fetch_dom_content url="test.html" prefix="home"
```

**Expect:**
- `browser_doms` lists `home_page`, `home_peer`, `home_background`.
- Previous `fetch_*` snapshots (if any) are still present.

---

## Test 5 — page with no same-site links

**Call:**
```
fetch_dom_content url="https://example.com"
```

Note: `example.com` has minimal links, all external.

**Expect:**
- Returns the full DOM with a message like `No same-site peer link found. Returning full DOM.`
- Does not crash.
- Does not hang.

---

## Test 6 — rich real-world site

**Call:**
```
fetch_dom_content url="https://en.wikipedia.org/wiki/HTML"
```

**Expect:**
- Peer is detected (another Wikipedia article — same `en.wikipedia.org` host, similar path depth).
- Background contains the Wikipedia nav, sidebar, and footer (hundreds of lines).
- Foreground contains the article body text about HTML.
- `# background: N lines stripped` — N should be substantial (> 100).
- Content is meaningfully shorter than the raw DOM.

---

## Test 7 — browser session state after call

After `fetch_dom_content`, call:

```
browser_read_text
```

**Expect:**
- Returns text of the **original** URL (test.html), not the peer.
- Confirms `fetch_dom_content` navigates back to the original URL before returning.
