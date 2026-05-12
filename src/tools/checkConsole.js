import { drainLogsStructured } from '../browser/session.js';
import { gotoPage } from '../browser/navigate.js';

const DEFAULT_INCLUDE = ['error', 'warning', 'pageerror'];
const ALLOWED_WAIT_UNTIL = new Set(['load', 'domcontentloaded', 'networkidle0', 'networkidle2']);

export default {
  schema: {
    name: 'browser_check_console',
    description: 'Navigate to a URL, wait for it to settle, and return all console errors/warnings + uncaught page exceptions captured during the load. Single-shot, no retry.',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        url:        { type: 'string', description: 'URL or file path to navigate to.' },
        wait_until: { type: 'string', description: "Puppeteer wait condition: 'load', 'domcontentloaded', 'networkidle0', 'networkidle2'. Default 'networkidle2'." },
        include:    { type: 'array', items: { type: 'string' }, description: "Entry types to keep. Default ['error','warning','pageerror']." },
        timeout_ms: { type: 'number', description: 'Navigation timeout in ms. Default 15000.' },
      },
      required: ['url'],
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const waitUntil = args.wait_until ?? 'networkidle2';
    if (!ALLOWED_WAIT_UNTIL.has(waitUntil)) {
      throw new Error(`Invalid wait_until '${waitUntil}'. Allowed: ${[...ALLOWED_WAIT_UNTIL].join(', ')}.`);
    }
    const include = new Set(args.include ?? DEFAULT_INCLUDE);
    const timeout = typeof args.timeout_ms === 'number' ? args.timeout_ms : 15000;

    // Clear any leftover logs from earlier tool calls so the response only
    // reflects what this navigation produced.
    drainLogsStructured(session);

    const finalUrl = await gotoPage(page, args.url, { waitUntil, timeout });
    const title = await page.title();
    const entries = drainLogsStructured(session)
      .filter(e => include.has(e.type))
      .map(e => ({
        level: e.type,
        text: e.text,
        source: e.location?.url
          ? `${e.location.url}${e.location.lineNumber != null ? `:${e.location.lineNumber}` : ''}`
          : null,
        stack: e.stack ?? undefined,
      }));

    const counts = {};
    for (const t of include) counts[t] = 0;
    for (const e of entries) counts[e.level] = (counts[e.level] ?? 0) + 1;

    const response = { url: finalUrl, title, counts, entries };
    return { content: [{ type: 'text', text: JSON.stringify(response, null, 2) }] };
  },
};
