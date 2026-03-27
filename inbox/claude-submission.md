# Claude Plugin Submission — smth

Submission form (local MCP server): https://forms.gle/tyiAZvch1kDADKoP9

---

## Form fields

**Extension name**
smth

**Description / functionality overview**
A local MCP browser server that gives Claude a full Puppeteer/Chromium session via Docker. Supports page screenshots (any device), DOM inspection with lens filtering (text/layout/code/media), WCAG 2.2 color contrast analysis, page interaction (click, type, hover), named DOM snapshots, and automatic foreground content extraction that strips shared site chrome.

**Version**
1.0.0

**GitHub repository**
https://github.com/Calpano/smth

**Transport type**
Streamable HTTP — `http://localhost:3000/mcp` (legacy SSE also available at `/sse`)

**Privacy policy**
smth runs entirely on the user's local machine inside a Docker container. It does not transmit any data to external services. No telemetry, no analytics. Full statement in README.md.

**Support contact**
[your email]

---

## 3 working examples

### Example 1 — WCAG contrast audit

**User prompt:** "Check if the colors on https://quotes.toscrape.com/ pass WCAG accessibility requirements"

**Tool usage:**
```
browser_launch url="https://quotes.toscrape.com/"
browser_see_color_pairs
```

**Expected behavior:** Returns all text/background color pairs with WCAG 2.2 contrast ratios and AA/AAA flags. Claude can immediately identify which combinations fail (e.g., white on light blue at 2.55:1 fails AA).

---

### Example 2 — Extract page content without chrome

**User prompt:** "Get just the article content from https://quotes.toscrape.com/ without the navigation and footer"

**Tool usage:**
```
fetch_dom_content url="https://quotes.toscrape.com/"
```

**Expected behavior:** Auto-detects the login page as a peer, computes shared chrome (19 lines), returns the 10 quotes with their authors and tags — nothing else.

---

### Example 3 — Mobile preview

**User prompt:** "Show me what https://quotes.toscrape.com/ looks like on an iPhone 15 Pro"

**Tool usage:**
```
browser_launch url="https://quotes.toscrape.com/"
browser_see_visual device="iPhone 15 Pro"
```

**Expected behavior:** Emulates the device and returns an embedded PNG screenshot displayed inline to the model.

---

## Installation instructions (for form)

Requirements: Docker Desktop or Docker Engine

```bash
git clone https://github.com/maxvolkel/smth.git
cd smth
docker compose up -d
```

Add to project `.mcp.json`:
```json
{
  "mcpServers": {
    "smth": { "type": "http", "url": "http://localhost:3000/mcp" }
  }
}
```

Restart Claude Code. Tools appear as `mcp__smth__browser_*`.
