import { drainLogs } from '../browser/session.js';
import { readPageText } from '../browser/text.js';

export default {
  schema: {
    name: 'browser_read_text',
    description: 'Return the visible text of the current page as Markdown, plus a list of interactive elements each annotated with a CSS selector usable with browser_click, browser_hover, and browser_type.',
    annotations: { readOnlyHint: true },
    inputSchema: { type: 'object', properties: { getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' } } },
  },
  async handler({ session }, args) {
    const text = await readPageText(session.page);
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: text + logs }] };
  },
};
