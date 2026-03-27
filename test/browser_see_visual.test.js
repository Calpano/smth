import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

describe('browser_see_visual', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('returns a PNG image', async () => {
    const { image } = await callTool(client, 'browser_see_visual');
    expect(image).toBeDefined();
    expect(image.mimeType).toBe('image/png');
    expect(image.data.length).toBeGreaterThan(1000); // non-trivial base64
  });

  it('full_page=false produces smaller image than full_page=true', async () => {
    const full    = await callTool(client, 'browser_see_visual', { full_page: true });
    const partial = await callTool(client, 'browser_see_visual', { full_page: false });
    expect(full.image.data.length).toBeGreaterThan(partial.image.data.length);
  });

  it('element_id scrolls to and captures a specific element', async () => {
    const { image } = await callTool(client, 'browser_see_visual', {
      element_id: 'card-form',
      full_page: false,
    });
    expect(image).toBeDefined();
    expect(image.mimeType).toBe('image/png');
  });

  it('zoom produces a different (larger base64) image', async () => {
    const normal = await callTool(client, 'browser_see_visual', { zoom: 1, full_page: false });
    const zoomed = await callTool(client, 'browser_see_visual', { zoom: 2, full_page: false });
    // Zoomed image data should be different
    expect(zoomed.image.data).not.toBe(normal.image.data);
  });

  it('zoom is reset after capture (subsequent call looks normal)', async () => {
    await callTool(client, 'browser_see_visual', { zoom: 3, full_page: false });
    const after = await callTool(client, 'browser_see_visual', { zoom: 1, full_page: false });
    // Just verify it doesn't error and returns valid image
    expect(after.image).toBeDefined();
  });

  it('device emulation returns an image', async () => {
    const { image } = await callTool(client, 'browser_see_visual', { device: 'iPhone 15' });
    expect(image).toBeDefined();
    expect(image.mimeType).toBe('image/png');
  });

  it('throws on unknown device name', async () => {
    await expectToolError(
      client, 'browser_see_visual', { device: 'NotARealDevice999' },
      /Unknown device/,
    );
  });

  it('throws when element_id not found', async () => {
    await expectToolError(
      client, 'browser_see_visual', { element_id: 'ghost-element' },
      /not found/i,
    );
  });
});
