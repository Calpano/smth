// docker compose helpers for `smth up|down|status` and auto-start.
//
// The CLI talks to the same Docker stack the test harness uses. We resolve
// the project root from this file's location so `npm install -g .` works:
// the bin script and the compose file ship together.

import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const PROJECT_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
export const DEFAULT_URL = process.env.SMTH_URL ?? 'http://localhost:3000';

// Run docker compose with inherited stdio so the user sees build / pull output.
function runCompose(args) {
  return new Promise((resolveExit, rejectExit) => {
    const child = spawn('docker', ['compose', ...args], {
      cwd: PROJECT_ROOT,
      stdio: 'inherit',
    });
    child.on('error', rejectExit);
    child.on('exit', code => {
      if (code === 0) resolveExit();
      else rejectExit(new Error(`docker compose ${args.join(' ')} exited with code ${code}`));
    });
  });
}

// Single health probe. Returns the parsed body on 200, null otherwise.
export async function probe(url = DEFAULT_URL) {
  try {
    const res = await fetch(`${url}/health`, { signal: AbortSignal.timeout(2000) });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

// Block until /health returns ok, polling every 500ms up to `timeoutMs`.
export async function waitHealthy(url = DEFAULT_URL, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const body = await probe(url);
    if (body) return body;
    await new Promise(r => setTimeout(r, 500));
  }
  throw new Error(`smth did not become healthy at ${url} within ${timeoutMs}ms`);
}

export async function up({ quiet = false } = {}) {
  if (!quiet) console.error('[smth] starting container...');
  await runCompose(['up', '-d', 'smth']);
  await waitHealthy();
  if (!quiet) console.error('[smth] container healthy');
}

export async function down() {
  await runCompose(['down']);
}

// Used at the top of every tool invocation. If smth is up, return immediately.
// If down, start it. Used in --auto-start mode (default on).
export async function ensureUp() {
  const body = await probe();
  if (body) return body;
  await up({ quiet: false });
  return await probe();
}
