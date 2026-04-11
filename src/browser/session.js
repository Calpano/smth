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
  page.on('console', msg => consoleLogs.push(msg));
  browserSessions.set(sessionId, { browser, page, snapshots: new Map(), consoleLogs });
  const title = await page.title();
  const note = finalUrl !== resolved ? ` (localhost unreachable; use host.docker.internal)` : '';
  return `Launched: ${title}${note}`;
}

// Drain buffered console logs from a session and return them formatted for
// inclusion in a tool response. Returns empty string when nothing is buffered.
export function drainLogs(session) {
  if (!session?.consoleLogs?.length) return '';
  const out = session.consoleLogs.map(m => `[console.${m.type()}] ${m.text()}`).join('\n');
  session.consoleLogs = [];
  return '\n\n---\nconsole:\n' + out;
}
