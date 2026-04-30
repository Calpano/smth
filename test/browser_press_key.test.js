import { describe, it, beforeAll, afterAll, beforeEach, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

async function getInputValue(client, id) {
  const { text } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
  return text.match(new RegExp(`id="${id}"[^>]*value="([^"]*)"`))?.[1] ?? null;
}

describe('browser_press_key', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });
  beforeEach(async () => {
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });

  it('presses a key with no element focus', async () => {
    const { text } = await callTool(client, 'browser_press_key', { key: 'Tab' });
    expect(text).toContain('Pressed Tab');
  });

  it('focuses element via id and types a character', async () => {
    await callTool(client, 'browser_press_key', { id: 'name-input', key: 'a' });
    const v = await getInputValue(client, 'name-input');
    expect(v).toBe('a');
  });

  it('reports the modifier combo in the response', async () => {
    const { text } = await callTool(client, 'browser_press_key', { key: 'a', modifiers: ['Control', 'Shift'] });
    expect(text).toContain('Pressed Control+Shift+a');
  });

  it('count repeats the press', async () => {
    await callTool(client, 'browser_press_key', { id: 'name-input', key: 'x', count: 5 });
    const v = await getInputValue(client, 'name-input');
    expect(v).toBe('xxxxx');
  });

  it('Backspace deletes the last character', async () => {
    await callTool(client, 'browser_type', { id: 'name-input', text: 'hello' });
    await callTool(client, 'browser_press_key', { key: 'Backspace' });
    const v = await getInputValue(client, 'name-input');
    expect(v).toBe('hell');
  });

  it('Enter on a focused form input submits the form', async () => {
    await callTool(client, 'browser_type', { id: 'name-input', text: 'someone' });
    const { text } = await callTool(client, 'browser_press_key', { id: 'name-input', key: 'Enter' });
    // The form action navigates to test-b.html (title contains "articles").
    expect(text).toMatch(/articles/i);
  });

  it('Tab moves focus to the next field', async () => {
    await callTool(client, 'browser_press_key', { id: 'name-input', key: 'a' });
    await callTool(client, 'browser_press_key', { key: 'Tab' });
    // Now email-input should be focused; typing a char should land there.
    await callTool(client, 'browser_press_key', { key: 'b' });
    const name = await getInputValue(client, 'name-input');
    const email = await getInputValue(client, 'email-input');
    expect(name).toBe('a');
    expect(email).toBe('b');
  });

  it('accepts a CSS selector for focus', async () => {
    const { text } = await callTool(client, 'browser_press_key', {
      selector: '#email-input',
      key: 'q',
    });
    expect(text).toContain('focused #email-input');
    const v = await getInputValue(client, 'email-input');
    expect(v).toBe('q');
  });

  it('throws on unknown modifier', async () => {
    await expectToolError(
      client,
      'browser_press_key',
      { key: 'a', modifiers: ['Hyper'] },
      /Invalid modifier "Hyper"/,
    );
  });

  it('throws when focus selector does not match', async () => {
    await expectToolError(
      client,
      'browser_press_key',
      { id: 'no-such-element', key: 'a' },
      /not found|No node/i,
    );
  });
});
