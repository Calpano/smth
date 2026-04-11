import { browserSessions } from '../state.js';
import { launchBrowser, drainLogs } from '../browser/session.js';
import { resolveTarget, gotoPage } from '../browser/navigate.js';
import { captureDom } from '../browser/dom.js';
import { lineIntersection, subtractLines } from '../util/diff.js';
import { findMostSimilarUrl } from '../util/similarUrl.js';

export default {
  needsSession: false,
  schema: {
    name: 'fetch_dom_content',
    description: 'All-in-one: open a URL, auto-detect background by visiting a similar page on the same site, and return the unique (foreground) DOM content of the original page. Also stores named snapshots: "<prefix>_page", "<prefix>_peer", "<prefix>_background" for further inspection.',
    annotations: { readOnlyHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        url:    { type: 'string', description: 'URL to fetch content from.' },
        lens:   { type: 'array', items: { type: 'string', enum: ['text', 'media', 'layout', 'code', 'svg', 'none'] }, description: 'Lenses for DOM capture (default: ["text", "layout"]). Use "none" to skip DOM output (useful when only console logs are needed).' },
        prefix: { type: 'string', description: 'Prefix for stored snapshot names (default: "fetch").' },
        getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
      },
      required: ['url'],
    },
  },
  async handler({ sessionId }, args) {
    const { url, lens = ['text', 'layout'], prefix = 'fetch' } = args;
    const resolvedUrl = resolveTarget(url);

    // 1. Launch fresh session and load the target URL.
    await launchBrowser(sessionId, url);
    // Re-read session after launch (launchBrowser replaces it).
    const freshSession = browserSessions.get(sessionId);
    const freshPage = freshSession.page;
    const freshSnapshots = freshSession.snapshots;

    // 2. Capture DOM of page A.
    const htmlA = await captureDom(freshPage, { lens });
    freshSnapshots.set(`${prefix}_page`, { html: htmlA, lens, timestamp: new Date().toISOString(), chars: htmlA.length });

    // 3. Find the most similar same-site link.
    const allLinks = await freshPage.evaluate(() =>
      Array.from(document.querySelectorAll('a[href]')).map(a => a.href)
    );
    const peerUrl = findMostSimilarUrl(resolvedUrl, allLinks);
    if (!peerUrl) {
      const logs = args.getConsoleLogs ? drainLogs(freshSession) : '';
      return { content: [{ type: 'text', text: `No same-site peer link found. Returning full DOM.\n\n${htmlA}` + logs }] };
    }

    // 4. Navigate to peer and capture DOM of page B.
    await gotoPage(freshPage, peerUrl);
    const htmlB = await captureDom(freshPage, { lens });
    freshSnapshots.set(`${prefix}_peer`, { html: htmlB, lens, timestamp: new Date().toISOString(), chars: htmlB.length });

    // 5. Compute background (lines common to both pages).
    const bgHtml = lineIntersection(htmlA, htmlB);
    freshSnapshots.set(`${prefix}_background`, { html: bgHtml, lens, timestamp: new Date().toISOString(), chars: bgHtml.length });

    // 6. Navigate back to the original URL.
    await gotoPage(freshPage, url);

    // 7. Return page A content minus background.
    const content = subtractLines(htmlA, bgHtml);
    const bgLines = bgHtml.split('\n').filter(l => l.trim()).length;
    const contentLines = content.split('\n').filter(l => l.trim()).length;
    const header = `# fetch_dom_content: ${url}\n# peer: ${peerUrl}\n# background: ${bgLines} lines stripped, ${contentLines} lines remain\n# snapshots: ${prefix}_page, ${prefix}_peer, ${prefix}_background\n\n`;
    const logs = args.getConsoleLogs ? drainLogs(freshSession) : '';
    return { content: [{ type: 'text', text: header + (content || '(no content after background subtraction)') + logs }] };
  },
};
