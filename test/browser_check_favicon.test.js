import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON } from './helpers.js';

const GOOD    = 'test-favicon-good.html';
const BROKEN  = 'test-favicon-broken.html';
const NONE    = 'test-favicon-none.html';
const MULTI   = 'test-favicon-multi.html';

describe('browser_check_favicon', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: GOOD });
  });
  afterAll(async () => { await client.close(); });

  it('passes when a declared favicon resolves to a real image', async () => {
    const result = await callTool(client, 'browser_check_favicon', { url: GOOD });
    const data = parseJSON(result);
    expect(data.ok).toBe(true);
    expect(data.reason).toBeNull();
    expect(data.favicons).toHaveLength(1);
    expect(data.favicons[0].rel).toBe('icon');
    expect(data.favicons[0].href).toMatch(/test-favicon\.png$/);
    expect(data.favicons[0].ok).toBe(true);
    expect(data.favicons[0].contentType).toBe('image/png');
    expect(data.favicons[0].bytes).toBeGreaterThan(0);
  });

  it('captures sizes/type attributes from the link tag', async () => {
    const { favicons } = parseJSON(await callTool(client, 'browser_check_favicon', { url: GOOD }));
    expect(favicons[0].type).toBe('image/png');
    expect(favicons[0].sizes).toBe('32x32');
  });

  it('fails when the declared favicon URL does not resolve', async () => {
    const result = await callTool(client, 'browser_check_favicon', { url: BROKEN });
    const data = parseJSON(result);
    expect(data.ok).toBe(false);
    expect(data.reason).toMatch(/no favicon URL returned a valid image/);
    expect(data.favicons).toHaveLength(1);
    expect(data.favicons[0].ok).toBe(false);
    expect(data.favicons[0].status).toBe(404);
  });

  it('falls back to /favicon.ico when no <link> is declared', async () => {
    const result = await callTool(client, 'browser_check_favicon', { url: NONE });
    const data = parseJSON(result);
    expect(data.favicons).toEqual([]);
    // File-served pages have origin "null"; the resolved fallback is /favicon.ico
    // at the file:// root, which doesn't exist in test-pages.
    expect(data.default).not.toBeNull();
    expect(data.default.url).toMatch(/\/favicon\.ico$/);
    expect(data.default.ok).toBe(false);
    expect(data.ok).toBe(false);
  });

  it('skips the /favicon.ico probe when check_default=false', async () => {
    const result = await callTool(client, 'browser_check_favicon', { url: NONE, check_default: false });
    const data = parseJSON(result);
    expect(data.default).toBeNull();
    expect(data.reason).toMatch(/fallback skipped/);
  });

  it('passes when at least one of multiple favicons works (others broken)', async () => {
    const result = await callTool(client, 'browser_check_favicon', { url: MULTI });
    const data = parseJSON(result);
    expect(data.ok).toBe(true);
    expect(data.favicons).toHaveLength(3);
    const byRel = Object.fromEntries(data.favicons.map(f => [f.rel, f]));
    expect(byRel['icon'].ok).toBe(true);
    expect(byRel['icon'].contentType).toBe('image/png');
    expect(byRel['apple-touch-icon'].ok).toBe(false);
    expect(byRel['shortcut icon'].ok).toBe(true);
    expect(byRel['shortcut icon'].contentType).toBe('image/png');
    // data: URL probe reports status 200 with a non-zero byte estimate.
    expect(byRel['shortcut icon'].status).toBe(200);
    expect(byRel['shortcut icon'].bytes).toBeGreaterThan(0);
  });
});
