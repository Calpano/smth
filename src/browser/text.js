// Page text extraction helpers for browser_read_text and browser_see_dom (search mode).

// Return the page as Markdown plus an "Interactive Elements" section with
// stable CSS selectors for every interactive element.
export async function readPageText(page) {
  return page.evaluate(() => {
    const HEADING_MAP = { H1: '#', H2: '##', H3: '###', H4: '####', H5: '#####', H6: '######' };
    const lines = [];

    function walkText(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(text);
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return;
      const tag = node.tagName;
      if (tag === 'SCRIPT' || tag === 'STYLE') return;

      const idSuffix = node.id ? ` {#${node.id}}` : '';

      if (HEADING_MAP[tag]) {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(`\n${HEADING_MAP[tag]} ${text}${idSuffix}\n`);
        return;
      }
      if (tag === 'P') {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(`\n${text}${idSuffix}\n`);
        return;
      }
      if (tag === 'LI') {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(`- ${text}${idSuffix}`);
        return;
      }
      Array.from(node.childNodes).forEach(walkText);
    }

    walkText(document.body);

    // Generate a minimal unique CSS selector for an element.
    function getSelector(el) {
      if (el.id) return '#' + el.id;
      const parts = [];
      let node = el;
      while (node && node.tagName && node !== document.body) {
        if (node.id) { parts.unshift('#' + node.id); break; }
        let part = node.tagName.toLowerCase();
        const same = Array.from(node.parentElement?.children ?? []).filter(c => c.tagName === node.tagName);
        if (same.length > 1) part += ':nth-of-type(' + (same.indexOf(node) + 1) + ')';
        parts.unshift(part);
        node = node.parentElement;
      }
      return parts.join(' > ');
    }

    const interactive = [];
    document.querySelectorAll('a[href], button, input, select, textarea, [onclick], [onmouseover], [role="button"], [role="link"], [role="tab"], [role="menuitem"]').forEach(el => {
      const label = el.getAttribute('aria-label') || el.getAttribute('placeholder') ||
        el.getAttribute('title') || el.textContent.replace(/\s+/g, ' ').trim().slice(0, 60) || '';
      interactive.push({ id: el.id || null, selector: getSelector(el), tag: el.tagName.toLowerCase(), label });
    });
    // SVG elements with cursor:pointer or click handlers (not already captured above)
    const seen = new Set(interactive.map(i => i.selector));
    document.querySelectorAll('svg, svg *').forEach(el => {
      const sel = getSelector(el);
      if (seen.has(sel)) return;
      const hasClick = el.hasAttribute('onclick') || el.hasAttribute('onmouseover');
      const hasCursor = window.getComputedStyle(el).cursor === 'pointer';
      const hasRole = el.hasAttribute('role');
      if (!hasClick && !hasCursor && !hasRole) return;
      const label = el.getAttribute('aria-label') || el.getAttribute('title') ||
        el.textContent?.replace(/\s+/g, ' ').trim().slice(0, 60) || '';
      interactive.push({ id: el.id || null, selector: sel, tag: el.tagName.toLowerCase(), label });
    });

    let result = lines.join('\n');
    if (interactive.length) {
      result += '\n\n## Interactive Elements\n';
      interactive.forEach(({ id, selector, tag, label }) => {
        const ref = id ? '#' + id : selector;
        result += `\n- [${tag}] ${ref}${label ? ': ' + label : ''}`;
      });
    }
    return result;
  });
}

// Search page innerText for terms and return surrounding context lines.
// Each result: { term, context } where context is a slice of lines with
// a ">>> " marker on the matching line.
export async function searchPageText(page, terms, context = 3) {
  const text = await page.evaluate(() => document.body.innerText);
  const lines = text.split('\n');
  const results = [];
  const covered = []; // [start, end] ranges already included

  for (const term of terms) {
    const lower = term.toLowerCase();
    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].toLowerCase().includes(lower)) continue;
      const start = Math.max(0, i - context);
      const end = Math.min(lines.length - 1, i + context);
      if (covered.some(([s, e]) => start <= e && end >= s)) continue;
      covered.push([start, end]);
      const chunk = lines.slice(start, end + 1).map((l, idx) => {
        const lineNo = start + idx;
        return (lineNo === i ? '>>> ' : '    ') + l;
      }).join('\n');
      results.push({ term, context: chunk });
    }
  }
  return results;
}
