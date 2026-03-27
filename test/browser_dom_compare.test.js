import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE, TEST_PAGE_B } from './helpers.js';

describe('browser_dom_compare', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
    await callTool(client, 'browser_remember_dom', { name: 'home', lens: ['text'] });
    await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    await callTool(client, 'browser_remember_dom', { name: 'articles', lens: ['text'] });
    await callTool(client, 'browser_goto', { url: TEST_PAGE });
  });
  afterAll(async () => { await client.close(); });

  // ── Diff mode ────────────────────────────────────────────────────────────

  it('diff mode: returns lines unique to each page', async () => {
    const { text } = await callTool(client, 'browser_dom_compare', { a: 'home', b: 'articles' });
    // Home-unique content → removed lines
    expect(text).toContain('-');
    expect(text).toContain('Welcome to smth test page');
    // Articles-unique content → added lines
    expect(text).toContain('+');
    expect(text).toContain('Articles');
  });

  it('diff mode: shared nav/footer lines are absent from diff', async () => {
    const { text } = await callTool(client, 'browser_dom_compare', { a: 'home', b: 'articles' });
    // Nav/footer shared by both pages — should not appear in diff
    const diffLines = text.split('\n').filter(l => l.startsWith('+') || l.startsWith('-'));
    // Check specifically for <a>…</a> nav links, not just any element containing "Home"/"Articles"
    const hasNavLink = diffLines.some(l => l.includes('<a>Home</a>') || l.includes('<a>Articles</a>'));
    expect(hasNavLink).toBe(false);
  });

  it('diff mode: identical snapshots return (no diff)', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'same-a', lens: ['text'] });
    await callTool(client, 'browser_remember_dom', { name: 'same-b', lens: ['text'] });
    const { text } = await callTool(client, 'browser_dom_compare', { a: 'same-a', b: 'same-b' });
    expect(text).toBe('(no diff)');
  });

  it('diff mode: before/after a DOM mutation shows the change', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'pre-type', lens: ['text'] });
    await callTool(client, 'browser_type', { id: 'name-input', text: 'Mutated' });
    await callTool(client, 'browser_remember_dom', { name: 'post-type', lens: ['text'] });
    const { text } = await callTool(client, 'browser_dom_compare', { a: 'pre-type', b: 'post-type' });
    expect(text).toContain('Mutated');
  });

  // ── Subtract mode ────────────────────────────────────────────────────────

  it('subtract mode: returns foreground content without background', async () => {
    // Build a background snapshot: nav+footer lines common to both pages
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
    await callTool(client, 'browser_remember_dom', { name: 'pg-home', lens: ['text'] });
    await callTool(client, 'browser_goto', { url: TEST_PAGE_B });
    await callTool(client, 'browser_remember_dom', { name: 'pg-b', lens: ['text'] });

    // Subtract pg-b from pg-home → leaves only home-unique content
    const { text } = await callTool(client, 'browser_dom_compare', {
      a: 'pg-home',
      subtract: 'pg-b',
    });
    expect(text).toContain('Welcome to smth test page');
    expect(text).not.toContain('>Home<'); // nav shared → subtracted
  });

  it('subtract mode: self-subtraction returns empty', async () => {
    await callTool(client, 'browser_remember_dom', { name: 'self' });
    const { text } = await callTool(client, 'browser_dom_compare', { a: 'self', subtract: 'self' });
    expect(text).toBe('(empty after background subtraction)');
  });

  // ── Error cases ──────────────────────────────────────────────────────────

  it('throws when snapshot a does not exist', async () => {
    await expectToolError(
      client, 'browser_dom_compare', { a: 'no-such', b: 'home' },
      /not found/i,
    );
  });

  it('throws when b is missing and no subtract provided', async () => {
    // Create a fresh snapshot to ensure 'a' exists, then omit 'b' and 'subtract'
    await callTool(client, 'browser_remember_dom', { name: 'tmp-for-error' });
    await expectToolError(
      client, 'browser_dom_compare', { a: 'tmp-for-error' },
      /Provide b/i,
    );
  });
});
