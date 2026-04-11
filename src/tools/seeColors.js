import { drainLogs } from '../browser/session.js';
import { seeColorsScript } from '../scripts.js';

export default {
  schema: {
    name: 'browser_see_colors',
    description: 'Return a JSON report of all computed colors in use on the current page — text, background, and border — with usage counts per color.',
    annotations: { readOnlyHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        only: {
          type: 'array',
          items: { type: 'string', enum: ['text', 'background', 'border', 'fill', 'stroke'] },
          description: 'Restrict results to these usage categories. Omit to include all.',
        },
        colors: {
          type: 'array',
          items: { type: 'string' },
          description: 'Filter to specific colors given as hex strings (e.g. "#ff0000", "#abc"). Only entries matching these colors are returned.',
        },
        where: {
          type: 'boolean',
          description: 'When true, each color entry gains a "where" key listing the element selectors (#id, .class, or tag) where that color is used, grouped by category.',
        },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
    },
  },
  async handler({ session }, args) {
    const script = seeColorsScript
      .replaceAll('__ONLY__',   JSON.stringify(args.only   ?? null))
      .replaceAll('__COLORS__', JSON.stringify(args.colors ?? null))
      .replaceAll('__WHERE__',  JSON.stringify(args.where  ?? false));
    const result = await session.page.evaluate(script);
    const logs = args.getConsoleLogs ? drainLogs(session) : '';
    return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) + logs }] };
  },
};
