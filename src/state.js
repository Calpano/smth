// Process-wide state shared across routes and tools.
//
// One module owns these maps so the concurrency model is explicit: anyone
// that touches sessions or transports has to import from here, rather than
// closing over module-local variables in server.js.

// Active MCP transports keyed by sessionId (SSE and Streamable HTTP share this map)
export const transports = new Map();

// SSE transports need separate tracking for the legacy /messages POST route
export const sseTransports = new Map();

// Active browser sessions keyed by sessionId
// Each session: { browser, page, snapshots: Map<name, { html, lens, timestamp, chars }>, consoleLogs }
export const browserSessions = new Map();

// Last activity timestamp per session — used by the idle reaper to evict
// abandoned sessions. Streamable HTTP clients don't reliably send DELETE on
// disconnect, so without this every dropped client leaks a Chromium tree.
export const lastActivity = new Map();

export function touchSession(sessionId) {
  if (sessionId) lastActivity.set(sessionId, Date.now());
}

export function sessionCount() {
  return transports.size + sseTransports.size;
}

// Clean every map for a session. Called from transport.onclose so it must
// not itself trigger transport.close(); see lifecycle.closeSession() for the
// orchestrated path that fires onclose.
export async function disposeSession(sessionId) {
  transports.delete(sessionId);
  sseTransports.delete(sessionId);
  lastActivity.delete(sessionId);
  const bs = browserSessions.get(sessionId);
  if (bs) {
    await bs.browser.close().catch(() => {});
    browserSessions.delete(sessionId);
  }
}
