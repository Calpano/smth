import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, TEST_PAGE } from './helpers.js';

describe('fetch_dom_content', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });

  it('returns a metadata header and foreground content', async () => {
    const { text } = await callTool(client, 'fetch_dom_content', { url: TEST_PAGE });
    expect(text).toMatch(/^# fetch_dom_content:/m);
    expect(text).toMatch(/^# peer:/m);
    expect(text).toMatch(/^# background: \d+ lines stripped/m);
    expect(text).toMatch(/^# snapshots: fetch_page, fetch_peer, fetch_background/m);
  });

  it('detects test-b.html as the peer (most similar same-site link)', async () => {
    const { text } = await callTool(client, 'fetch_dom_content', { url: TEST_PAGE });
    expect(text).toMatch(/# peer:.*test-b\.html/);
  });

  it('foreground content contains home-page-unique text', async () => {
    const { text } = await callTool(client, 'fetch_dom_content', { url: TEST_PAGE });
    expect(text).toContain('Welcome to smth test page');
    expect(text).toContain('alpha bravo charlie');
    expect(text).toContain('Error: something went wrong');
  });

  it('foreground content does not contain nav/footer background', async () => {
    const { text } = await callTool(client, 'fetch_dom_content', { url: TEST_PAGE });
    // Nav links and footer copyright are background — should be stripped
    const contentLines = text.split('\n').filter(l => !l.startsWith('#'));
    const hasNavLink = contentLines.some(l => l.match(/>Home</));
    const hasFooterCopy = contentLines.some(l => l.includes('2024 smth test site'));
    expect(hasNavLink).toBe(false);
    expect(hasFooterCopy).toBe(false);
  });

  it('stores three named snapshots', async () => {
    await callTool(client, 'fetch_dom_content', { url: TEST_PAGE });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toContain('fetch_page');
    expect(text).toContain('fetch_peer');
    expect(text).toContain('fetch_background');
  });

  it('custom prefix stores snapshots under that prefix', async () => {
    await callTool(client, 'fetch_dom_content', { url: TEST_PAGE, prefix: 'run2' });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toContain('run2_page');
    expect(text).toContain('run2_peer');
    expect(text).toContain('run2_background');
  });

  it('subtract mode works with the stored background', async () => {
    await callTool(client, 'fetch_dom_content', { url: TEST_PAGE, prefix: 'chk' });
    const { text } = await callTool(client, 'browser_dom_compare', {
      a: 'chk_page',
      subtract: 'chk_background',
    });
    expect(text).toContain('Welcome to smth test page');
    expect(text).not.toBe('(empty after background subtraction)');
  });

  it('browser session is on the original URL after the call', async () => {
    await callTool(client, 'fetch_dom_content', { url: TEST_PAGE, prefix: 'restore' });
    const { text } = await callTool(client, 'browser_read_text');
    // Should show test.html content, not test-b.html
    expect(text).toContain('Welcome to smth test page');
    expect(text).not.toContain('sierra tango uniform');
  });
});
