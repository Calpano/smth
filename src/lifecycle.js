// Session lifecycle: idle reaper, max-sessions cap, and graceful shutdown.

import { transports, sseTransports, lastActivity, disposeSession, sessionCount } from './state.js';

// Defaults; overridable via env so production can tune without a code change.
export const SESSION_IDLE_MS = Number(process.env.SMTH_SESSION_IDLE_MS ?? 10 * 60 * 1000);
export const REAPER_INTERVAL_MS = Number(process.env.SMTH_REAPER_INTERVAL_MS ?? 60 * 1000);
export const MAX_SESSIONS = Number(process.env.SMTH_MAX_SESSIONS ?? 20);

let reaperTimer = null;

// Close one session. Prefers transport.close() so the SDK fires its onclose
// hook (which calls disposeSession). Falls back to direct disposal if there's
// no transport — e.g. partial state after a crashed onclose.
export async function closeSession(sessionId) {
  const transport = transports.get(sessionId) ?? sseTransports.get(sessionId);
  if (transport) await transport.close().catch(() => {});
  // Belt-and-braces: if onclose didn't fire (older SDK versions, errors), make
  // sure the maps are clean. disposeSession is idempotent.
  await disposeSession(sessionId);
}

export async function reapIdleSessions(now = Date.now()) {
  const reaped = [];
  for (const [sessionId, ts] of lastActivity) {
    if (now - ts > SESSION_IDLE_MS) reaped.push(sessionId);
  }
  for (const id of reaped) {
    await closeSession(id);
    console.log(`[reaper] closed idle session ${id}`);
  }
  return reaped.length;
}

export function startReaper() {
  if (reaperTimer) return;
  reaperTimer = setInterval(() => {
    reapIdleSessions().catch(err => console.error('[reaper] error:', err));
  }, REAPER_INTERVAL_MS);
  reaperTimer.unref?.();
}

export function stopReaper() {
  if (reaperTimer) {
    clearInterval(reaperTimer);
    reaperTimer = null;
  }
}

export function oldestIdleAgeMs(now = Date.now()) {
  let oldest = 0;
  for (const ts of lastActivity.values()) oldest = Math.max(oldest, now - ts);
  return oldest;
}

export function atSessionCap() {
  return sessionCount() >= MAX_SESSIONS;
}

export async function shutdown() {
  stopReaper();
  const ids = [...new Set([...transports.keys(), ...sseTransports.keys()])];
  await Promise.all(ids.map(id => closeSession(id)));
}

export function installSignalHandlers(server) {
  let shuttingDown = false;
  const onSignal = async (sig) => {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log(`[shutdown] received ${sig}, closing ${sessionCount()} session(s)`);
    await shutdown();
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 5000).unref?.();
  };
  process.on('SIGTERM', () => onSignal('SIGTERM'));
  process.on('SIGINT', () => onSignal('SIGINT'));
}
