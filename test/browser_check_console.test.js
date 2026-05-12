import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON, expectToolError } from './helpers.js';

const CONSOLE_PAGE = 'test-console.html';
const CLEAN_PAGE   = 'test-console-clean.html';

describe('browser_check_console', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: CLEAN_PAGE });
  });
  afterAll(async () => { await client.close(); });

  it('captures console errors, warnings, and uncaught pageerrors', async () => {
    const result = await callTool(client, 'browser_check_console', { url: CONSOLE_PAGE });
    const data = parseJSON(result);
    expect(data.url).toMatch(/test-console\.html$/);
    expect(data.title).toBe('smth test — console');
    expect(data.counts.error).toBeGreaterThanOrEqual(1);
    expect(data.counts.warning).toBeGreaterThanOrEqual(1);
    expect(data.counts.pageerror).toBeGreaterThanOrEqual(1);
    const texts = data.entries.map(e => e.text).join('\n');
    expect(texts).toContain('boom-error');
    expect(texts).toContain('soft-warning');
    expect(texts).toContain('uncaught-explosion');
  });

  it('marks each entry with a level matching its source type', async () => {
    const result = await callTool(client, 'browser_check_console', { url: CONSOLE_PAGE });
    const data = parseJSON(result);
    const byLevel = Object.fromEntries(data.entries.map(e => [e.text, e.level]));
    expect(byLevel['boom-error']).toBe('error');
    expect(byLevel['soft-warning']).toBe('warning');
    const pageerrorEntry = data.entries.find(e => e.level === 'pageerror');
    expect(pageerrorEntry).toBeDefined();
    expect(pageerrorEntry.text).toContain('uncaught-explosion');
  });

  it('include filter narrows the results', async () => {
    const result = await callTool(client, 'browser_check_console', {
      url: CONSOLE_PAGE,
      include: ['error'],
    });
    const data = parseJSON(result);
    expect(Object.keys(data.counts)).toEqual(['error']);
    for (const e of data.entries) expect(e.level).toBe('error');
    expect(data.counts.error).toBeGreaterThanOrEqual(1);
  });

  it('returns an empty entries list for a clean page', async () => {
    const result = await callTool(client, 'browser_check_console', { url: CLEAN_PAGE });
    const data = parseJSON(result);
    expect(data.entries).toEqual([]);
    expect(data.counts.error).toBe(0);
    expect(data.counts.warning).toBe(0);
    expect(data.counts.pageerror).toBe(0);
  });

  it('clears leftover logs from prior navigation (no bleed-through)', async () => {
    // First load the noisy page so the buffer would fill.
    await callTool(client, 'browser_goto', { url: CONSOLE_PAGE });
    // Then ask for a check on the clean page — entries must be from the clean load.
    const result = await callTool(client, 'browser_check_console', { url: CLEAN_PAGE });
    const data = parseJSON(result);
    const allText = data.entries.map(e => e.text).join('\n');
    expect(allText).not.toContain('boom-error');
    expect(allText).not.toContain('uncaught-explosion');
  });

  it('rejects an unknown wait_until value', async () => {
    await expectToolError(client, 'browser_check_console',
      { url: CLEAN_PAGE, wait_until: 'banana' },
      /Invalid wait_until/);
  });
});
