import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE, TEST_PAGE_B } from './helpers.js';

describe('browser_goto', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('navigates to a different page and returns its title', async () => {
    const { text } = await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    expect(text).toMatch(/Navigated to:/);
    expect(text).toContain('articles');
  });

  it('can navigate back', async () => {
    const { text } = await callTool(client, 'browser_goto', { url: TEST_PAGE });
    expect(text).toContain('home');
  });

  it('preserves session snapshots across navigation', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'goto-persist-test' });
    await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    const { text } = await callTool(client, 'browser_doms');
    expect(text).toContain('goto-persist-test');
  });

  it('throws when called without a browser session', async () => {
    const fresh = await createClient();
    await expectToolError(fresh, 'browser_goto', { url: TEST_PAGE }, 'browser_launch');
    await fresh.close();
  });
});
