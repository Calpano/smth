import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

describe('browser_launch', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });

  it('opens a local test page and returns its title', async () => {
    const { text } = await callTool(client, 'browser_launch', { url: TEST_PAGE });
    expect(text).toMatch(/Launched:/);
    expect(text).toContain('smth test');
  });

  it('opens a remote URL', async () => {
    const { text } = await callTool(client, 'browser_launch', { url: 'https://example.com' });
    expect(text).toMatch(/Launched:/);
    expect(text).toContain('Example');
  });

  it('replaces an existing session without error', async () => {
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
    const { text } = await callTool(client, 'browser_launch', { url: 'test-b.html' });
    expect(text).toContain('articles');
  });

  it('throws on an invalid/unreachable URL', async () => {
    await expectToolError(
      client, 'browser_launch',
      { url: 'https://this-domain-does-not-exist-smth.invalid' },
      /net::|ERR_|timeout|ENOTFOUND/i,
    );
  });
});
