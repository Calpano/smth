import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON, TEST_PAGE } from './helpers.js';

describe('browser_see_color_pairs', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns a valid JSON array', async () => {
    const pairs = parseJSON(await callTool(client, 'browser_see_color_pairs'));
    expect(Array.isArray(pairs)).toBe(true);
    expect(pairs.length).toBeGreaterThan(0);
  });

  it('each entry has required fields with correct types', async () => {
    const pairs = parseJSON(await callTool(client, 'browser_see_color_pairs'));
    for (const p of pairs) {
      expect(typeof p.text).toBe('string');
      expect(typeof p.background).toBe('string');
      expect(typeof p.contrast).toBe('number');
      expect(p.contrast).toBeGreaterThan(0);
      expect(typeof p.aa).toBe('boolean');
      expect(typeof p.aaa).toBe('boolean');
      expect(typeof p.count).toBe('number');
    }
  });

  it('aa/aaa flags are consistent with contrast ratio', async () => {
    const pairs = parseJSON(await callTool(client, 'browser_see_color_pairs'));
    for (const p of pairs) {
      expect(p.aa).toBe(p.contrast >= 4.5);
      expect(p.aaa).toBe(p.contrast >= 7.0);
    }
  });

  it('nav text on dark background has high contrast and passes AAA', async () => {
    const pairs = parseJSON(await callTool(client, 'browser_see_color_pairs'));
    // Nav has #eee on #1a1a2e — very high contrast
    const navPair = pairs.find(p =>
      p.background?.toLowerCase().includes('1a1a2e') ||
      p.background?.toLowerCase() === '#1a1a2e'
    );
    expect(navPair).toBeDefined();
    expect(navPair.contrast).toBeGreaterThan(7);
    expect(navPair.aaa).toBe(true);
  });

  it('is sorted by count descending', async () => {
    const pairs = parseJSON(await callTool(client, 'browser_see_color_pairs'));
    for (let i = 1; i < pairs.length; i++) {
      expect(pairs[i - 1].count).toBeGreaterThanOrEqual(pairs[i].count);
    }
  });
});
