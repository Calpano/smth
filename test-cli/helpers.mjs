// Shared helpers for CLI integration tests.

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';
import { rmSync, existsSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';

export const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const SMTH_BIN  = join(REPO_ROOT, 'bin', 'smth.js');

// Tests isolate the session-id file by pointing HOME at a temp dir. The
// CLI reads ~/.smth/session, so a per-test HOME prevents cross-test bleed.
export function tempHome() {
  const dir = join(tmpdir(), `smth-cli-test-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`);
  mkdirSync(dir, { recursive: true });
  return dir;
}

export function cleanHome(dir) {
  if (existsSync(dir)) rmSync(dir, { recursive: true, force: true });
}

// Spawn the CLI synchronously. Returns { status, stdout, stderr }.
export function runCli(args, { home, env = {}, timeoutMs = 60000 } = {}) {
  const result = spawnSync('node', [SMTH_BIN, ...args], {
    cwd: REPO_ROOT,
    timeout: timeoutMs,
    env: {
      ...process.env,
      ...(home ? { HOME: home } : {}),
      ...env,
    },
    encoding: 'utf8',
  });
  return {
    status: result.status,
    stdout: result.stdout ?? '',
    stderr: result.stderr ?? '',
  };
}

export function readSessionFile(home) {
  const path = join(home, '.smth', 'session');
  if (!existsSync(path)) return null;
  return readFileSync(path, 'utf8').trim();
}

export function writeSessionFile(home, id) {
  const dir = join(home, '.smth');
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'session'), id, 'utf8');
}

// Test fixture: a tiny static URL the smth container can reach without
// depending on PAGES_DIR. example.com is one of the most stable URLs on the
// internet and is intentionally meant for documentation/test use.
export const REMOTE_URL = 'https://example.com';
