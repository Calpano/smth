import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON } from './helpers.js';

const SVG_PAGE = 'test-svg.html';

describe('SVG support', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: SVG_PAGE });
  });
  afterAll(async () => { await client.close(); });

  // ── browser_read_text discovers SVG interactive elements ──────────────

  it('browser_read_text lists SVG elements with role="button"', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('Interactive Elements');
    // icon-heart and icon-star have role="button"
    expect(text).toContain('#icon-heart');
    expect(text).toContain('#icon-star');
    // aria-labels should appear
    expect(text).toContain('Like');
    expect(text).toContain('Favorite');
  });

  it('browser_read_text lists SVG elements with onclick', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    expect(text).toContain('#icon-settings');
    expect(text).toContain('Settings');
  });

  it('browser_read_text lists SVG chart bars with cursor:pointer', async () => {
    const { text } = await callTool(client, 'browser_read_text');
    // Bar elements have role="img" and cursor:pointer
    expect(text).toContain('#bar-a');
    expect(text).toContain('Category A');
  });

  // ── browser_see_dom svg lens ──────────────────────────────────────────

  it('svg lens returns SVG structure with attributes', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['svg'] });
    expect(text).toContain('<svg');
    expect(text).toContain('<path');
    expect(text).toContain('<circle');
    expect(text).toContain('<rect');
    expect(text).toContain('viewBox=');
    expect(text).toContain('fill=');
    expect(text).toContain('stroke=');
  });

  it('svg lens includes g elements with transforms', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['svg'] });
    expect(text).toContain('<g');
    expect(text).toContain('transform=');
  });

  it('svg lens includes gradient definitions', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['svg'] });
    // Chromium lowercases SVG tag names in the DOM
    expect(text.toLowerCase()).toContain('<lineargradient');
    expect(text.toLowerCase()).toContain('stop');
    expect(text).toContain('stop-color=');
  });

  it('svg lens includes use/symbol elements', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['svg'] });
    expect(text.toLowerCase()).toContain('<use');
    expect(text.toLowerCase()).toContain('<symbol');
    expect(text).toContain('href=');
  });

  it('svg lens includes text elements', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['svg'] });
    // SVG <text> element content
    expect(text).toContain('SVG Label');
    expect(text).toContain('text-anchor=');
  });

  it('svg lens omits non-SVG HTML elements', async () => {
    const { text } = await callTool(client, 'browser_see_dom', { lens: ['svg'] });
    // Non-SVG elements should not appear (h1, h2, div, etc.)
    expect(text).not.toContain('<h1');
    expect(text).not.toContain('<h2');
    expect(text).not.toContain('<div');
  });

  // ── browser_see_colors picks up SVG fill/stroke ───────────────────────

  it('browser_see_colors includes SVG fill colors', async () => {
    const result = parseJSON(await callTool(client, 'browser_see_colors', { only: ['fill'] }));
    expect(result.length).toBeGreaterThan(0);
    const colors = result.map(e => e.color.toLowerCase());
    // #e74c3c is the heart fill and bar-b fill
    expect(colors).toContain('#e74c3c');
    // categories should include 'fill'
    expect(result[0].categories).toContain('fill');
  });

  it('browser_see_colors includes SVG stroke colors', async () => {
    const result = parseJSON(await callTool(client, 'browser_see_colors', { only: ['stroke'] }));
    expect(result.length).toBeGreaterThan(0);
    expect(result[0].categories).toContain('stroke');
  });

  // ── browser_see_color_pairs includes SVG fill vs background ───────────

  it('browser_see_color_pairs includes SVG elements', async () => {
    const result = parseJSON(await callTool(client, 'browser_see_color_pairs'));
    expect(result.length).toBeGreaterThan(0);
    // Should have entries from SVG fills — the red heart (#e74c3c) against white bg
    const texts = result.map(e => e.text.toLowerCase());
    expect(texts.some(t => t.includes('e74c3c') || t.includes('3498db') || t.includes('2ecc71'))).toBe(true);
  });

  // ── browser_see_fonts includes SVG text ───────────────────────────────

  it('browser_see_fonts includes fonts from SVG text elements', async () => {
    const result = parseJSON(await callTool(client, 'browser_see_fonts'));
    expect(result.length).toBeGreaterThan(0);
    // SVG text uses Arial
    const families = result.map(e => e.family.toLowerCase());
    expect(families.some(f => f.includes('arial'))).toBe(true);
  });

  // ── browser_click works on SVG elements ───────────────────────────────

  it('browser_click works on SVG element with onclick', async () => {
    const { text } = await callTool(client, 'browser_click', { id: 'icon-settings' });
    expect(text).toContain('Clicked');
    // Verify the onclick handler ran
    const { text: pageText } = await callTool(client, 'browser_read_text');
    expect(pageText).toContain('Settings clicked!');
  });

  it('browser_click works on SVG element by CSS selector', async () => {
    // Click heart icon via selector
    const { text } = await callTool(client, 'browser_click', { selector: '#icon-heart' });
    expect(text).toContain('Clicked');
  });
});
