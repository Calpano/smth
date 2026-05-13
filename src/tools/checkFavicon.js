import { gotoPage } from '../browser/navigate.js';
import { findIconLinks, probeUrl, defaultFaviconUrl } from '../checks/favicon.js';

export default {
  schema: {
    name: 'browser_check_favicon',
    description: 'Navigate to a URL, scan declared <link rel="icon"|"apple-touch-icon"|…> entries plus the default /favicon.ico fallback, fetch each, and report whether at least one is a valid image response.',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        url:           { type: 'string',  description: 'URL or file path to navigate to.' },
        check_default: { type: 'boolean', description: 'If true (default), also probe /favicon.ico when no <link> declares it.' },
      },
      required: ['url'],
    },
  },
  async handler({ session }, args) {
    const { page } = session;
    const finalUrl = await gotoPage(page, args.url);
    const links = await findIconLinks(page);

    const declared = await Promise.all(
      links.map(async link => ({ ...link, ...await probeUrl(link.href) })),
    );

    let fallback = null;
    if (args.check_default !== false) {
      const url = defaultFaviconUrl(finalUrl);
      const alreadyDeclared = declared.some(d => d.href === url);
      if (!alreadyDeclared) {
        fallback = { url, ...await probeUrl(url) };
      }
    }

    const all = [...declared, ...(fallback ? [fallback] : [])];
    const ok = all.some(c => c.ok);
    const reason = ok
      ? null
      : all.length === 0
        ? 'no favicon declared and /favicon.ico fallback skipped'
        : 'no favicon URL returned a valid image';

    const response = {
      url: finalUrl,
      favicons: declared,
      default: fallback,
      ok,
      reason,
    };
    return { content: [{ type: 'text', text: JSON.stringify(response, null, 2) }] };
  },
};
