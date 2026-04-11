// Given a base URL and a list of candidate URLs, return the candidate on the
// same host with the most similar path (longest common path prefix, penalising
// depth difference). Supports both http(s):// and file:// URLs.
export function findMostSimilarUrl(baseUrl, candidates) {
  let base;
  try { base = new URL(baseUrl); } catch { return null; }
  if (!/^(https?|file):$/.test(base.protocol)) return null;
  const baseParts = base.pathname.split('/').filter(Boolean);

  let best = null, bestScore = -Infinity;
  for (const url of candidates) {
    let u;
    try { u = new URL(url); } catch { continue; }
    if (u.protocol !== base.protocol) continue;
    if (u.host !== base.host) continue;
    if (u.pathname === base.pathname) continue;   // skip same-path (hash variants)

    const parts = u.pathname.split('/').filter(Boolean);
    let common = 0;
    for (let i = 0; i < Math.min(baseParts.length, parts.length); i++) {
      if (baseParts[i] === parts[i]) common++; else break;
    }
    const score = common * 10 - Math.abs(parts.length - baseParts.length);
    if (score > bestScore) { bestScore = score; best = url; }
  }
  return best;
}
