// Format an MCP tool result for terminal output.
//
// Tool handlers return `{ content: [{type, text|data, ...}] }`. Most smth
// tools return a single text block, sometimes JSON-encoded. Image content
// (browser_see_visual) is written to a temp file so the terminal isn't
// flooded with base64.

import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

export function renderResult(result) {
  if (result.isError) {
    const text = result.content?.find(c => c.type === 'text')?.text ?? 'Tool error';
    process.stderr.write(text + '\n');
    process.exitCode = 1;
    return;
  }
  for (const part of result.content ?? []) {
    if (part.type === 'text') {
      process.stdout.write(part.text);
      if (!part.text.endsWith('\n')) process.stdout.write('\n');
    } else if (part.type === 'image') {
      const dir = join(tmpdir(), 'smth');
      mkdirSync(dir, { recursive: true });
      const ext = (part.mimeType ?? 'image/png').split('/')[1] ?? 'png';
      const path = join(dir, `smth-${Date.now()}.${ext}`);
      writeFileSync(path, Buffer.from(part.data, 'base64'));
      process.stdout.write(`[image saved to ${path}]\n`);
    } else {
      process.stdout.write(`[unknown content type: ${part.type}]\n`);
    }
  }
}
