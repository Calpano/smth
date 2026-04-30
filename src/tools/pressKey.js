import { drainLogs } from '../browser/session.js';
import { resolveSelector, findElement } from '../browser/select.js';

const VALID_MODIFIERS = new Set(['Control', 'Shift', 'Alt', 'Meta']);

export default {
  schema: {
    name: 'browser_press_key',
    description: 'Press a single key, optionally with modifiers (Control/Shift/Alt/Meta). Use for keys that have no character (Enter, Escape, Tab, ArrowDown, Backspace, etc.) or for shortcuts. Optionally focuses an element first. Waits up to 5s for navigation triggered by the press.',
    annotations: { readOnlyHint: false, destructiveHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        key:       { type: 'string', description: 'Key name. Examples: "Enter", "Escape", "Tab", "ArrowDown", "ArrowUp", "Backspace", "Delete", "PageDown", "F5", or a single character like "a" or "1". Case-sensitive — use the names from https://pptr.dev/api/puppeteer.keyinput.' },
        modifiers: { type: 'array', items: { type: 'string', enum: ['Control', 'Shift', 'Alt', 'Meta'] }, description: 'Modifier keys held while pressing. Meta is Cmd on macOS, Win key on Windows.' },
        id:       { type: 'string', description: 'Optional. HTML element id to focus before pressing.' },
        selector: { type: 'string', description: 'Optional. CSS selector to focus before pressing. Supports the ":has-text(\'substring\')" extension at the end.' },
        count:    { type: 'integer', minimum: 1, maximum: 50, description: 'Number of times to press the key (default 1).' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
      required: ['key'],
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const { key, modifiers = [], count = 1 } = args;

    for (const m of modifiers) {
      if (!VALID_MODIFIERS.has(m)) {
        throw new Error(`Invalid modifier "${m}". Must be one of: Control, Shift, Alt, Meta.`);
      }
    }

    let focused = null;
    if (args.id || args.selector) {
      const sel = resolveSelector(args);
      const handle = await findElement(page, sel);
      try {
        await handle.focus();
        focused = sel;
      } finally {
        await handle.dispose();
      }
    }

    for (const m of modifiers) await page.keyboard.down(m);
    try {
      const pressAll = (async () => {
        for (let i = 0; i < count; i++) await page.keyboard.press(key);
      })();
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => null),
        pressAll,
      ]);
    } finally {
      for (const m of [...modifiers].reverse()) await page.keyboard.up(m);
    }

    const combo = [...modifiers, key].join('+');
    const times = count > 1 ? ` ×${count}` : '';
    const where = focused ? ` (focused ${focused})` : '';
    const title = await page.title();
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: `Pressed ${combo}${times}${where}. Page: ${title}` + logs }] };
  },
};
