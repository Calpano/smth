import { drainLogs } from '../browser/session.js';
import { resolveSelector, findElement } from '../browser/select.js';
import { lineDiff } from '../util/diff.js';

export default {
  schema: {
    name: 'browser_hover',
    description: 'Hover over an element and return a before/after diff of visible text and interactive attributes (text+code lens).',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        id:       { type: 'string', description: 'HTML element id (without #). Preferred when available.' },
        selector: { type: 'string', description: 'CSS selector. Supports the ":has-text(\'substring\')" extension at the end to match the innermost element whose visible text contains the substring (case-insensitive). Example: "a:has-text(\'Learn more\')".' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const sel = resolveSelector(args);
    const handle = await findElement(page, sel);
    try {
      // Move to (0, 0) first so any CSS hover effects from the previous action are cleared,
      // giving a consistent "before" baseline regardless of prior mouse position.
      await page.mouse.move(0, 0);
      await new Promise(r => setTimeout(r, 100));
      // Use innerText (visibility-aware) so CSS-toggled content (e.g. tooltips) shows in the diff.
      const before = await page.evaluate(() => document.body.innerText);
      await handle.hover();
      await new Promise(r => setTimeout(r, 300));
      const after = await page.evaluate(() => document.body.innerText);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: lineDiff(before, after) + logs }] };
    } finally {
      await handle.dispose();
    }
  },
};
