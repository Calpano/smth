// Integration tests for the `smth` CLI. Spawns the CLI as a subprocess
// against an already-running smth container.
//
// Preconditions:
//   docker compose up -d smth
//
// Run:
//   node --test test-cli/cli.test.mjs

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';

import { runCli, tempHome, cleanHome, readSessionFile, REMOTE_URL } from './helpers.mjs';

// Each test gets its own HOME so the session file is isolated.
const homes = new Set();
function newHome() { const h = tempHome(); homes.add(h); return h; }

before(async () => {
  const probe = await fetch('http://localhost:3000/health').catch(() => null);
  if (!probe || !probe.ok) {
    throw new Error('smth container is not up. Start it with: docker compose up -d smth');
  }
});

after(() => { for (const h of homes) cleanHome(h); });

// ── basic CLI shell ───────────────────────────────────────────────────────

test('--help prints usage', () => {
  const { status, stdout } = runCli(['--help'], { home: newHome() });
  assert.equal(status, 0);
  assert.match(stdout, /Usage:/);
  assert.match(stdout, /smth <tool>/);
});

test('no args prints usage', () => {
  const { status, stdout } = runCli([], { home: newHome() });
  assert.equal(status, 0);
  assert.match(stdout, /Usage:/);
});

test('status reports UP and prints health JSON', () => {
  const { status, stdout } = runCli(['status'], { home: newHome() });
  assert.equal(status, 0);
  assert.match(stdout, /smth is UP/);
  // The JSON block should be parseable.
  const jsonStart = stdout.indexOf('{');
  const jsonEnd = stdout.indexOf('}', jsonStart) + 1;
  const body = JSON.parse(stdout.slice(jsonStart, jsonEnd + stdout.slice(jsonEnd).indexOf('\n')));
  assert.equal(body.status, 'ok');
  assert.equal(body.service, 'smth');
});

test('list returns all expected tool names', () => {
  const { status, stdout } = runCli(['list'], { home: newHome() });
  assert.equal(status, 0);
  for (const name of [
    'browser_launch', 'browser_goto', 'browser_read_text',
    'browser_click', 'browser_check_console', 'browser_check_imprint',
    'browser_see_visual', 'fetch_dom_content',
  ]) {
    assert.match(stdout, new RegExp(`\\b${name}\\b`), `missing tool in list: ${name}`);
  }
});

test('help <tool> renders schema-driven argument block', () => {
  const { status, stdout } = runCli(['help', 'browser_check_console'], { home: newHome() });
  assert.equal(status, 0);
  assert.match(stdout, /^browser_check_console/m);
  assert.match(stdout, /--url\s+string\s+\(required\)/);
  assert.match(stdout, /--include\s+array\s+\(optional\)/);
  assert.match(stdout, /--timeout_ms\s+number\s+\(optional\)/);
});

test('help <tool> errors on unknown tool', () => {
  const { status, stderr } = runCli(['help', 'does_not_exist'], { home: newHome() });
  assert.notEqual(status, 0);
  assert.match(stderr, /Unknown tool/);
});

test('unknown subcommand exits non-zero', () => {
  const { status, stderr } = runCli(['banana_split'], { home: newHome() });
  assert.notEqual(status, 0);
  assert.match(stderr, /Unknown (command|tool)/i);
});

test('session show with empty state reports (none)', () => {
  const { status, stdout } = runCli(['session', 'show'], { home: newHome() });
  assert.equal(status, 0);
  assert.match(stdout, /Saved session id: \(none\)/);
});

test('session reset on empty state is a no-op', () => {
  const home = newHome();
  const { status, stdout } = runCli(['session', 'reset'], { home });
  assert.equal(status, 0);
  assert.match(stdout, /Cleared saved session id/);
  assert.equal(readSessionFile(home), null);
});

test('session unknown subcommand errors', () => {
  const { status, stderr } = runCli(['session', 'banana'], { home: newHome() });
  assert.notEqual(status, 0);
  assert.match(stderr, /Unknown session subcommand/);
});

// ── tool dispatch ─────────────────────────────────────────────────────────

test('browser_launch returns page title (with =value form)', () => {
  const { status, stdout } = runCli(
    ['browser_launch', `--url=${REMOTE_URL}`],
    { home: newHome() },
  );
  assert.equal(status, 0);
  assert.match(stdout, /Launched:/);
  assert.match(stdout, /Example/);
});

test('browser_launch accepts space-separated --url value form', () => {
  const { status, stdout } = runCli(
    ['browser_launch', '--url', REMOTE_URL],
    { home: newHome() },
  );
  assert.equal(status, 0);
  assert.match(stdout, /Example Domain/);
});

test('browser_check_console returns parseable JSON', () => {
  const home = newHome();
  // check_console needs an existing browser session (it navigates the current page).
  const launch = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home });
  assert.equal(launch.status, 0);
  const { status, stdout } = runCli(
    ['browser_check_console', `--url=${REMOTE_URL}`, '--include=error,pageerror'],
    { home },
  );
  assert.equal(status, 0);
  const data = JSON.parse(stdout);
  assert.equal(data.title, 'Example Domain');
  assert.ok(typeof data.counts === 'object');
  assert.ok('error' in data.counts);
  assert.ok('pageerror' in data.counts);
  assert.ok(Array.isArray(data.entries));
});

test('browser_check_console include filter narrows counts', () => {
  const home = newHome();
  const launch = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home });
  assert.equal(launch.status, 0);
  const { stdout } = runCli(
    ['browser_check_console', `--url=${REMOTE_URL}`, '--include=error'],
    { home },
  );
  const data = JSON.parse(stdout);
  assert.deepEqual(Object.keys(data.counts), ['error']);
});

test('tool error from server propagates to stderr + non-zero exit', () => {
  // Calling browser_read_text in a fresh session (no launch) errors.
  const { status, stderr } = runCli(['browser_read_text'], { home: newHome() });
  assert.notEqual(status, 0);
  assert.match(stderr, /browser_launch first/i);
});

test('parser rejects positional args', () => {
  const { status, stderr } = runCli(
    ['browser_launch', 'positional'],
    { home: newHome() },
  );
  assert.notEqual(status, 0);
  assert.match(stderr, /Positional arguments are not supported/);
});
