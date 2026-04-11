// DOM capture/serialisation helpers that run inside the page via evaluate().

import { seeDomScript } from '../scripts.js';

// Serialize the live DOM as compact HTML.
// Currently unused by smth tools — retained for future use / hover debugging.
export async function serializeDOM(page) {
  return page.evaluate(() => {
    const DEFAULT_ATTRS = {
      INPUT: { type: 'text' },
      FORM: { method: 'get' },
      SCRIPT: { type: 'text/javascript' },
      LINK: { media: 'all' },
    };
    const VOID = new Set(['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr']);

    function serializeNode(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        return node.textContent.replace(/\s+/g, ' ').trim();
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return '';

      const tag = node.tagName.toLowerCase();
      const skipContent = tag === 'script' || tag === 'style';
      const defaults = DEFAULT_ATTRS[node.tagName] || {};

      const attrs = Array.from(node.attributes)
        .filter(a => !(a.name in defaults && defaults[a.name] === a.value))
        .map(a => a.value === '' ? a.name : `${a.name}="${a.value}"`)
        .join(' ');

      const openTag = attrs ? `<${tag} ${attrs}>` : `<${tag}>`;
      if (VOID.has(tag)) return openTag;
      if (skipContent) return `${openTag}</${tag}>`;

      const children = Array.from(node.childNodes).map(serializeNode).filter(Boolean).join('');
      return `${openTag}${children}</${tag}>`;
    }

    return serializeNode(document.documentElement);
  });
}

// Run see-dom.js with given params and return the result string.
// Output has newlines between tags so line-level diffs (snapshots) are meaningful.
export async function captureDom(page, { lens = null, custom = null, maxChars = null, exclude = null } = {}) {
  if (lens && lens.includes('none')) return '';
  const script = seeDomScript
    .replaceAll('__LENS__',       JSON.stringify(lens))
    .replaceAll('__CUSTOM__',     JSON.stringify(custom))
    .replaceAll('__JUST_COUNT__', 'false')
    .replaceAll('__MAX_CHARS__',  JSON.stringify(maxChars))
    .replaceAll('__EXCLUDE__',    JSON.stringify(exclude));
  const result = await page.evaluate(script);
  const html = typeof result === 'string' ? result : JSON.stringify(result, null, 2);
  // Put each opening tag on its own line for meaningful line-level diffs.
  // This ensures orphaned text (e.g. from <title>) doesn't get merged with the next tag.
  return html.replace(/<(?!\/)/g, '\n<').trim();
}
