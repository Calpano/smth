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

// Navigate an existing page to a URL, handling the localhost→host.docker.internal
// fallback when the host's loopback is unreachable from inside the container.
// Returns the final URL navigated to.
export async function gotoPage(page, url) {
  const resolved = resolveTarget(url);
  try {
    await page.goto(resolved, { waitUntil: 'networkidle0', timeout: 30000 });
    return resolved;
  } catch (err) {
    const isConnRefused = err.message && (
      err.message.includes('ERR_CONNECTION_REFUSED') ||
      err.message.includes('ECONNREFUSED')
    );
    const isLocalhost = /^https?:\/\/localhost[:/]/i.test(resolved);
    if (isConnRefused && isLocalhost) {
      const fallback = resolved.replace(/^(https?:\/\/)localhost([:/])/i, '$1host.docker.internal$2');
      await page.goto(fallback, { waitUntil: 'networkidle0', timeout: 30000 });
      return fallback;
    }
    throw err;
  }
}
