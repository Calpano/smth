import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

export const SMTH_URL = process.env.SMTH_URL ?? 'http://localhost:3000';
export const TEST_PAGE  = 'test.html';
export const TEST_PAGE_B = 'test-b.html';

/** Open a new MCP session to the smth server. */
export async function createClient() {
  const transport = new StreamableHTTPClientTransport(new URL(`${SMTH_URL}/mcp`));
  const client = new Client({ name: 'vitest', version: '1.0.0' }, { capabilities: {} });
  await client.connect(transport);
  return client;
}

/**
 * Call a tool and return { text, image, content }.
 * Throws if the tool returns isError: true.
 */
export async function callTool(client, name, args = {}) {
  const result = await client.callTool({ name, arguments: args });
  if (result.isError) {
    const msg = result.content.find(c => c.type === 'text')?.text ?? 'Tool error';
    throw new Error(msg);
  }
  return {
    text:    result.content.find(c => c.type === 'text')?.text ?? null,
    image:   result.content.find(c => c.type === 'image') ?? null,
    content: result.content,
  };
}

/** Parse the text response as JSON. */
export function parseJSON(result) {
  if (!result.text) throw new Error('No text content in result');
  return JSON.parse(result.text);
}

/** Assert that callTool throws and the message matches the pattern. */
export async function expectToolError(client, name, args, pattern) {
  try {
    await callTool(client, name, args);
    throw new Error('Expected tool to throw but it succeeded');
  } catch (err) {
    if (pattern instanceof RegExp) {
      if (!pattern.test(err.message)) {
        throw new Error(`Error message "${err.message}" did not match ${pattern}`);
      }
    } else if (typeof pattern === 'string') {
      if (!err.message.includes(pattern)) {
        throw new Error(`Error message "${err.message}" did not include "${pattern}"`);
      }
    }
  }
}
