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
