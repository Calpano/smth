import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';

import { browserSessions } from './state.js';
import { tools, toolsByName } from './tools/index.js';

export function createMcpServer(sessionId) {
  const server = new Server(
    { name: 'smth', version: '1.0.0' },
    { capabilities: { tools: {} } }
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: tools.map(t => t.schema),
  }));

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const name = request.params.name;
    const args = request.params.arguments ?? {};
    const tool = toolsByName.get(name);
    if (!tool) throw new Error(`Unknown tool: ${name}`);

    let session;
    if (tool.needsSession !== false) {
      session = browserSessions.get(sessionId);
      if (!session) throw new Error('No open browser session. Call browser_launch first.');
    }
    return tool.handler({ sessionId, session }, args);
  });

  return server;
}
