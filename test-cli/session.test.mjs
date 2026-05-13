// Session-persistence integration tests for the smth CLI.
//
// These verify that the mcp-session-id is cached in ~/.smth/session and
// reused across invocations, that `session reset` works, and that the CLI
// transparently recovers when the cached id is no longer accepted by the
// server (stale-session retry).
//
// Run:  node --test test-cli/session.test.mjs

import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';

import { runCli, tempHome, cleanHome, readSessionFile, writeSessionFile, REMOTE_URL } from './helpers.mjs';

const homes = new Set();
function newHome() { const h = tempHome(); homes.add(h); return h; }

before(async () => {
  const probe = await fetch('http://localhost:3000/health').catch(() => null);
  if (!probe || !probe.ok) throw new Error('smth container is not up. Start it with: docker compose up -d smth');
});

after(() => { for (const h of homes) cleanHome(h); });

test('first tool call writes a session id to ~/.smth/session', () => {
  const home = newHome();
  assert.equal(readSessionFile(home), null);

  const { status } = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home });
  assert.equal(status, 0);

  const id = readSessionFile(home);
  assert.ok(id, 'expected a session id to be written');
  assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
});

test('second invocation reuses the cached session (browser persists)', () => {
  const home = newHome();
  // First call: launch (creates browser session under some id).
  const r1 = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home });
  assert.equal(r1.status, 0);
  const id1 = readSessionFile(home);
  assert.ok(id1);

  // Second call: read_text needs an existing browser session. If session id
  // is being reused correctly, this succeeds and finds the launched page.
  const r2 = runCli(['browser_read_text'], { home });
  assert.equal(r2.status, 0, `browser_read_text failed unexpectedly: ${r2.stderr}`);
  assert.match(r2.stdout, /Example Domain/);

  // The session id should not have rotated.
  assert.equal(readSessionFile(home), id1);
});

test('session reset clears the saved id; next call starts fresh', () => {
  const home = newHome();
  const launch = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home });
  assert.equal(launch.status, 0);
  const oldId = readSessionFile(home);
  assert.ok(oldId);

  const reset = runCli(['session', 'reset'], { home });
  assert.equal(reset.status, 0);
  assert.equal(readSessionFile(home), null);

  // Without a launch on the new session, a browser tool should error
  // with "Call browser_launch first" — confirming we got a fresh MCP
  // session (no leaked state from the previous browser).
  const probe = runCli(['browser_read_text'], { home });
  assert.notEqual(probe.status, 0);
  assert.match(probe.stderr, /browser_launch first/i);

  // And a new session id should now be saved.
  const newId = readSessionFile(home);
  assert.ok(newId);
  assert.notEqual(newId, oldId);
});

test('stale cached session id is transparently dropped and replaced', () => {
  const home = newHome();
  // Plant a bogus session id that the server will reject.
  writeSessionFile(home, '00000000-0000-0000-0000-000000000000');

  // `list` works without a browser session — perfect probe for the retry path.
  const { status, stdout } = runCli(['list'], { home });
  assert.equal(status, 0, 'expected stale-session retry to succeed');
  assert.match(stdout, /browser_launch/);

  // The bogus id should have been replaced with a real one.
  const id = readSessionFile(home);
  assert.ok(id);
  assert.notEqual(id, '00000000-0000-0000-0000-000000000000');
});

test('session show after a tool call reflects the saved id', () => {
  const home = newHome();
  const launch = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home });
  assert.equal(launch.status, 0);
  const saved = readSessionFile(home);

  const { status, stdout } = runCli(['session', 'show'], { home });
  assert.equal(status, 0);
  assert.ok(stdout.includes(saved), `expected '${saved}' in: ${stdout}`);
});

test('two separate HOMEs do not share session state', () => {
  const homeA = newHome();
  const homeB = newHome();

  const a = runCli(['browser_launch', `--url=${REMOTE_URL}`], { home: homeA });
  assert.equal(a.status, 0);
  const idA = readSessionFile(homeA);

  // homeB has no session yet. A browser tool here must NOT reuse homeA's id.
  const probeB = runCli(['browser_read_text'], { home: homeB });
  assert.notEqual(probeB.status, 0);
  assert.match(probeB.stderr, /browser_launch first/i);

  const idB = readSessionFile(homeB);
  assert.ok(idB);
  assert.notEqual(idA, idB);

  // homeA's state should be untouched and the page still readable.
  const readA = runCli(['browser_read_text'], { home: homeA });
  assert.equal(readA.status, 0);
  assert.match(readA.stdout, /Example Domain/);
});
