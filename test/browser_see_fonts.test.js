import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON, TEST_PAGE } from './helpers.js';

describe('browser_see_fonts', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns valid JSON', async () => {
    const result = await callTool(client, 'browser_see_fonts');
    expect(() => parseJSON(result)).not.toThrow();
  });

  it('includes Georgia (body font)', async () => {
    const fonts = parseJSON(await callTool(client, 'browser_see_fonts'));
    const georgia = fonts.find(f => f.family?.toLowerCase().includes('georgia'));
    expect(georgia).toBeDefined();
    expect(georgia.count).toBeGreaterThan(0);
  });

  it('includes Arial (nav/button font)', async () => {
    const fonts = parseJSON(await callTool(client, 'browser_see_fonts'));
    const arial = fonts.find(f => f.family?.toLowerCase().includes('arial'));
    expect(arial).toBeDefined();
  });

  it('each entry has sizes and weights arrays', async () => {
    const fonts = parseJSON(await callTool(client, 'browser_see_fonts'));
    for (const font of fonts) {
      expect(Array.isArray(font.sizes)).toBe(true);
      expect(Array.isArray(font.weights)).toBe(true);
      expect(font.sizes.length).toBeGreaterThan(0);
    }
  });

  it('has no duplicate family names', async () => {
    const fonts = parseJSON(await callTool(client, 'browser_see_fonts'));
    const families = fonts.map(f => f.family);
    expect(families.length).toBe(new Set(families).size);
  });
});
