export default {
  schema: {
    name: 'browser_doms',
    description: 'List all named DOM snapshots stored in this session.',
    annotations: { readOnlyHint: true },
    inputSchema: { type: 'object', properties: {} },
  },
  async handler({ session }) {
    const { snapshots } = session;
    if (!snapshots.size) return { content: [{ type: 'text', text: '(no snapshots)' }] };
    const lines = [...snapshots.entries()].map(([name, s]) =>
      `${name}  ${s.chars} chars  lens:${s.lens ? s.lens.join('+') : 'full'}  ${s.timestamp}`
    );
    return { content: [{ type: 'text', text: lines.join('\n') }] };
  },
};
