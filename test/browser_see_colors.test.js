import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON, TEST_PAGE } from './helpers.js';

describe('browser_see_colors', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns valid JSON array', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors'));
    expect(Array.isArray(colors)).toBe(true);
    expect(colors.length).toBeGreaterThan(0);
  });

  it('contains known background color #1a1a2e (nav/footer)', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors'));
    const entry = colors.find(c => c.color?.toLowerCase() === '#1a1a2e');
    expect(entry).toBeDefined();
    expect(entry.count).toBeGreaterThan(0);
  });

  it('contains error text color #e74c3c', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors'));
    const entry = colors.find(c => c.color?.toLowerCase() === '#e74c3c');
    expect(entry).toBeDefined();
  });

  it('each entry has color, count, and categories', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors'));
    for (const c of colors) {
      expect(typeof c.color).toBe('string');
      expect(typeof c.count).toBe('number');
      expect(Array.isArray(c.categories)).toBe(true);
    }
  });

  it('only= filter restricts to background colors', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors', { only: ['background'] }));
    for (const c of colors) {
      expect(c.categories).toContain('background');
    }
  });

  it('colors= filter returns only requested colors', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors', { colors: ['#e74c3c', '#27ae60'] }));
    const hexes = colors.map(c => c.color.toLowerCase());
    for (const hex of hexes) {
      expect(['#e74c3c', '#27ae60']).toContain(hex);
    }
  });

  it('where=true adds selector breakdown', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors', { where: true, colors: ['#1a1a2e'] }));
    const entry = colors.find(c => c.color?.toLowerCase() === '#1a1a2e');
    expect(entry?.where).toBeDefined();
  });

  it('returns empty array for unknown color', async () => {
    const colors = parseJSON(await callTool(client, 'browser_see_colors', { colors: ['#badbad'] }));
    expect(colors).toEqual([]);
  });
});
