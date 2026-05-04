import { mineProject } from '../code-mine/index.js';

/**
 * Format triples as ddot.it plain text.
 * Groups by subject, uses continuation lines for same-subject triples.
 * Spec: https://ddot.it/syntax.html
 */
function formatDdot(triples) {
  // Group by subject, preserving order of first appearance
  const groups = new Map();
  for (const [s, p, o] of triples) {
    if (!groups.has(s)) groups.set(s, []);
    groups.get(s).push([p, o]);
  }

  const lines = [];
  for (const [subject, pairs] of groups) {
    const [firstP, firstO] = pairs[0];
    lines.push(`${subject} ..${firstP}.. ${firstO}`);
    for (let i = 1; i < pairs.length; i++) {
      lines.push(`  ..${pairs[i][0]}.. ${pairs[i][1]}`);
    }
    lines.push('');
  }
  return lines.join('\n');
}

export default {
  needsSession: false,
  schema: {
    name: 'code_mine',
    description:
      'Mine a TypeScript project for structural relationships. Returns triples: ' +
      '[subject, predicate, object]. Predicates: imports, defines, extends, implements, ' +
      'gets, returns, has_type, tests, documented_in. ' +
      'Set CODE_DIR env var to mount the target codebase into the container.',
    annotations: { readOnlyHint: true, destructiveHint: false },
    inputSchema: {
      type: 'object',
      properties: {
        path: {
          type: 'string',
          description:
            'Path to the project root (inside the container). Defaults to /code.',
        },
        filter: {
          type: 'object',
          description: 'Optional filters to limit output.',
          properties: {
            predicate: {
              type: 'array',
              items: { type: 'string' },
              description:
                'Only include triples with these predicates (e.g. ["imports", "defines"]).',
            },
            file: {
              type: 'string',
              description: 'Only include triples involving this file (substring match).',
            },
            type: {
              type: 'string',
              description: 'Only include triples involving this type name (substring match).',
            },
          },
        },
        format: {
          type: 'string',
          enum: ['ddot', 'triples', 'grouped'],
          description: 'Output format. "ddot" = ddot.it plain-text (default, most token-efficient), "triples" = JSON array, "grouped" = JSON grouped by subject.',
        },
      },
    },
  },

  async handler(_ctx, args) {
    const projectPath = args.path || process.env.CODE_DIR || '/code';
    const format = args.format || 'ddot';

    let triples = mineProject(projectPath);

    // Apply filters
    if (args.filter) {
      const { predicate, file, type } = args.filter;
      if (predicate?.length) {
        triples = triples.filter(([, p]) => predicate.includes(p));
      }
      if (file) {
        triples = triples.filter(
          ([s, , o]) => s.includes(file) || o.includes(file)
        );
      }
      if (type) {
        triples = triples.filter(
          ([s, , o]) => s.includes(type) || o.includes(type)
        );
      }
    }

    let output;
    if (format === 'ddot') {
      output = formatDdot(triples);
    } else if (format === 'grouped') {
      const groups = {};
      for (const [s, p, o] of triples) {
        (groups[s] ??= []).push([p, o]);
      }
      output = JSON.stringify(groups, null, 2);
    } else {
      output = JSON.stringify(triples);
    }

    return { content: [{ type: 'text', text: output }] };
  },
};
