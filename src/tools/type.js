import { drainLogs } from '../browser/session.js';
import { resolveSelector, findElement } from '../browser/select.js';

export default {
  schema: {
    name: 'browser_type',
    description: 'Type text into a form field. Clears the field first by default.',
    annotations: { readOnlyHint: false, destructiveHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        id:       { type: 'string', description: 'HTML element id (without #). Preferred when available.' },
        selector: { type: 'string', description: 'CSS selector. Supports the ":has-text(\'substring\')" extension at the end to match the innermost element whose visible text contains the substring (case-insensitive).' },
        text:     { type: 'string', description: 'Text to type into the element.' },
        clear:    { type: 'boolean', description: 'Clear the field before typing (default: true).' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
      required: ['text'],
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const sel = resolveSelector(args);
    const handle = await findElement(page, sel);
    try {
      const clear = args.clear ?? true;
      if (clear) {
        await handle.evaluate(el => { el.value = ''; });
      }
      await handle.focus();
      await handle.type(args.text);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: `Typed into ${sel}` + logs }] };
    } finally {
      await handle.dispose();
    }
  },
};
