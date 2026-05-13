// MCP client that persists the server-assigned session ID across CLI
// invocations. Without persistence, every `smth <tool>` call would get a
// fresh Chromium — useless for chains like `smth browser_launch` then
// `smth browser_click`. With persistence, the second call passes the
// stored session ID in the `mcp-session-id` header and reuses the
// already-open browser. The session is reaped on the server side after
// SMTH_SESSION_IDLE_MS of inactivity, so we transparently retry once
// without a stored ID on 404 ("Session not found").

import { mkdirSync, readFileSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

import { DEFAULT_URL } from './container.js';

const STATE_DIR = join(homedir(), '.smth');
const SESSION_FILE = join(STATE_DIR, 'session');

function loadSessionId() {
  if (!existsSync(SESSION_FILE)) return null;
  const s = readFileSync(SESSION_FILE, 'utf8').trim();
  return s || null;
}

function saveSessionId(id) {
  mkdirSync(STATE_DIR, { recursive: true });
  writeFileSync(SESSION_FILE, id, 'utf8');
}

export function clearSessionId() {
  if (existsSync(SESSION_FILE)) rmSync(SESSION_FILE);
}

export function currentSessionId() {
  return loadSessionId();
}

async function connect(url, sessionId) {
  const transport = new StreamableHTTPClientTransport(new URL(`${url}/mcp`), { sessionId });
  const client = new Client({ name: 'smth-cli', version: '1.0.0' }, { capabilities: {} });
  await client.connect(transport);
  return { client, transport };
}

const STALE_SESSION_RE = /Session not found|HTTP 404|status code: 404/i;

// Run `fn(client)` with session-id persistence. Retries once without the
// cached id if the server says the session is gone (typical after a container
// restart or idle reap).
async function withClient(url, fn) {
  let sessionId = loadSessionId();
  for (let attempt = 0; attempt < 2; attempt++) {
    let conn;
    try {
      conn = await connect(url, sessionId ?? undefined);
    } catch (err) {
      if (sessionId && attempt === 0 && STALE_SESSION_RE.test(String(err.message))) {
        sessionId = null;
        clearSessionId();
        continue;
      }
      throw err;
    }
    try {
      const result = await fn(conn.client);
      const newId = conn.transport.sessionId;
      if (newId && newId !== sessionId) saveSessionId(newId);
      return result;
    } catch (err) {
      if (sessionId && attempt === 0 && STALE_SESSION_RE.test(String(err.message))) {
        sessionId = null;
        clearSessionId();
        await conn.client.close().catch(() => {});
        continue;
      }
      throw err;
    } finally {
      await conn.client.close().catch(() => {});
    }
  }
  // Unreachable: the loop either returns, throws, or continues.
  throw new Error('withClient: exhausted retries');
}

export async function callTool(name, args, { url = DEFAULT_URL } = {}) {
  return withClient(url, c => c.callTool({ name, arguments: args }));
}

export async function listTools({ url = DEFAULT_URL } = {}) {
  const result = await withClient(url, c => c.listTools());
  return result.tools;
}
