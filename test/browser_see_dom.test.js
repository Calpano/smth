import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON, TEST_PAGE } from './helpers.js';

describe('browser_see_dom', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns full DOM when no lens specified', async () => {
    const { text } = await callTool(client, 'browser_see_dom');
    expect(text).toMatch(/^<html/);
    expect(text).toContain('<nav');
    expect(text).toContain('<form');
    expect(text).toContain('<input');
    expect(text).toContain('<footer');
  });

  it('text lens includes text elements and omits bare layout', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
    expect(text).toContain('<h1');
    expect(text).toContain('<p');
    expect(text).toContain('<button');
    expect(text).toContain('<label');
    // style attribute is layout-only, should not appear on text-lens elements
    expect(text).not.toMatch(/style="[^"]*margin/);
  });

  it('layout lens includes structural elements and style/class attributes', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['layout'] });
    expect(text).toContain('<div');
    expect(text).toContain('<nav');
    expect(text).toContain('<footer');
    expect(text).toContain('class=');
  });

  it('code lens includes href, type, action attributes', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['code'] });
    expect(text).toContain('href=');
    expect(text).toContain('action=');
    expect(text).toContain('type=');
  });

  it('justCount returns counts object not HTML', async () => {
    const result = parseJSON(await callTool(client, 'browser_see_dom', { lens: ['text'], justCount: true }));
    expect(typeof result.chars).toBe('number');
    expect(result.chars).toBeGreaterThan(0);
    expect(typeof result.elements).toBe('object');
    expect(typeof result.attributes).toBe('object');
  });

  it('max_chars limits output length', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { max_chars: 500 });
    // Should be at or near the budget (depth 1 might exceed, that's acceptable)
    expect(text.length).toBeLessThan(2000);
  });

  it('exclude removes matched elements', async () => {
    const { text } = await callTool(client, 'browser_see_dom', {
      lens: ['text'],
      exclude: 'nav, footer',
    });
    // Nav links should be gone
    expect(text).not.toContain('>Home<');
    expect(text).not.toContain('>Articles<');
    // Body content should remain
    expect(text).toContain('Welcome to smth test page');
  });

  it('search returns context blocks with >>> marker', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { search: ['alpha'] });
    expect(text).toContain('>>>');
    expect(text.toLowerCase()).toContain('alpha');
    // Should not be HTML
    expect(text).not.toMatch(/<html|<div/);
  });

  it('search with multiple terms returns multiple blocks separated by ---', async () => {
    // 'Welcome' is near the top (h1) and 'Success' is near the bottom — they are far enough apart
    // that their 3-line context windows do not overlap, producing two distinct blocks.
    const { text } = await callTool(client, 'browser_see_dom', { search: ['Welcome', 'Success'] });
    expect(text).toContain('[Welcome]');
    expect(text).toContain('[Success]');
    expect(text).toContain('---');
  });

  it('search with no match returns (no matches)', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { search: ['zzznomatch999'] });
    expect(text).toBe('(no matches)');
  });

  it('css-classes lens returns class frequency array sorted descending', async () => {
    const result = parseJSON(await callTool(client, 'browser_see_dom', { lens: ['css-classes'] }));
    expect(Array.isArray(result)).toBe(true);
    expect(result.length).toBeGreaterThan(0);
    // Each entry has {class, count}
    result.forEach(entry => {
      expect(typeof entry.class).toBe('string');
      expect(typeof entry.count).toBe('number');
      expect(entry.count).toBeGreaterThan(0);
    });
    // Sorted descending by count
    for (let i = 1; i < result.length; i++) {
      expect(result[i].count).toBeLessThanOrEqual(result[i - 1].count);
    }
    // Known classes from test.html should appear
    const names = result.map(e => e.class);
    expect(names).toContain('card');
  });

  it('css-classes lens respects exclude', async () => {
    const all = parseJSON(await callTool(client, 'browser_see_dom', { lens: ['css-classes'] }));
    const excluded = parseJSON(await callTool(client, 'browser_see_dom', { lens: ['css-classes'], exclude: '.card' }));
    const allCard = all.find(e => e.class === 'card');
    const exCard  = excluded.find(e => e.class === 'card');
    // card elements are excluded so count should drop or disappear
    expect((exCard?.count ?? 0)).toBeLessThan(allCard.count);
  });
});
