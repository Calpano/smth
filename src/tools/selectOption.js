import { drainLogs } from '../browser/session.js';
import { resolveSelector, findElement } from '../browser/select.js';

export default {
  schema: {
    name: 'browser_select_option',
    description: 'Pick an option from a <select> element by value or by visible label. Dispatches input and change events so frameworks see the update.',
    annotations: { readOnlyHint: false, destructiveHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        id:       { type: 'string', description: 'HTML element id of the <select> (without #). Preferred when available.' },
        selector: { type: 'string', description: 'CSS selector for the <select>. Supports the ":has-text(\'substring\')" extension at the end.' },
        value:    { type: 'string', description: 'The option\'s value attribute (matches <option value="...">). Use this when known.' },
        label:    { type: 'string', description: 'The option\'s visible text. Matched case-insensitively: exact match preferred, otherwise substring. Use when value is unknown.' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const sel = resolveSelector(args);
    if (args.value == null && args.label == null) {
      throw new Error('Provide value or label.');
    }
    const handle = await findElement(page, sel);
    try {
      const result = await handle.evaluate((el, { value, label }) => {
        if (el.tagName !== 'SELECT') {
          return { ok: false, reason: 'not_select', tag: el.tagName.toLowerCase() };
        }
        const options = Array.from(el.options);
        let opt = null;
        if (value != null) {
          opt = options.find(o => o.value === value);
        } else {
          const needle = label.toLowerCase();
          const text = o => (o.textContent ?? '').replace(/\s+/g, ' ').trim();
          opt = options.find(o => text(o).toLowerCase() === needle)
             ?? options.find(o => text(o).toLowerCase().includes(needle));
        }
        if (!opt) {
          return {
            ok: false,
            reason: 'no_option',
            available: options.map(o => ({ value: o.value, label: (o.textContent ?? '').replace(/\s+/g, ' ').trim() })),
          };
        }
        el.value = opt.value;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return { ok: true, value: opt.value, label: (opt.textContent ?? '').replace(/\s+/g, ' ').trim() };
      }, { value: args.value ?? null, label: args.label ?? null });

      if (!result.ok && result.reason === 'not_select') {
        throw new Error(`Element is not a <select>: ${sel} (got <${result.tag}>)`);
      }
      if (!result.ok && result.reason === 'no_option') {
        const list = result.available.map(o => `"${o.label}" (value="${o.value}")`).join(', ') || '(none)';
        const what = args.value != null ? `value="${args.value}"` : `label="${args.label}"`;
        throw new Error(`No option matching ${what} in ${sel}. Available: ${list}`);
      }

      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: `Selected "${result.label}" (value="${result.value}") in ${sel}` + logs }] };
    } finally {
      await handle.dispose();
    }
  },
};
