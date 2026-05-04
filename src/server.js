import express from 'express';
import { SSEServerTransport } from '@modelcontextprotocol/sdk/server/sse.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { randomUUID } from 'crypto';

import { PORT, PAGES_DIR } from './config.js';
import {
  transports, sseTransports,
  touchSession, disposeSession, sessionCount,
} from './state.js';
import {
  startReaper, installSignalHandlers, atSessionCap,
  oldestIdleAgeMs, MAX_SESSIONS, SESSION_IDLE_MS,
} from './lifecycle.js';
import { createMcpServer } from './mcpServer.js';

const app = express();

// Streamable HTTP endpoint — handles all MCP traffic (POST, GET, DELETE)
app.all('/mcp', express.json(), async (req, res) => {
  if (req.method === 'POST') {
    const sessionId = req.headers['mcp-session-id'];

    if (sessionId) {
      const transport = transports.get(sessionId);
      if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
      touchSession(sessionId);
      await transport.handleRequest(req, res, req.body);
      return;
    }

    if (atSessionCap()) {
      res.status(503).json({
        error: `Server at max sessions (${MAX_SESSIONS}). Idle sessions are reaped after ${SESSION_IDLE_MS}ms; retry shortly.`,
      });
      return;
    }

    // Generate ID upfront so createMcpServer can capture it
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

    transport.onclose = () => disposeSession(newSessionId);

    const server = createMcpServer(newSessionId);
    transports.set(newSessionId, transport);
    touchSession(newSessionId);
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
    return;
  }

  if (req.method === 'GET' || req.method === 'DELETE') {
    const sessionId = req.headers['mcp-session-id'];
    const transport = transports.get(sessionId);
    if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
    touchSession(sessionId);
    await transport.handleRequest(req, res);
    return;
  }

  res.status(405).json({ error: 'Method not allowed' });
});

// Legacy SSE endpoint — for clients that don't support Streamable HTTP (e.g. Claude Code)
app.get('/sse', async (req, res) => {
  if (atSessionCap()) {
    res.status(503).json({
      error: `Server at max sessions (${MAX_SESSIONS}). Idle sessions are reaped after ${SESSION_IDLE_MS}ms; retry shortly.`,
    });
    return;
  }
  const transport = new SSEServerTransport('/messages', res);
  const server = createMcpServer(transport.sessionId);
  sseTransports.set(transport.sessionId, transport);
  touchSession(transport.sessionId);

  res.on('close', () => disposeSession(transport.sessionId));

  await server.connect(transport);
});

app.post('/messages', express.json(), async (req, res) => {
  const transport = sseTransports.get(req.query.sessionId);
  if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
  touchSession(req.query.sessionId);
  await transport.handlePostMessage(req, res, req.body);
});

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'smth',
    sessions: sessionCount(),
    maxSessions: MAX_SESSIONS,
    idleTimeoutMs: SESSION_IDLE_MS,
    oldestIdleAgeMs: oldestIdleAgeMs(),
  });
});

// Friendly landing for plain GETs at the root — `/mcp` deliberately rejects
// non-MCP clients with 406, so dropping a curl on the root used to give a
// confusing 404 instead of a hint about what this thing is.
app.get('/', (_req, res) => {
  res.type('text/plain').send(
    'smth MCP server\n\n' +
    'MCP endpoints (require MCP client headers):\n' +
    '  POST/GET/DELETE /mcp    Streamable HTTP transport\n' +
    '  GET /sse                Legacy SSE transport\n\n' +
    'Status: GET /health\n'
  );
});

const httpServer = app.listen(PORT, () => {
  console.log(`smth ready on port ${PORT}`);
  console.log(`  Streamable HTTP: http://localhost:${PORT}/mcp`);
  console.log(`  SSE (legacy):    http://localhost:${PORT}/sse`);
  console.log(`  Health check:    http://localhost:${PORT}/health`);
  console.log(`  Pages dir:       ${PAGES_DIR}`);
  console.log(`  Max sessions:    ${MAX_SESSIONS}, idle timeout: ${SESSION_IDLE_MS}ms`);
});

startReaper();
installSignalHandlers(httpServer);
