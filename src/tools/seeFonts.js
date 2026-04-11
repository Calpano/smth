import { drainLogs } from '../browser/session.js';
import { seeFontsScript } from '../scripts.js';

export default {
  schema: {
    name: 'browser_see_fonts',
    description: 'Return a JSON report of all fonts in use on the current page, grouped by family with size, weight, and usage count.',
    annotations: { readOnlyHint: true },
    inputSchema: { type: 'object', properties: { getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' } } },
  },
  async handler({ session }, args) {
    const result = await session.page.evaluate(seeFontsScript);
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) + logs }] };
  },
};
