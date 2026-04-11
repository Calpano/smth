import express from 'express';
import { SSEServerTransport } from '@modelcontextprotocol/sdk/server/sse.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { randomUUID } from 'crypto';

import { PORT, PAGES_DIR } from './config.js';
import { transports, sseTransports, browserSessions } from './state.js';
import { createMcpServer } from './mcpServer.js';

const app = express();

// Streamable HTTP endpoint — handles all MCP traffic (POST, GET, DELETE)
app.all('/mcp', express.json(), async (req, res) => {
  if (req.method === 'POST') {
    const sessionId = req.headers['mcp-session-id'];

    if (sessionId) {
      // Route to existing session
      const transport = transports.get(sessionId);
      if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
      await transport.handleRequest(req, res, req.body);
      return;
    }

    // New session — generate ID upfront so createMcpServer can capture it
    const newSessionId = randomUUID();
    const transport = new StreamableHTTPServerTransport({
      // Constant generator: we pre-mint the UUID above so createMcpServer
      // (called below) can key browser sessions by the same id the transport
      // will use. Omitting this option would put the transport into stateless
      // mode and break the `transports.get(sessionId)` lookup in GET/DELETE.
      // LSP may flag this as "unused" — false positive; the SDK invokes it
      // through a private field.
      sessionIdGenerator: () => newSessionId,
    });

    transport.onclose = async () => {
      transports.delete(newSessionId);
      const bs = browserSessions.get(newSessionId);
      if (bs) {
        await bs.browser.close().catch(() => {});
        browserSessions.delete(newSessionId);
      }
    };

    const server = createMcpServer(newSessionId);
    transports.set(newSessionId, transport);
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
    return;
  }

  if (req.method === 'GET' || req.method === 'DELETE') {
    const sessionId = req.headers['mcp-session-id'];
    const transport = transports.get(sessionId);
    if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
    await transport.handleRequest(req, res);
    return;
  }

  res.status(405).json({ error: 'Method not allowed' });
});

// Legacy SSE endpoint — for clients that don't support Streamable HTTP (e.g. Claude Code)
app.get('/sse', async (req, res) => {
  const transport = new SSEServerTransport('/messages', res);
  const server = createMcpServer(transport.sessionId);
  sseTransports.set(transport.sessionId, transport);

  res.on('close', async () => {
    sseTransports.delete(transport.sessionId);
    const session = browserSessions.get(transport.sessionId);
    if (session) {
      await session.browser.close().catch(() => {});
      browserSessions.delete(transport.sessionId);
    }
  });

  await server.connect(transport);
});

app.post('/messages', express.json(), async (req, res) => {
  const transport = sseTransports.get(req.query.sessionId);
  if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
  await transport.handlePostMessage(req, res, req.body);
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', service: 'smth', sessions: transports.size + sseTransports.size });
});

app.listen(PORT, () => {
  console.log(`smth ready on port ${PORT}`);
  console.log(`  Streamable HTTP: http://localhost:${PORT}/mcp`);
  console.log(`  SSE (legacy):    http://localhost:${PORT}/sse`);
  console.log(`  Health check:    http://localhost:${PORT}/health`);
  console.log(`  Pages dir:       ${PAGES_DIR}`);
});
