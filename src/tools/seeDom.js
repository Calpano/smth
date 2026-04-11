import { drainLogs } from '../browser/session.js';
import { searchPageText } from '../browser/text.js';
import { seeDomScript } from '../scripts.js';

export default {
  schema: {
    name: 'browser_see_dom',
    description: 'Return a filtered view of the live DOM as compact HTML. Pass "search" to instead find text matches with surrounding context lines.',
    annotations: { readOnlyHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        lens: {
          type: 'array',
          items: { type: 'string', enum: ['text', 'media', 'layout', 'code', 'svg', 'css-classes', 'none'] },
          description: 'Active lenses. Omit for the full DOM. One or more of: "text" (visible text, labels, values), "media" (images, SVG, canvas), "layout" (structure, positioning), "code" (event handlers, data-* attributes, JS hooks), "svg" (all SVG elements and attributes — shapes, paths, gradients, transforms, clip-paths), "css-classes" (returns [{class, count}] sorted by frequency instead of HTML), "none" (returns empty DOM — useful when only console logs are needed).',
        },
        search: {
          type: 'array',
          items: { type: 'string' },
          description: 'Search terms. When provided, returns matching lines from page text with 3 lines of context above and below each match instead of serialized HTML.',
        },
        custom: {
          type: 'object',
          description: 'Extra classifications merged (union) with the built-ins. Shape: { elements?: { tagName: category[] }, attributes?: { global?: { attrName: category[] }, by_element?: { tagName: { attrName: category[] } } } }',
        },
        justCount: {
          type: 'boolean',
          description: 'If true, return only counts: { chars, elements, attributes }. Useful for estimating token cost before fetching.',
        },
        max_chars: {
          type: 'number',
          description: 'Character budget. Deepens the DOM one level at a time until the output would exceed this limit.',
        },
        exclude: {
          type: 'string',
          description: 'Comma-separated CSS selectors. Matching elements and their entire subtrees are removed before lens filtering. Example: ".sidebar, #cookie-banner, script"',
        },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const logsNow = () => (args.getConsoleLogs ? drainLogs(session) : '');

    if (args.search) {
      const terms = Array.isArray(args.search) ? args.search : [args.search];
      const results = await searchPageText(page, terms);
      const logs = logsNow();
      if (!results.length) return { content: [{ type: 'text', text: '(no matches)' + logs }] };
      const out = results.map(r => `[${r.term}]\n${r.context}`).join('\n\n---\n\n');
      return { content: [{ type: 'text', text: out + logs }] };
    }

    const script = seeDomScript
      .replaceAll('__LENS__',       JSON.stringify(args.lens      ?? null))
      .replaceAll('__CUSTOM__',     JSON.stringify(args.custom    ?? null))
      .replaceAll('__JUST_COUNT__', JSON.stringify(args.justCount ?? false))
      .replaceAll('__MAX_CHARS__',  JSON.stringify(args.max_chars ?? null))
      .replaceAll('__EXCLUDE__',    JSON.stringify(args.exclude   ?? null));
    const result = await page.evaluate(script);
    const logs = logsNow();
    return { content: [{ type: 'text', text: (typeof result === 'string' ? result : JSON.stringify(result, null, 2)) + logs }] };
  },
};
