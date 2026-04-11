// Selector resolution for browser_click, browser_hover, browser_type.
//
// Supports standard CSS plus a ":has-text('substring')" extension that matches
// the innermost element whose visible text contains the substring.

// Turn { id?, selector? } args into a single CSS selector string.
export function resolveSelector(args) {
  const { id, selector } = args;
  if (id) return `#${id}`;
  if (selector) return selector;
  throw new Error('Provide id or selector.');
}

// Parse a trailing ":has-text('...')" extension on a selector.
// Returns { base, text } or null if not present.
// Supports single or double quotes; backslash-escapes the quote char.
export function parseHasText(selector) {
  const m = selector.match(/^(.*):has-text\(\s*(['"])((?:\\.|(?!\2).)*)\2\s*\)\s*$/);
  if (!m) return null;
  const base = m[1].trim() || '*';
  const text = m[3].replace(/\\(['"\\])/g, '$1');
  return { base, text };
}

// Resolve a selector to a Puppeteer ElementHandle. Supports standard CSS plus
// the ":has-text('substring')" extension (case-insensitive substring match,
// picks the innermost matching element). Caller is responsible for disposing
// the returned handle.
export async function findElement(page, selector) {
  const parsed = parseHasText(selector);
  if (!parsed) {
    const handle = await page.$(selector);
    if (!handle) throw new Error(`Element not found: ${selector}`);
    return handle;
  }
  const handle = await page.evaluateHandle(({ base, text }) => {
    const needle = text.toLowerCase();
    let candidates;
    try { candidates = Array.from(document.querySelectorAll(base)); }
    catch { return null; }
    const matches = candidates.filter(el => {
      const t = (el.innerText ?? el.textContent ?? '').toLowerCase();
      return t.includes(needle);
    });
    // Pick the innermost match: one with no matching descendant among the matches.
    return matches.find(el => !matches.some(o => o !== el && el.contains(o))) || null;
  }, parsed);
  const el = handle.asElement();
  if (!el) {
    await handle.dispose();
    throw new Error(`Element not found: ${selector}`);
  }
  return el;
}
