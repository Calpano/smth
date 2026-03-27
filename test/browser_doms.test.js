import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, TEST_PAGE, TEST_PAGE_B } from './helpers.js';

describe('browser_doms', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns (no snapshots) when session is fresh', async () => {
    const fresh = await createClient();
    await callTool(fresh, 'browser_launch', { url: TEST_PAGE });
    const { text } = await callTool(fresh, 'browser_doms');
    expect(text).toBe('(no snapshots)');
    await fresh.close();
  });

  it('lists a snapshot after it is created', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'listed' });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toContain('listed');
  });

  it('output line includes chars, lens, and timestamp', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'meta-test', lens: ['layout'] });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toMatch(/meta-test\s+\d+ chars\s+lens:layout\s+\d{4}-\d{2}-\d{2}/);
  });

  it('lists multiple snapshots with different lenses', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'a-full' });
    await callTool(client, 'browser_remember_dom', { name: 'a-text', lens: ['text'] });
    await callTool(client, 'browser_remember_dom', { name: 'a-code', lens: ['code'] });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toContain('a-full');
    expect(text).toContain('a-text');
    expect(text).toContain('a-code');
    expect(text).toContain('lens:full');
    expect(text).toContain('lens:text');
    expect(text).toContain('lens:code');
  });

  it('snapshots survive navigation', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'before-nav' });
    await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    await callTool(client, 'browser_goto', { url: TEST_PAGE });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toContain('before-nav');
  });
});
