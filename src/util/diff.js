// Line-level diff/intersection utilities used by snapshots and hover.

// Unified-diff-ish output: removed lines prefixed "- ", added lines prefixed "+ ".
// Returns "(no diff)" when both strings produce the same line set.
export function lineDiff(before, after) {
  const setA = new Set(before.split('\n'));
  const setB = new Set(after.split('\n'));
  const removed = before.split('\n').filter(l => !setB.has(l)).map(l => `- ${l}`);
  const added   = after.split('\n').filter(l => !setA.has(l)).map(l => `+ ${l}`);
  return [...removed, ...added].join('\n') || '(no diff)';
}

// Line-level intersection of two HTML strings (the "background").
export function lineIntersection(htmlA, htmlB) {
  const setB = new Set(htmlB.split('\n').filter(l => l.trim()));
  return htmlA.split('\n').filter(l => l.trim() && setB.has(l)).join('\n');
}

// Remove lines in subtractHtml from html.
export function subtractLines(html, subtractHtml) {
  const bg = new Set(subtractHtml.split('\n').filter(l => l.trim()));
  return html.split('\n').filter(l => !bg.has(l)).join('\n');
}
