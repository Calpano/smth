import { lineDiff, subtractLines } from '../util/diff.js';

export default {
  schema: {
    name: 'browser_dom_compare',
    description: 'Diff two snapshots (a vs b), or subtract a background snapshot from a single snapshot to get its essential content (a minus subtract).',
    annotations: { readOnlyHint: true },
    inputSchema: {
      type: 'object',
      properties: {
        a:        { type: 'string', description: 'Name of the first (or only) snapshot.' },
        b:        { type: 'string', description: 'Name of the second snapshot. Required when not using subtract.' },
        subtract: { type: 'string', description: 'Name of a background snapshot. When provided, returns lines in a that are absent from this snapshot (foreground content). Mutually exclusive with b.' },
      },
      required: ['a'],
    },
  },
  async handler({ session }, args) {
    const { snapshots } = session;
    const { a, b, subtract } = args;
    const snapA = snapshots.get(a);
    if (!snapA) throw new Error(`Snapshot "${a}" not found. Call browser_doms to list available snapshots.`);

    if (subtract) {
      const snapSub = snapshots.get(subtract);
      if (!snapSub) throw new Error(`Snapshot "${subtract}" not found. Call browser_doms to list available snapshots.`);
      const foreground = subtractLines(snapA.html, snapSub.html);
      return { content: [{ type: 'text', text: foreground || '(empty after background subtraction)' }] };
    }

    if (!b) throw new Error('Provide b (second snapshot name) or subtract (background snapshot name).');
    const snapB = snapshots.get(b);
    if (!snapB) throw new Error(`Snapshot "${b}" not found. Call browser_doms to list available snapshots.`);
    return { content: [{ type: 'text', text: lineDiff(snapA.html, snapB.html) }] };
  },
};
