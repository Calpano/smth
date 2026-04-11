import { drainLogs } from '../browser/session.js';
import { seeColorPairsScript } from '../scripts.js';

export default {
  schema: {
    name: 'browser_see_color_pairs',
    description: 'For every visible text element, find its text color and effective background color (walking up the DOM), compute the WCAG 2.2 contrast ratio, and return all pairs with AA/AAA pass/fail flags sorted by usage count.',
    annotations: { readOnlyHint: true },
    inputSchema: { type: 'object', properties: { getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' } } },
  },
  async handler({ session }, args) {
    const result = await session.page.evaluate(seeColorPairsScript);
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) + logs }] };
  },
};
