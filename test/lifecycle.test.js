import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, SMTH_URL, TEST_PAGE } from './helpers.js';

async function getHealth() {
  const res = await fetch(`${SMTH_URL}/health`);
  return res.json();
}

describe('session lifecycle: /health observability', () => {
  it('exposes sessions, maxSessions, idleTimeoutMs, oldestIdleAgeMs', async () => {
    const h = await getHealth();
    expect(h.status).toBe('ok');
    expect(typeof h.sessions).toBe('number');
    expect(typeof h.maxSessions).toBe('number');
    expect(h.maxSessions).toBeGreaterThan(0);
    expect(typeof h.idleTimeoutMs).toBe('number');
    expect(h.idleTimeoutMs).toBeGreaterThan(0);
    expect(typeof h.oldestIdleAgeMs).toBe('number');
    expect(h.oldestIdleAgeMs).toBeGreaterThanOrEqual(0);
  });

  it('session count tracks new and closed clients', async () => {
    const before = (await getHealth()).sessions;
    const client = await createClient();
    // Trigger a server-side session by issuing a request.
    await client.listTools();
    const during = (await getHealth()).sessions;
    expect(during).toBeGreaterThan(before);
    await client.close();
    // Streamable HTTP close → DELETE → server fires onclose → maps clear.
    // The reaper (backstop, every ~3s, idle ~30s in dev) handles cases where
    // the SDK doesn't send DELETE cleanly. Poll up to one full idle+sweep
    // cycle so this test passes whether the fast or slow path wins.
    const deadline = Date.now() + 40_000;
    let after = during;
    while (Date.now() < deadline) {
      after = (await getHealth()).sessions;
      if (after <= before) break;
      await new Promise(r => setTimeout(r, 100));
    }
    expect(after).toBeLessThanOrEqual(before);
  });
});

describe('session lifecycle: friendly root endpoint', () => {
  it('GET / returns a plain-text hint instead of 404', async () => {
    const res = await fetch(`${SMTH_URL}/`);
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toMatch(/text\/plain/);
    const body = await res.text();
    expect(body).toContain('smth MCP server');
    expect(body).toContain('/mcp');
    expect(body).toContain('/health');
  });
});

describe('session lifecycle: client browser session is reachable across calls', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });

  it('two consecutive tool calls share the same browser session', async () => {
    // browser_launch creates the session; browser_read_text reuses it.
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('Welcome to smth test page');
  });
});
