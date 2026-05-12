import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON } from './helpers.js';

const HOME_OK       = 'test-imprint-home.html';
const HOME_BAD      = 'test-imprint-bad-home.html';
const HOME_NO_LINK  = 'test-imprint-missing-link.html';

describe('browser_check_imprint', () => {
  let client;
  beforeAll(async () => {
    client = await createClient();
    await callTool(client, 'browser_launch', { url: HOME_OK });
  });
  afterAll(async () => { await client.close(); });

  it('passes when imprint link exists and all required fields are present', async () => {
    const result = await callTool(client, 'browser_check_imprint', { url: HOME_OK });
    const data = parseJSON(result);
    expect(data.ok).toBe(true);
    expect(data.reason).toBeNull();
    expect(data.imprint.found).toBe(true);
    expect(data.imprint.link_text).toBe('Impressum');
    expect(data.imprint.url).toMatch(/test-imprint\.html$/);
    expect(data.imprint.fields.name.ok).toBe(true);
    expect(data.imprint.fields.name.match).toContain('Max Mustermann');
    expect(data.imprint.fields.address.ok).toBe(true);
    expect(data.imprint.fields.address.match).toMatch(/Beispielstraße 12/);
    expect(data.imprint.fields.email.ok).toBe(true);
    expect(data.imprint.fields.email.match).toBe('max@example.com');
  });

  it('reports missing email field and ok=false on incomplete imprint', async () => {
    const result = await callTool(client, 'browser_check_imprint', { url: HOME_BAD });
    const data = parseJSON(result);
    expect(data.ok).toBe(false);
    expect(data.reason).toMatch(/email/i);
    expect(data.imprint.found).toBe(true);
    expect(data.imprint.fields.email.ok).toBe(false);
    expect(data.imprint.fields.name.ok).toBe(true);
    expect(data.imprint.fields.address.ok).toBe(true);
  });

  it('reports also_check link presence', async () => {
    const result = await callTool(client, 'browser_check_imprint', { url: HOME_OK });
    const data = parseJSON(result);
    expect(Array.isArray(data.also_check)).toBe(true);
    const ds = data.also_check.find(x => x.label === 'Datenschutz');
    expect(ds).toBeDefined();
    expect(ds.found).toBe(true);
  });

  it('reports also_check absent when label not present on home', async () => {
    const result = await callTool(client, 'browser_check_imprint', {
      url: HOME_OK,
      also_check: ['NichtVorhanden'],
    });
    const data = parseJSON(result);
    const entry = data.also_check.find(x => x.label === 'NichtVorhanden');
    expect(entry.found).toBe(false);
    expect(entry.url).toBeNull();
  });

  it('falls back to rules on the current page when no link is found', async () => {
    const result = await callTool(client, 'browser_check_imprint', { url: HOME_NO_LINK });
    const data = parseJSON(result);
    expect(data.ok).toBe(false);
    expect(data.reason).toMatch(/not found/i);
    expect(data.imprint.found).toBe(false);
    expect(data.imprint.url).toBeNull();
    expect(data.imprint.fields).toBeDefined();
    // The fallback runs the rules — fields exist but the test page lacks them.
    expect(data.imprint.fields.email.ok).toBe(false);
  });

  it('honours a custom required_fields list', async () => {
    const result = await callTool(client, 'browser_check_imprint', {
      url: HOME_OK,
      required_fields: ['name', 'phone'],
    });
    const data = parseJSON(result);
    expect(Object.keys(data.imprint.fields).sort()).toEqual(['name', 'phone']);
    expect(data.imprint.fields.phone.ok).toBe(true);
    // The default rule captures a digits-and-separators run after Tel:/Telefon/Phone.
    // We don't pin the exact slice (greedy-match behaviour varies), just require
    // it to be a reasonable phone-shaped substring.
    expect(data.imprint.fields.phone.match).toMatch(/[\d\s+]{5,}/);
  });

  it('honours custom link_text labels', async () => {
    // The fixture only has 'Impressum'; asking for English-only labels should fail to find it.
    const result = await callTool(client, 'browser_check_imprint', {
      url: HOME_OK,
      link_text: ['Imprint'],
    });
    const data = parseJSON(result);
    expect(data.imprint.found).toBe(false);
  });
});
