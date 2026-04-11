import { drainLogs } from '../browser/session.js';
import { captureDom } from '../browser/dom.js';

export default {
  schema: {
    name: 'browser_remember_dom',
    description: 'Capture a named snapshot of the current DOM for later comparison with browser_dom_compare. Use the same lens params for both snapshots when planning to compare.',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        name:      { type: 'string', description: 'Name for this snapshot.' },
        lens:      { type: 'array', items: { type: 'string', enum: ['text', 'media', 'layout', 'code', 'svg', 'none'] }, description: 'Lenses to apply at capture time. Omit for the full DOM. Use "none" to capture an empty snapshot (useful when only console logs are needed).' },
        exclude:   { type: 'string', description: 'Comma-separated CSS selectors to exclude before capturing.' },
        max_chars: { type: 'number', description: 'Character budget (same as browser_see_dom).' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
      required: ['name'],
    },
  },
  async handler({ session }, args) {
    const { name, lens = null, exclude = null, max_chars = null } = args;
    const html = await captureDom(session.page, { lens, exclude, maxChars: max_chars });
    session.snapshots.set(name, { html, lens, timestamp: new Date().toISOString(), chars: html.length });
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: `Snapshot "${name}" saved (${html.length} chars, lens: ${lens ? lens.join('+') : 'full'})` + logs }] };
  },
};
