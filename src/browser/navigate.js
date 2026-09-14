// URL resolution and Puppeteer navigation helpers.

import { join, isAbsolute } from 'path';
import { PAGES_DIR, HOST_PAGES_DIR } from '../config.js';

// Resolve a user-provided target into a URL Chromium can open.
// - http(s) URLs are passed through.
// - file:// URLs are rewritten if they point inside HOST_PAGES_DIR.
// - Absolute paths are turned into file:// URLs, rewriting into PAGES_DIR when applicable.
// - Relative paths are resolved against PAGES_DIR.
export function resolveTarget(target) {
  if (/^https?:\/\//i.test(target)) return target;
  if (/^file:\/\//i.test(target)) {
    if (HOST_PAGES_DIR) {
      const hostPrefix = `file://${HOST_PAGES_DIR}`;
      if (target.startsWith(hostPrefix)) {
        return `file://${join(PAGES_DIR, target.slice(hostPrefix.length))}`;
      }
    }
    return target;
  }
  if (isAbsolute(target)) {
    if (HOST_PAGES_DIR && target.startsWith(HOST_PAGES_DIR)) {
      return `file://${join(PAGES_DIR, target.slice(HOST_PAGES_DIR.length))}`;
    }
    return `file://${target}`;
  }
  return `file://${join(PAGES_DIR, target)}`;
}

// The two waits that mean "until the network goes quiet". They are the ones that
// cannot be asked of a page that keeps a connection open on purpose.
const IDLE = new Set(['networkidle0', 'networkidle2']);

// Navigate an existing page to a URL. Returns the final URL navigated to.
// `waitUntil` and `timeout` are forwarded to Puppeteer (defaults: networkidle0 / 30s).
//
// An idle wait is done in two steps rather than handed to `page.goto`, because a
// page with a stream in it — server-sent events, a websocket, a long poll — never
// goes quiet, and asking `goto` to wait for that turns every such page into a
// 30-second timeout and no page at all. Live-reloading dev servers are the common
// case: vite, webpack and SvelteKit all hold a connection open, and so does any
// app with an /events endpoint. So the navigation is committed on its own terms
// and the quiet is waited for separately, with the wait — not the page — being
// what is given up on.
export async function gotoPage(page, url, { waitUntil = 'networkidle0', timeout = 30000 } = {}) {
  const resolved = resolveTarget(url);
  if (!IDLE.has(waitUntil)) {
    await page.goto(resolved, { waitUntil, timeout });
    return resolved;
  }
  await page.goto(resolved, { waitUntil: 'domcontentloaded', timeout });
  await page
    .waitForNetworkIdle({
      idleTime: 500,
      concurrency: waitUntil === 'networkidle2' ? 2 : 0,
      timeout: Math.min(timeout, 10000),
    })
    .catch(() => {});
  return resolved;
}
