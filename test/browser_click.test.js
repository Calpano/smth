import { describe, it, beforeAll, afterAll, beforeEach, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

describe('browser_click', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });
  beforeEach(async () => {
    // Reset to test page before each test so navigation side-effects don't bleed
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });

  it('clicks element by id with no navigation', async () => {
    const { text } = await callTool(client, 'browser_click', { id: 'btn-primary' });
    expect(text).toContain('Clicked #btn-primary');
    // Page should not have navigated
    expect(text).toContain('smth test');
  });

  it('clicks element by CSS selector (element without id)', async () => {
    const { text } = await callTool(client, 'browser_click', { selector: 'button.btn-secondary' });
    expect(text).toContain('Clicked button.btn-secondary');
  });

  it('waits for navigation when click triggers a page load', async () => {
    const { text } = await callTool(client, 'browser_click', { id: 'submit-btn' });
    // Tool should have waited and returned the new page title
    expect(text).toContain('articles');
    // Immediately reading DOM should show the new page
    const { text: dom } = await callTool(client, 'browser_read_text');
    expect(dom).toContain('Articles');
    expect(dom).not.toContain('Welcome to smth test page');
  });

  it('throws when element is not found', async () => {
    await expectToolError(client, 'browser_click', { id: 'ghost-element' }, /not found|No node/i);
  });

  it('throws when neither id nor selector is provided', async () => {
    await expectToolError(client, 'browser_click', {}, 'Provide id or selector');
  });
});
