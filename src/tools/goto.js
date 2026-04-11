import { drainLogs } from '../browser/session.js';
import { resolveTarget, gotoPage } from '../browser/navigate.js';

export default {
  schema: {
    name: 'browser_goto',
    description: 'Navigate the existing browser session to a new URL without relaunching the browser.',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        url: { type: 'string', description: 'URL or file path to navigate to.' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
      required: ['url'],
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const finalUrl = await gotoPage(page, args.url);
    const title = await page.title();
    const note = finalUrl !== resolveTarget(args.url) ? ` (localhost unreachable; use host.docker.internal)` : '';
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: `Navigated to: ${title}${note}` + logs }] };
  },
};
