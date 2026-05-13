// Convert `--key=value`, `--key value`, `--flag`, `--no-flag` into an object
// shaped by a tool's JSONSchema `inputSchema`. Repeating an array-typed key
// (e.g. `--include error --include warning`) appends; a comma-separated value
// (`--include error,warning`) splits.

export function parseArgs(argv, inputSchema = {}) {
  const props = inputSchema.properties ?? {};
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const tok = argv[i];
    if (!tok.startsWith('--')) {
      throw new Error(`Positional arguments are not supported (got '${tok}'). Use --key=value.`);
    }
    let key, raw;
    const eq = tok.indexOf('=');
    if (eq >= 0) { key = tok.slice(2, eq); raw = tok.slice(eq + 1); }
    else {
      key = tok.slice(2);
      // boolean shorthand: --no-foo or bare --flag for boolean-typed props
      if (key.startsWith('no-')) {
        const realKey = key.slice(3);
        out[realKey] = false;
        continue;
      }
      const propType = props[key]?.type;
      if (propType === 'boolean') { out[key] = true; continue; }
      // otherwise consume the next token as value
      raw = argv[++i];
      if (raw === undefined) throw new Error(`Missing value for --${key}`);
    }
    coerce(out, key, raw, props[key]);
  }
  return out;
}

function coerce(out, key, raw, prop) {
  const type = prop?.type;
  if (type === 'number') {
    const n = Number(raw);
    if (Number.isNaN(n)) throw new Error(`--${key} expects a number, got '${raw}'`);
    out[key] = n;
    return;
  }
  if (type === 'boolean') {
    out[key] = raw === 'true' || raw === '1';
    return;
  }
  if (type === 'array') {
    const parts = raw.split(',').map(s => s.trim()).filter(Boolean);
    out[key] = out[key] ? [...out[key], ...parts] : parts;
    return;
  }
  out[key] = raw;
}

// Human-readable usage block for a tool, generated from its inputSchema.
export function formatToolHelp(toolSchema) {
  const lines = [];
  lines.push(`${toolSchema.name}`);
  if (toolSchema.description) lines.push('  ' + toolSchema.description);
  const props = toolSchema.inputSchema?.properties ?? {};
  const required = new Set(toolSchema.inputSchema?.required ?? []);
  if (Object.keys(props).length) {
    lines.push('');
    lines.push('  Arguments:');
    for (const [key, p] of Object.entries(props)) {
      const tag = required.has(key) ? '(required)' : '(optional)';
      const type = p.type ?? 'string';
      lines.push(`    --${key}  ${type} ${tag}`);
      if (p.description) lines.push(`        ${p.description}`);
    }
  }
  return lines.join('\n');
}
