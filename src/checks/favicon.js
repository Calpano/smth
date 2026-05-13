// Pure helpers for browser_check_favicon. Splitting these out so a CLI or
// test can run a check without going through the MCP tool wrapper.

import { fileURLToPath } from 'node:url';
import { stat, readFile } from 'node:fs/promises';

const ICON_RELS = new Set([
  'icon',
  'shortcut icon',
  'apple-touch-icon',
  'apple-touch-icon-precomposed',
  'mask-icon',
]);

const EXT_MIME = {
  ico: 'image/x-icon',
  png: 'image/png',
  svg: 'image/svg+xml',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
};

// Read every <link rel="icon"|"apple-touch-icon"|…> from the current page.
// `href` is the browser-resolved absolute URL, so we don't have to redo URL
// resolution on the Node side. A `rel` attribute may contain multiple tokens
// (e.g. "shortcut icon"); we keep it as the lower-cased original.
export async function findIconLinks(page) {
  return page.evaluate((rels) => {
    const wanted = new Set(rels);
    const out = [];
    for (const link of document.querySelectorAll('link[rel]')) {
      const rel = (link.getAttribute('rel') ?? '').toLowerCase().trim();
      if (!wanted.has(rel)) continue;
      out.push({
        rel,
        href: link.href,
        type: link.getAttribute('type') || null,
        sizes: link.getAttribute('sizes') || null,
      });
    }
    return out;
  }, [...ICON_RELS]);
}

// Probe a single favicon URL. Supports http(s), file:// and data: scheme.
// Returns a consistent shape so the tool can fold many probes into one
// response without conditional branches per scheme.
export async function probeUrl(url) {
  if (url.startsWith('data:')) return probeDataUrl(url);
  if (url.startsWith('file://')) return probeFileUrl(url);
  if (/^https?:/i.test(url)) return probeHttpUrl(url);
  return { status: null, contentType: null, bytes: 0, ok: false, error: `unsupported scheme: ${url.slice(0, 12)}…` };
}

function probeDataUrl(url) {
  const m = url.match(/^data:([^;,]*)(?:;base64)?,(.*)$/);
  if (!m) return { status: 400, contentType: null, bytes: 0, ok: false, error: 'malformed data URL' };
  const contentType = m[1] || 'text/plain';
  const isBase64 = url.startsWith(`data:${contentType};base64`);
  const bytes = isBase64 ? Math.floor((m[2].length * 3) / 4) : m[2].length;
  return {
    status: 200,
    contentType,
    bytes,
    ok: contentType.startsWith('image/') && bytes > 0,
  };
}

async function probeFileUrl(url) {
  try {
    const path = fileURLToPath(url);
    const st = await stat(path);
    const ext = path.split('.').pop()?.toLowerCase();
    const contentType = EXT_MIME[ext] ?? 'application/octet-stream';
    return {
      status: 200,
      contentType,
      bytes: st.size,
      ok: st.size > 0 && contentType.startsWith('image/'),
    };
  } catch (err) {
    return { status: 404, contentType: null, bytes: 0, ok: false, error: err.message };
  }
}

async function probeHttpUrl(url) {
  try {
    const res = await fetch(url, { method: 'GET', redirect: 'follow', signal: AbortSignal.timeout(10000) });
    const buf = await res.arrayBuffer();
    const contentType = res.headers.get('content-type');
    return {
      status: res.status,
      contentType,
      bytes: buf.byteLength,
      ok: res.ok && (contentType ?? '').startsWith('image/') && buf.byteLength > 0,
      finalUrl: res.url !== url ? res.url : undefined,
    };
  } catch (err) {
    return { status: null, contentType: null, bytes: 0, ok: false, error: err.message };
  }
}

// Build the default /favicon.ico URL for a given page URL, using URL
// resolution so we get the right thing for both http(s) and file:// pages.
export function defaultFaviconUrl(pageUrl) {
  return new URL('/favicon.ico', pageUrl).href;
}
