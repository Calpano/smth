import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool } from './helpers.js';

describe('browser_list_devices', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });

  it('returns a non-empty list of device names', async () => {
    const { text } = await callTool(client, 'browser_list_devices');
    const devices = text.split('\n').filter(Boolean);
    expect(devices.length).toBeGreaterThan(50);
  });

  it('is alphabetically sorted', async () => {
    const { text } = await callTool(client, 'browser_list_devices');
    const devices = text.split('\n').filter(Boolean);
    const sorted = [...devices].sort();
    expect(devices).toEqual(sorted);
  });

  it('contains well-known device names', async () => {
    const { text } = await callTool(client, 'browser_list_devices');
    expect(text).toContain('iPhone');
    expect(text).toContain('iPad');
    expect(text).toContain('Pixel');
  });

  it('works without an active browser session', async () => {
    // stateless — should not throw even with no browser_launch
    const freshClient = await createClient();
    const { text } = await callTool(freshClient, 'browser_list_devices');
    expect(text.length).toBeGreaterThan(0);
    await freshClient.close();
  });
});
