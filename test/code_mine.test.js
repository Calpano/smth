import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { createClient, callTool, parseJSON } from './helpers.js';

describe('code_mine', () => {
  let client;
  beforeAll(async () => { client = await createClient(); });
  afterAll(async ()  => { await client.close(); });

  const mine = (args = {}) =>
    callTool(client, 'code_mine', { path: '/test-fixtures/ts-sample', format: 'triples', ...args });

  it('returns ddot format by default', async () => {
    const result = await callTool(client, 'code_mine', { path: '/test-fixtures/ts-sample' });
    // ddot format: lines like "subject ..predicate.. object"
    expect(result.text).toContain('..');
    expect(result.text).toContain('models.ts');
    // Should NOT be JSON
    expect(result.text[0]).not.toBe('[');
  });

  it('returns triples as JSON array', async () => {
    const result = await mine();
    const triples = JSON.parse(result.text);
    expect(Array.isArray(triples)).toBe(true);
    expect(triples.length).toBeGreaterThan(0);
    // Each triple has 3 elements
    for (const t of triples) {
      expect(t).toHaveLength(3);
    }
  });

  it('extracts file imports file', async () => {
    const triples = JSON.parse((await mine()).text);
    const imports = triples.filter(([, p]) => p === 'imports');
    expect(imports.length).toBeGreaterThan(0);
    // service.ts imports models.ts
    const serviceImport = imports.find(
      ([s, , o]) => s.includes('service.ts') && !s.includes('.test.') && o.includes('models.ts')
    );
    expect(serviceImport).toBeDefined();
  });

  it('extracts file defines type', async () => {
    const triples = JSON.parse((await mine()).text);
    const defines = triples.filter(
      ([s, p]) => p === 'defines' && s.includes('models.ts')
    );
    const typeNames = defines.map(([, , o]) => o);
    expect(typeNames).toContain('Dog');
    expect(typeNames).toContain('Animal');
    expect(typeNames).toContain('Toy');
    expect(typeNames).toContain('DogBreed');
  });

  it('extracts type defines method with qualified name', async () => {
    const triples = JSON.parse((await mine()).text);
    const dogMethods = triples.filter(
      ([s, p]) => s === 'Dog' && p === 'defines'
    );
    const methodNames = dogMethods.map(([, , o]) => o);
    expect(methodNames).toContain('Dog.sound');
    expect(methodNames).toContain('Dog.fetch');
  });

  it('extracts type implements interface', async () => {
    const triples = JSON.parse((await mine()).text);
    const impl = triples.find(
      ([s, p, o]) => s === 'Dog' && p === 'implements' && o === 'Animal'
    );
    expect(impl).toBeDefined();
  });

  it('extracts method gets type (parameters)', async () => {
    const triples = JSON.parse((await mine()).text);
    const gets = triples.filter(([, p]) => p === 'gets');
    // Dog.fetch gets Toy
    const fetchParam = gets.find(
      ([s, , o]) => s === 'Dog.fetch' && o === 'Toy'
    );
    expect(fetchParam).toBeDefined();
  });

  it('extracts method returns type', async () => {
    const triples = JSON.parse((await mine()).text);
    const returns = triples.filter(([, p]) => p === 'returns');
    // Dog.fetch returns Toy
    const fetchReturn = returns.find(
      ([s, , o]) => s === 'Dog.fetch' && o === 'Toy'
    );
    expect(fetchReturn).toBeDefined();
  });

  it('classifies files with has_type', async () => {
    const triples = JSON.parse((await mine()).text);
    const types = triples.filter(([, p]) => p === 'has_type');
    const codeFile = types.find(([s, , o]) => s.includes('models.ts') && o === 'code');
    const testFile = types.find(([s, , o]) => s.includes('.test.ts') && o === 'test');
    expect(codeFile).toBeDefined();
    expect(testFile).toBeDefined();
  });

  it('extracts tests from test files', async () => {
    const triples = JSON.parse((await mine()).text);
    const tests = triples.filter(([, p]) => p === 'tests');
    expect(tests.length).toBeGreaterThan(0);
    const testNames = tests.map(([, , o]) => o);
    expect(testNames).toContain('adds a dog');
    expect(testNames).toContain('plays fetch');
  });

  it('filters by predicate', async () => {
    const triples = JSON.parse(
      (await mine({ filter: { predicate: ['imports'] } })).text
    );
    expect(triples.length).toBeGreaterThan(0);
    for (const [, p] of triples) {
      expect(p).toBe('imports');
    }
  });

  it('filters by file', async () => {
    const triples = JSON.parse(
      (await mine({ filter: { file: 'service.ts' } })).text
    );
    expect(triples.length).toBeGreaterThan(0);
    for (const [s, , o] of triples) {
      expect(s.includes('service.ts') || o.includes('service.ts')).toBe(true);
    }
  });

  it('returns grouped format', async () => {
    const result = await mine({ format: 'grouped' });
    const grouped = JSON.parse(result.text);
    expect(typeof grouped).toBe('object');
    expect(Array.isArray(grouped)).toBe(false);
    // Each value is an array of [predicate, object] pairs
    for (const pairs of Object.values(grouped)) {
      expect(Array.isArray(pairs)).toBe(true);
      for (const pair of pairs) {
        expect(pair).toHaveLength(2);
      }
    }
  });
});
