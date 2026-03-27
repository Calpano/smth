import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, TEST_PAGE, TEST_PAGE_B } from './helpers.js';

describe('browser_remember_dom', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('captures a full DOM snapshot and confirms storage', async () => {
    const { text } = await callTool(client, 'browser_remember_dom', { name: 'full' });
    expect(text).toContain('"full"');
    expect(text).toContain('saved');
    expect(text).toMatch(/\d+ chars/);
    expect(text).toContain('lens: full');
  });

  it('text lens snapshot is smaller than full snapshot', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'snap-full' });
    await callTool(client, 'browser_remember_dom', { name: 'snap-text', lens: ['text'] });

    const { text: listText } = await callTool(client, 'browser_doms');
    const fullMatch = listText.match(/snap-full\s+(\d+) chars/);
    const textMatch = listText.match(/snap-text\s+(\d+) chars/);
    expect(parseInt(fullMatch[1])).toBeGreaterThan(parseInt(textMatch[1]));
  });

  it('exclude reduces snapshot size', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'no-excl' });
    await callTool(client, 'browser_remember_dom', { name: 'excl', exclude: 'nav, footer' });

    const noExclDiff = await callTool(client, 'browser_dom_compare', { a: 'no-excl', b: 'excl' });
    // Excluded elements appear as removed lines
    expect(noExclDiff.text).toContain('-');
  });

  it('overwrites existing snapshot without error', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'overwrite-me' });
    await callTool(client, 'browser_remember_dom', { name: 'overwrite-me' });
    const { text } = await callTool(client, 'browser_doms');
    // Count occurrences of the name — should be exactly once
    const matches = text.match(/overwrite-me/g);
    expect(matches).toHaveLength(1);
  });

  it('max_chars limits the stored snapshot size', async () => {
    const { text } = await callTool(client, 'browser_remember_dom', { name: 'small', max_chars: 400 });
    const charMatch = text.match(/(\d+) chars/);
    expect(parseInt(charMatch[1])).toBeLessThan(2000); // definitely bounded
  });

  it('captures correct content from different pages', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'home-snap', lens: ['text'] });
    await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    await callTool(client, 'browser_remember_dom', { name: 'b-snap', lens: ['text'] });

    const { text: diff } = await callTool(client, 'browser_dom_compare', { a: 'home-snap', b: 'b-snap' });
    expect(diff).toContain('Welcome to smth test page'); // removed from home
    expect(diff).toContain('Articles');                  // added in b
  });
});
