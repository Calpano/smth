import { describe, it, beforeAll, afterAll, beforeEach, expect } from 'vitest';
import { createClient, callTool, expectToolError, TEST_PAGE } from './helpers.js';

async function getInputValue(client, id) {
  // Use code lens which includes value attribute on inputs
  const { text } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
  const match = text.match(new RegExp(`id="${id}"[^>]*value="([^"]*)"`));
  return match?.[1] ?? null;
}

describe('browser_type', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });
  beforeEach(async () => {
    await callTool(client, 'browser_launch', { url: TEST_PAGE });
  });

  it('types into input by id', async () => {
    const { text } = await callTool(client, 'browser_type', { id: 'name-input', text: 'Alice' });
    expect(text).toContain('Typed into #name-input');
  });

  it('typed value appears in DOM', async () => {
    await callTool(client, 'browser_type', { id: 'name-input', text: 'Bob' });
    const { text: dom } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
    expect(dom).toContain('Bob');
  });

  it('clear=true replaces existing value', async () => {
    await callTool(client, 'browser_type', { id: 'name-input', text: 'First', clear: true });
    await callTool(client, 'browser_type', { id: 'name-input', text: 'Second', clear: true });
    const { text: dom } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
    expect(dom).toContain('Second');
    expect(dom).not.toContain('FirstSecond');
  });

  it('clear=false appends to existing value', async () => {
    await callTool(client, 'browser_type', { id: 'name-input', text: 'Hello', clear: true });
    await callTool(client, 'browser_type', { id: 'name-input', text: ' World', clear: false });
    const { text: dom } = await callTool(client, 'browser_see_dom', { lens: ['text'] });
    expect(dom).toContain('Hello World');
  });

  it('accepts a CSS selector for inputs without id', async () => {
    const { text } = await callTool(client, 'browser_type', {
      selector: '#email-input',
      text: 'test@example.com',
    });
    expect(text).toContain('Typed into');
  });

  it('typing and submitting the form navigates away', async () => {
    await callTool(client, 'browser_type', { id: 'name-input', text: 'Dave' });
    const { text } = await callTool(client, 'browser_click', { id: 'submit-btn' });
    expect(text).toContain('articles');
  });

  it('throws when element is not found', async () => {
    await expectToolError(client, 'browser_type', { id: 'ghost-input', text: 'x' }, /not found|No node/i);
  });

  it('throws when neither id nor selector is provided', async () => {
    await expectToolError(client, 'browser_type', { text: 'x' }, 'Provide id or selector');
  });
});
