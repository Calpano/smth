import { describe, it, beforeAll, afterAll, beforeEach, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

describe('browser_hover', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });
  // Navigate back before each test to reset hover/CSS state (tooltip, etc.)
  beforeEach(async () => {
    await callTool(client, 'browser_goto', { url: TEST_PAGE });
  });

  it('hovering an element with no visible change returns (no diff)', async () => {
    // Run this first so the tooltip is not yet visible (fresh page from beforeAll)
    const { text } = await callTool(client, 'browser_hover', { id: 'page-title' });
    expect(text).toBe('(no diff)');
  });

  it('hovering tooltip host reveals tooltip text', async () => {
    const { text } = await callTool(client, 'browser_hover', { id: 'tip-host' });
    // The .tip-text goes from display:none to display:block — text becomes visible
    expect(text).toContain('+');
    expect(text).toContain('Tooltip appeared!');
  });

  it('accepts a CSS selector instead of id', async () => {
    // #tip-host as a CSS selector should behave identically to id="tip-host"
    const { text } = await callTool(client, 'browser_hover', { selector: '#tip-host' });
    expect(text).toContain('Tooltip appeared!');
  });

  it('diff output does not contain full raw DOM (is lens-filtered)', async () => {
    const { text } = await callTool(client, 'browser_hover', { id: 'tip-host' });
    // The text+code lens diff should be short — not thousands of chars
    expect(text.length).toBeLessThan(5000);
  });

  it('throws when element is not found', async () => {
    await expectToolError(client, 'browser_hover', { id: 'ghost' }, /not found|No node/i);
  });

  it('throws when neither id nor selector is provided', async () => {
    await expectToolError(client, 'browser_hover', {}, 'Provide id or selector');
  });

  it('hovers via :has-text() extension to reveal the tooltip', async () => {
    // The .tip span contains "hover over me" — match via :has-text on the span.
    const { text } = await callTool(client, 'browser_hover', {
      selector: "span:has-text('hover over me')",
    });
    expect(text).toContain('Tooltip appeared!');
  });
});
