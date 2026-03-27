import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, TEST_PAGE, TEST_PAGE_B } from './helpers.js';

describe('browser_read_text', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns page headings as markdown', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toMatch(/^#+ Welcome to smth test page/m);
    expect(text).toMatch(/^#+ Buttons/m);
    expect(text).toMatch(/^#+ Form/m);
    expect(text).toMatch(/^#+ Known text/m);
  });

  it('includes known paragraph text', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('quick brown fox');
    expect(text).toContain('alpha bravo charlie');
    expect(text).toContain('Error: something went wrong');
    expect(text).toContain('Success: operation completed');
  });

  it('does not include raw HTML tags', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).not.toMatch(/<div|<span|<nav|<style/i);
  });

  it('includes an Interactive Elements section', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('## Interactive Elements');
  });

  it('lists buttons with id using #id reference', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('#btn-primary');
    expect(text).toContain('#submit-btn');
  });

  it('lists elements without id using a CSS selector', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    // The no-id button (btn-secondary) should have some selector, not an empty ref
    const lines = text.split('\n').filter(l => l.includes('[button]'));
    expect(lines.length).toBeGreaterThanOrEqual(2);
    const noIdLine = lines.find(l => !l.includes('#btn-primary') && !l.includes('#submit-btn'));
    expect(noIdLine).toBeDefined();
    expect(noIdLine).toMatch(/\[button\]\s+\S+/); // has some non-empty ref
  });

  it('lists input fields with selectors', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('#name-input');
    expect(text).toContain('#email-input');
  });

  it('shows different content on test-b page', async () => {
    await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toMatch(/^#+ Articles/m);
    expect(text).toContain('sierra tango uniform');
    expect(text).not.toContain('Welcome to smth test page');
  });
});
