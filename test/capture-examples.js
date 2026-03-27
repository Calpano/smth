/**
 * Captures example output for the new MCP tools using quotes.toscrape.com.
 * Run inside the test Docker container: it connects to smth via SMTH_URL.
 */
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { SSEClientTransport } from '@modelcontextprotocol/sdk/client/sse.js';

const BASE = process.env.SMTH_URL || 'http://localhost:3000';

async function createClient() {
  const transport = new SSEClientTransport(new URL(`${BASE}/sse`));
  const client = new Client({ name: 'example-capture', version: '1.0.0' }, { capabilities: {} });
  await client.connect(transport);
  return client;
}

async function call(client, name, args = {}) {
  const res = await client.callTool({ name, arguments: args });
  if (res.isError) throw new Error(res.content[0]?.text);
  return res.content[0]?.text ?? '';
}

const client = await createClient();
const results = {};

// 1. Launch
await call(client, 'browser_launch', { url: 'https://quotes.toscrape.com/' });

// 2. browser_goto — navigate to page 2
const gotoResult = await call(client, 'browser_goto', { url: 'https://quotes.toscrape.com/page/2/' });
results.browser_goto = { input: { url: 'https://quotes.toscrape.com/page/2/' }, output: gotoResult };

// 3. browser_type — go to login, type username
await call(client, 'browser_goto', { url: 'https://quotes.toscrape.com/login' });
const typeResult = await call(client, 'browser_type', { id: 'username', text: 'testuser' });
results.browser_type = { input: { id: 'username', text: 'testuser' }, output: typeResult };

// 4. browser_remember_dom — back to home, snapshot two pages
await call(client, 'browser_launch', { url: 'https://quotes.toscrape.com/' });
const rememberResult = await call(client, 'browser_remember_dom', { name: 'home', lens: ['text'] });
results.browser_remember_dom = { input: { name: 'home', lens: ['text'] }, output: rememberResult };

// Navigate to page 2 and take second snapshot
await call(client, 'browser_goto', { url: 'https://quotes.toscrape.com/page/2/' });
await call(client, 'browser_remember_dom', { name: 'page2', lens: ['text'] });

// 5. browser_doms — list snapshots
const domsResult = await call(client, 'browser_doms');
results.browser_doms = { input: {}, output: domsResult };

// 6. browser_dom_compare — diff mode
await call(client, 'browser_goto', { url: 'https://quotes.toscrape.com/' });
const compareResult = await call(client, 'browser_dom_compare', { a: 'home', b: 'page2' });
results.browser_dom_compare = {
  input: { a: 'home', b: 'page2' },
  output: compareResult,
};

// 7. fetch_dom_content
const fetchResult = await call(client, 'fetch_dom_content', {
  url: 'https://quotes.toscrape.com/',
  lens: ['text'],
  prefix: 'quotes',
});
results.fetch_dom_content = {
  input: { url: 'https://quotes.toscrape.com/', lens: ['text'], prefix: 'quotes' },
  output: fetchResult,
};

await client.close();

console.log(JSON.stringify(results, null, 2));
