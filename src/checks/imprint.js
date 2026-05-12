// Pure helpers for finding and validating a German imprint page. Designed to
// be callable from the MCP tool, a CLI, or tests without MCP plumbing.

// Find a footer-ish link whose visible text matches one of the configured
// labels (case-insensitive). Returns { href, text } or null. We scan footer
// elements first, then fall back to any visible <a>.
export async function findImprintLink(page, linkTexts) {
  return page.evaluate((labels) => {
    const wanted = labels.map(l => l.toLowerCase());
    function visible(el) {
      const cs = window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
      const rect = el.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0;
    }
    function scan(scope) {
      for (const a of scope.querySelectorAll('a[href]')) {
        const txt = (a.textContent ?? '').trim().toLowerCase();
        if (!txt) continue;
        for (const w of wanted) {
          if (txt === w || txt.includes(w)) {
            if (!visible(a)) continue;
            return { href: a.href, text: (a.textContent ?? '').trim() };
          }
        }
      }
      return null;
    }
    for (const sel of ['footer', '[class*="footer"]', '[id*="footer"]']) {
      for (const f of document.querySelectorAll(sel)) {
        const hit = scan(f);
        if (hit) return hit;
      }
    }
    return scan(document);
  }, linkTexts);
}

// Check whether a link matching one of the labels exists anywhere on the page
// (used for `also_check` siblings — we only verify presence, not navigate).
export async function findSiblingLinks(page, labelsList) {
  return page.evaluate((groups) => {
    function visible(el) {
      const cs = window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
      const rect = el.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0;
    }
    const out = [];
    for (const label of groups) {
      const wanted = label.toLowerCase();
      let found = null;
      for (const a of document.querySelectorAll('a[href]')) {
        const txt = (a.textContent ?? '').trim().toLowerCase();
        if (!txt) continue;
        if (txt === wanted || txt.includes(wanted)) {
          if (!visible(a)) continue;
          found = { href: a.href, text: (a.textContent ?? '').trim() };
          break;
        }
      }
      out.push({ label, found: !!found, url: found?.href ?? null });
    }
    return out;
  }, labelsList);
}
