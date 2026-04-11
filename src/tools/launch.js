import { browserSessions } from '../state.js';
import { launchBrowser, drainLogs } from '../browser/session.js';

export default {
  needsSession: false,
  schema: {
    name: 'browser_launch',
    description: 'Launch a new browser session and open a URL. Always call this before any other browser_ tool. Replaces any existing session.',
    annotations: { readOnlyHint: false, destructiveHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        url: { type: 'string', description: 'URL or file path to navigate to.' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
      required: ['url'],
    },
  },
  async handler({ sessionId }, args) {
    const msg = await launchBrowser(sessionId, args.url);
    const logs = args.getConsoleLogs ? drainLogs(browserSessions.get(sessionId)) : '';
    return { content: [{ type: 'text', text: msg + logs }] };
  },
};
