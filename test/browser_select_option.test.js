import { describe, it, beforeAll, afterAll, beforeEach, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

async function getSelectValue(client) {
  // The test page wires a change-listener that writes "picked:<value>" to #country-echo.
  const { text } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
  return text.match(/picked:([a-z]+)/)?.[1] ?? null;
}

describe('browser_select_option', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });
  beforeEach(async () => {
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });

  it('selects an option by value', async () => {
    const { text } = await callTool(client, 'browser_select_option', { id: 'country-select', value: 'de' });
    expect(text).toContain('Selected "Germany"');
    expect(text).toContain('value="de"');
  });

  it('selected value is reflected in the live DOM', async () => {
    await callTool(client, 'browser_select_option', { id: 'country-select', value: 'jp' });
    const v = await getSelectValue(client);
    expect(v).toBe('jp');
  });

  it('selects an option by exact label', async () => {
    const { text } = await callTool(client, 'browser_select_option', { id: 'country-select', label: 'France' });
    expect(text).toContain('value="fr"');
    const v = await getSelectValue(client);
    expect(v).toBe('fr');
  });

  it('label match is case-insensitive', async () => {
    const { text } = await callTool(client, 'browser_select_option', { id: 'country-select', label: 'germany' });
    expect(text).toContain('value="de"');
  });

  it('label match falls back to substring', async () => {
    const { text } = await callTool(client, 'browser_select_option', { id: 'country-select', label: 'United' });
    expect(text).toContain('value="us"');
  });

  it('accepts a CSS selector', async () => {
    const { text } = await callTool(client, 'browser_select_option', { selector: 'select[name="country"]', value: 'fr' });
    expect(text).toContain('value="fr"');
  });

  it('dispatches a change event so listeners fire', async () => {
    await callTool(client, 'browser_select_option', { id: 'country-select', value: 'jp' });
    const { text: dom } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
    expect(dom).toContain('picked:jp');
  });

  it('throws when element is not a <select>', async () => {
    await expectToolError(
      client,
      'browser_select_option',
      { id: 'name-input', value: 'x' },
      /not a <select>/i,
    );
  });

  it('throws when option value does not exist', async () => {
    await expectToolError(
      client,
      'browser_select_option',
      { id: 'country-select', value: 'zz' },
      /No option matching value="zz"/,
    );
  });

  it('throws when option label does not exist', async () => {
    await expectToolError(
      client,
      'browser_select_option',
      { id: 'country-select', label: 'Atlantis' },
      /No option matching label="Atlantis"/,
    );
  });

  it('throws when neither value nor label is provided', async () => {
    await expectToolError(
      client,
      'browser_select_option',
      { id: 'country-select' },
      'Provide value or label',
    );
  });

  it('throws when neither id nor selector is provided', async () => {
    await expectToolError(
      client,
      'browser_select_option',
      { value: 'us' },
      'Provide id or selector',
    );
  });
});
