import { drainLogs } from '../browser/session.js';
import { resolveSelector, findElement } from '../browser/select.js';

export default {
  schema: {
    name: 'browser_click',
    description: 'Click an element on the current page. Waits for any navigation triggered by the click to complete before returning.',
    annotations: { readOnlyHint: false, destructiveHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        id:       { type: 'string', description: 'HTML element id (without #). Preferred when available.' },
        selector: { type: 'string', description: 'CSS selector. Supports the ":has-text(\'substring\')" extension at the end to match the innermost element whose visible text contains the substring (case-insensitive). Example: "button:has-text(\'Submit\')".' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const sel = resolveSelector(args);
    const handle = await findElement(page, sel);
    try {
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => null),
        handle.click(),
      ]);
    } finally {
      await handle.dispose();
    }
    const title = await page.title();
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: `Clicked ${sel}. Page: ${title}` + logs }] };
  },
};
