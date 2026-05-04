import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool } from './helpers.js';

describe('code_mine (Svelte 5)', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });

  const mine = (args = {}) =>
    callTool(client, 'code_mine', { path: '/test-fixtures/svelte-sample', format: 'triples', ...args });

  it('discovers .svelte files as components', async () => {
    const triples = JSON.parse((await mine()).text);
    const components = triples.filter(
      ([, p, o]) => p === 'has_type' && o === 'component'
    );
    expect(components.length).toBe(2);
    const names = components.map(([s]) => s);
    expect(names.some(n => n.includes('ItemList.svelte'))).toBe(true);
    expect(names.some(n => n.includes('ItemCard.svelte'))).toBe(true);
  });

  it('extracts imports from .svelte files', async () => {
    const triples = JSON.parse((await mine()).text);
    const svelteImports = triples.filter(
      ([s, p]) => p === 'imports' && s.includes('ItemList.svelte')
    );
    const targets = svelteImports.map(([, , o]) => o);
    // ItemList imports store.svelte.ts, types.ts, and ItemCard.svelte
    expect(targets.some(t => t.includes('store.svelte.ts'))).toBe(true);
    expect(targets.some(t => t.includes('ItemCard.svelte'))).toBe(true);
  });

  it('extracts types defined in .svelte script blocks', async () => {
    const triples = JSON.parse((await mine()).text);
    // ItemList.svelte defines interface Props
    const defines = triples.filter(
      ([s, p, o]) => s.includes('ItemList.svelte') && p === 'defines' && o === 'Props'
    );
    expect(defines.length).toBe(1);
  });

  it('classifies .svelte.ts as reactive-module', async () => {
    const triples = JSON.parse((await mine()).text);
    const storeType = triples.find(
      ([s, p]) => s.includes('store.svelte.ts') && p === 'has_type'
    );
    expect(storeType).toBeDefined();
    expect(storeType[2]).toBe('reactive-module');
  });

  it('mines classes from .svelte.ts files', async () => {
    const triples = JSON.parse((await mine()).text);
    // store.svelte.ts defines ItemStore
    const defines = triples.filter(
      ([s, p, o]) => s.includes('store.svelte.ts') && p === 'defines' && o === 'ItemStore'
    );
    expect(defines.length).toBe(1);

    // ItemStore defines methods (qualified names)
    const methods = triples.filter(
      ([s, p]) => s === 'ItemStore' && p === 'defines'
    );
    const names = methods.map(([, , o]) => o);
    expect(names).toContain('ItemStore.add');
    expect(names).toContain('ItemStore.remove');
  });

  it('extracts method parameter types from .svelte.ts', async () => {
    const triples = JSON.parse((await mine()).text);
    // ItemStore.add gets Item
    const gets = triples.find(
      ([s, p, o]) => s === 'ItemStore.add' && p === 'gets' && o === 'Item'
    );
    expect(gets).toBeDefined();
  });

  it('classifies .test.ts files as test', async () => {
    const triples = JSON.parse((await mine()).text);
    const testType = triples.find(
      ([s, p, o]) => s.includes('store.test.ts') && p === 'has_type' && o === 'test'
    );
    expect(testType).toBeDefined();
  });

  it('extracts test names from test files', async () => {
    const triples = JSON.parse((await mine()).text);
    const tests = triples.filter(([, p]) => p === 'tests');
    const names = tests.map(([, , o]) => o);
    expect(names).toContain('adds an item');
    expect(names).toContain('removes an item');
  });

  it('extracts $state rune usage from .svelte files', async () => {
    const triples = JSON.parse((await mine()).text);
    // ItemList.svelte uses $state for 'search'
    const states = triples.filter(
      ([s, p]) => s.includes('ItemList.svelte') && p === 'uses_state'
    );
    expect(states.length).toBeGreaterThan(0);
    expect(states.some(([, , o]) => o === 'search')).toBe(true);
  });

  it('extracts $derived rune usage from .svelte files', async () => {
    const triples = JSON.parse((await mine()).text);
    // ItemList.svelte uses $derived for 'filtered'
    const derived = triples.filter(
      ([s, p]) => s.includes('ItemList.svelte') && p === 'uses_derived'
    );
    expect(derived.length).toBeGreaterThan(0);
    expect(derived.some(([, , o]) => o === 'filtered')).toBe(true);
  });

  it('extracts $props rune usage from .svelte files', async () => {
    const triples = JSON.parse((await mine()).text);
    const props = triples.filter(
      ([s, p]) => s.includes('ItemList.svelte') && p === 'uses_props'
    );
    expect(props.length).toBeGreaterThan(0);
  });

  it('extracts $state rune usage from .svelte.ts files', async () => {
    const triples = JSON.parse((await mine()).text);
    // store.svelte.ts uses $state for 'items'
    const states = triples.filter(
      ([s, p]) => s.includes('store.svelte.ts') && p === 'uses_state'
    );
    expect(states.length).toBeGreaterThan(0);
    expect(states.some(([, , o]) => o === 'items')).toBe(true);
  });

  it('handles mixed TS and Svelte in one project', async () => {
    const triples = JSON.parse((await mine()).text);
    const files = [...new Set(triples.filter(([, p]) => p === 'has_type').map(([s]) => s))];
    // Should have .ts, .svelte.ts, .svelte, and .test.ts files
    expect(files.some(f => f.endsWith('.ts') && !f.includes('.svelte') && !f.includes('.test'))).toBe(true);
    expect(files.some(f => f.endsWith('.svelte.ts'))).toBe(true);
    expect(files.some(f => f.endsWith('.svelte'))).toBe(true);
    expect(files.some(f => f.includes('.test.ts'))).toBe(true);
  });
});
