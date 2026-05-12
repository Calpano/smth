// Browser session lifecycle — launching Chromium, tracking console logs.

import puppeteer from 'puppeteer-core';
import { CHROMIUM_PATH } from '../config.js';
import { browserSessions } from '../state.js';
import { resolveTarget } from './navigate.js';

// Launch (or relaunch) a browser for the given MCP session and open the URL.
// Returns a human-readable status string used by browser_launch's response.
export async function launchBrowser(sessionId, url) {
  const existing = browserSessions.get(sessionId);
  if (existing) {
    await existing.browser.close().catch(() => {});
    browserSessions.delete(sessionId);
  }
  const browser = await puppeteer.launch({
    executablePath: CHROMIUM_PATH,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--allow-file-access-from-files'],
    headless: true,
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 720 });

  const resolved = resolveTarget(url);
  let finalUrl = resolved;
  try {
    await page.goto(resolved, { waitUntil: 'networkidle0', timeout: 30000 });
  } catch (err) {
    const isConnRefused = err.message && (
      err.message.includes('ERR_CONNECTION_REFUSED') ||
      err.message.includes('ECONNREFUSED')
    );
    const isLocalhost = /^https?:\/\/localhost[:/]/i.test(resolved);
    if (isConnRefused && isLocalhost) {
      finalUrl = resolved.replace(/^(https?:\/\/)localhost([:/])/i, '$1host.docker.internal$2');
      await page.goto(finalUrl, { waitUntil: 'networkidle0', timeout: 30000 });
    } else {
      throw err;
    }
  }

  const consoleLogs = [];
  attachConsoleListeners(page, consoleLogs);
  browserSessions.set(sessionId, { browser, page, snapshots: new Map(), consoleLogs });
  const title = await page.title();
  const note = finalUrl !== resolved ? ` (localhost unreachable; use host.docker.internal)` : '';
  return `Launched: ${title}${note}`;
}

// Attach the console + pageerror listeners that push structured records into
// the shared buffer. Exposed so reconnection / page-replacement paths can
// reattach without duplicating the shape.
export function attachConsoleListeners(page, buffer) {
  page.on('console', msg => {
    const loc = msg.location?.() ?? {};
    // Puppeteer 24 reports console.warn as 'warn'; older versions used 'warning'.
    // Normalize to 'warning' so include filters and downstream consumers don't
    // have to know which version is in use.
    const rawType = msg.type();
    const type = rawType === 'warn' ? 'warning' : rawType;
    buffer.push({
      type,
      text: msg.text(),
      location: { url: loc.url ?? null, lineNumber: loc.lineNumber ?? null, columnNumber: loc.columnNumber ?? null },
      stack: null,
    });
  });
  page.on('pageerror', err => {
    buffer.push({
      type: 'pageerror',
      text: err?.message ?? String(err),
      location: { url: null, lineNumber: null, columnNumber: null },
      stack: err?.stack ?? null,
    });
  });
}

// Drain buffered console logs from a session and return them formatted for
// inclusion in a tool response. Returns empty string when nothing is buffered.
// Clears in place so the closure inside attachConsoleListeners keeps writing
// to the same array — replacing the reference would orphan future events.
export function drainLogs(session) {
  if (!session?.consoleLogs?.length) return '';
  const out = session.consoleLogs.map(m => `[console.${m.type}] ${m.text}`).join('\n');
  session.consoleLogs.length = 0;
  return '\n\n---\nconsole:\n' + out;
}

// Drain and return the structured records (one per console event / pageerror).
// Used by browser_check_console; same in-place clear as drainLogs above.
export function drainLogsStructured(session) {
  if (!session?.consoleLogs?.length) return [];
  const out = session.consoleLogs.slice();
  session.consoleLogs.length = 0;
  return out;
}
