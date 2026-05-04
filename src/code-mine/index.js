import { Project, SyntaxKind } from 'ts-morph';
import { existsSync } from 'fs';
import path from 'path';
import {
  extractSvelteScript,
  extractRunes,
  findSvelteFiles,
  parseImportsFromScript,
  resolveImportSpecifier,
} from './svelte.js';

/**
 * Mine a TypeScript (and Svelte 5) project for structural relationships.
 *
 * Returns an array of triples: [subject, predicate, object]
 */
export function mineProject(projectPath, opts = {}) {
  const tsconfigPath = findTsConfig(projectPath);

  const project = new Project({
    tsConfigFilePath: tsconfigPath,
    skipAddingFilesFromTsConfig: false,
    skipFileDependencyResolution: false,
  });

  const ignoreDirs = ['node_modules', '.svelte-kit', 'build', 'dist', '.git'];

  // If no tsconfig found, add files manually
  if (!tsconfigPath) {
    project.addSourceFilesAtPaths(path.join(projectPath, '**/*.{ts,tsx}'));
  }

  // Also pick up .svelte.ts files that tsconfig may not include
  project.addSourceFilesAtPaths([
    path.join(projectPath, '**/*.svelte.{ts,js}'),
    ...ignoreDirs.map(d => '!' + path.join(projectPath, d, '**')),
  ]);

  const triples = [];

  // ── Mine regular TS / .svelte.ts files via ts-morph ───────────────────────
  const sourceFiles = project.getSourceFiles();

  for (const sf of sourceFiles) {
    const filePath = relative(projectPath, sf.getFilePath());

    // Skip files outside the project or in ignored directories
    if (filePath.startsWith('..') || ignoreDirs.some(d => filePath.startsWith(d + '/') || filePath.startsWith(d + path.sep))) {
      continue;
    }

    // file has_type classification
    triples.push([filePath, 'has_type', classifyFile(filePath)]);

    // file imports file
    for (const imp of sf.getImportDeclarations()) {
      const resolved = imp.getModuleSpecifierSourceFile();
      if (resolved) {
        const target = relative(projectPath, resolved.getFilePath());
        if (target.startsWith('..') || ignoreDirs.some(d => target.startsWith(d + '/'))) continue;
        triples.push([filePath, 'imports', target]);
      }
    }

    mineSourceFile(sf, filePath, triples);
  }

  // ── Mine .svelte component files ──────────────────────────────────────────
  const svelteFiles = findSvelteFiles(projectPath);
  for (const fullPath of svelteFiles) {
    const filePath = relative(projectPath, fullPath);
    triples.push([filePath, 'has_type', 'component']);

    const extracted = extractSvelteScript(fullPath);
    if (!extracted) continue;

    // Parse imports from the raw script text (handles .svelte and $lib imports)
    const rawImports = parseImportsFromScript(extracted.scriptContent);
    for (const imp of rawImports) {
      const resolved = resolveImportSpecifier(imp.specifier, filePath, projectPath);
      if (resolved) {
        triples.push([filePath, 'imports', resolved]);
      }
    }

    // Create a virtual source file so ts-morph can analyze types/classes
    const virtualPath = fullPath + '.__script__.ts';
    const virtualSf = project.createSourceFile(virtualPath, extracted.scriptContent, {
      overwrite: true,
    });

    mineSourceFile(virtualSf, filePath, triples);

    // Clean up virtual file
    project.removeSourceFile(virtualSf);

    // Extract rune usages
    mineRunes(extracted.scriptContent, filePath, triples);
  }

  // Also extract runes from .svelte.ts files (they use runes too)
  for (const sf of sourceFiles) {
    const fp = relative(projectPath, sf.getFilePath());
    if (fp.endsWith('.svelte.ts') || fp.endsWith('.svelte.js')) {
      mineRunes(sf.getFullText(), fp, triples);
    }
  }

  return shortenFilePaths(triples);
}

/**
 * Shorten file paths in triples to the minimal unique suffix.
 * e.g. "src/lib/geo/geometry.ts" and "src/lib/hex/geometry.ts"
 * become "geo/geometry.ts" and "hex/geometry.ts".
 */
function shortenFilePaths(triples) {
  // Collect all unique file paths (anything containing '/' or ending in a known extension)
  const fileExt = /\.(ts|tsx|js|jsx|svelte|svelte\.ts|svelte\.js|md|mdx)$/;
  const allPaths = new Set();
  for (const [s, , o] of triples) {
    if (fileExt.test(s)) allPaths.add(s);
    if (fileExt.test(o)) allPaths.add(o);
  }

  // Build mapping: full path -> shortest unique suffix
  const paths = [...allPaths];
  const shortMap = new Map();

  for (const p of paths) {
    const parts = p.split('/');
    // Start with just the filename, add parent segments until unique
    for (let n = 1; n <= parts.length; n++) {
      const candidate = parts.slice(parts.length - n).join('/');
      const conflicts = paths.filter(
        other => other !== p && other.endsWith(candidate)
      );
      if (conflicts.length === 0) {
        shortMap.set(p, candidate);
        break;
      }
    }
    // If still not set (shouldn't happen), keep full path
    if (!shortMap.has(p)) shortMap.set(p, p);
  }

  // Apply mapping to all triples
  return triples.map(([s, p, o]) => [
    shortMap.get(s) ?? s,
    p,
    shortMap.get(o) ?? o,
  ]);
}

/**
 * Extract type definitions, methods, tests from a ts-morph SourceFile.
 */
function mineSourceFile(sf, filePath, triples) {
  for (const cls of sf.getClasses()) {
    const name = cls.getName();
    if (!name) continue;
    triples.push([filePath, 'defines', name]);
    mineType(cls, name, triples, filePath);
  }

  for (const iface of sf.getInterfaces()) {
    const name = iface.getName();
    triples.push([filePath, 'defines', name]);
    mineInterface(iface, name, triples, filePath);
  }

  for (const alias of sf.getTypeAliases()) {
    const name = alias.getName();
    triples.push([filePath, 'defines', name]);
  }

  for (const en of sf.getEnums()) {
    const name = en.getName();
    triples.push([filePath, 'defines', name]);
  }

  if (classifyFile(filePath) === 'test') {
    mineTestFile(sf, filePath, triples);
  }
}

/**
 * Extract Svelte 5 rune usages and emit triples.
 * Predicates: uses_state, uses_derived, uses_props, uses_bindable, uses_effect
 */
function mineRunes(scriptContent, filePath, triples) {
  const runes = extractRunes(scriptContent);
  for (const { variable, rune } of runes) {
    // Map rune name to predicate
    const predicate = runePredicateMap[rune];
    if (!predicate) continue;
    if (variable) {
      triples.push([filePath, predicate, variable]);
    } else {
      // $effect or destructured $props — file-level
      triples.push([filePath, predicate, '*']);
    }
  }
}

const runePredicateMap = {
  '$state':      'uses_state',
  '$derived':    'uses_derived',
  '$derived.by': 'uses_derived',
  '$props':      'uses_props',
  '$bindable':   'uses_bindable',
  '$effect':     'uses_effect',
};

function mineType(cls, typeName, triples, filePath) {
  // type extends / implements
  try {
    const baseClass = cls.getBaseClass();
    if (baseClass?.getName()) {
      triples.push([typeName, 'extends', baseClass.getName()]);
    }
  } catch {
    // Type checker can't resolve base class (e.g. missing tsconfig extends)
    // Fall back to text-based extraction from the heritage clause
    const ext = cls.getExtends();
    if (ext) {
      const extName = ext.getText().replace(/<.*>/, '');
      triples.push([typeName, 'extends', extName]);
    }
  }
  for (const impl of cls.getImplements()) {
    const implName = impl.getText().replace(/<.*>/, '');
    triples.push([typeName, 'implements', implName]);
  }

  // type defines method
  for (const method of cls.getMethods()) {
    const qualified = `${typeName}.${method.getName()}`;
    triples.push([typeName, 'defines', qualified]);
    mineMethod(method, qualified, triples);
  }

  // constructor parameters (often define types used)
  const ctor = cls.getConstructors()[0];
  if (ctor) {
    mineMethod(ctor, `${typeName}.constructor`, triples);
  }
}

function mineInterface(iface, typeName, triples, filePath) {
  // interface extends
  for (const ext of iface.getExtends()) {
    const extName = ext.getText().replace(/<.*>/, '');
    triples.push([typeName, 'extends', extName]);
  }

  // interface defines method/property
  for (const method of iface.getMethods()) {
    const qualified = `${typeName}.${method.getName()}`;
    triples.push([typeName, 'defines', qualified]);
    mineMethodSignature(method, qualified, triples);
  }

  for (const prop of iface.getProperties()) {
    const propName = prop.getName();
    const typeText = safeExtractTypeName(prop);
    if (typeText) {
      triples.push([`${typeName}.${propName}`, 'has_type', typeText]);
    }
  }
}

function mineMethod(method, qualifiedName, triples) {
  // method gets type (parameters)
  for (const param of method.getParameters()) {
    const typeText = safeExtractTypeName(param);
    if (typeText) {
      triples.push([qualifiedName, 'gets', typeText]);
    }
  }

  // method returns type
  try {
    const returnType = method.getReturnType();
    const returnText = extractTypeName(returnType);
    if (returnText) {
      triples.push([qualifiedName, 'returns', returnText]);
    }
  } catch {
    // Type checker unavailable — try text-based fallback
    const retNode = method.getReturnTypeNode?.();
    if (retNode) {
      const text = retNode.getText().replace(/<.*>/, '').trim();
      if (text && !/^[a-z]/.test(text)) {
        triples.push([qualifiedName, 'returns', text]);
      }
    }
  }
}

function mineMethodSignature(method, qualifiedName, triples) {
  for (const param of method.getParameters()) {
    const typeText = safeExtractTypeName(param);
    if (typeText) {
      triples.push([qualifiedName, 'gets', typeText]);
    }
  }

  try {
    const returnType = method.getReturnType();
    const returnText = extractTypeName(returnType);
    if (returnText) {
      triples.push([qualifiedName, 'returns', returnText]);
    }
  } catch {
    // Type checker unavailable
  }
}

/**
 * Safely extract type name from a node, falling back to text-based extraction
 * when the type checker is unavailable (e.g. missing tsconfig extends).
 */
function safeExtractTypeName(node) {
  try {
    return extractTypeName(node.getType());
  } catch {
    // Fall back to type annotation text
    const typeNode = node.getTypeNode?.();
    if (typeNode) {
      const text = typeNode.getText().replace(/<.*>/, '').trim();
      if (text && /^[A-Z]/.test(text)) return text;
    }
    return null;
  }
}

function mineTestFile(sf, filePath, triples) {
  // Look for describe/it/test calls and extract method names being tested
  const calls = sf.getDescendantsOfKind(SyntaxKind.CallExpression);
  for (const call of calls) {
    const expr = call.getExpression();
    const fnName = expr.getText();
    if (fnName === 'it' || fnName === 'test' || fnName === 'describe') {
      const args = call.getArguments();
      if (args.length > 0) {
        const testName = args[0].getText().replace(/^['"`]|['"`]$/g, '');
        triples.push([filePath, 'tests', testName]);
      }
    }
  }
}

/**
 * Extract a meaningful type name from a ts-morph Type.
 * Strips generics, unions of primitives, etc. Returns null for primitives.
 */
function extractTypeName(type) {
  if (!type) return null;

  const text = type.getText(undefined, /* TypeFormatFlags.None */ 0);

  // Skip primitives and intrinsics
  if (/^(string|number|boolean|void|undefined|null|never|any|unknown|symbol|bigint)$/.test(text)) {
    return null;
  }

  // For arrays, extract the element type
  if (type.isArray()) {
    const elemType = type.getArrayElementType();
    return extractTypeName(elemType);
  }

  // For Promise<T>, extract T
  const match = text.match(/^Promise<(.+)>$/);
  if (match) return match[1];

  // Strip generics for the base type name
  const baseName = text.replace(/<.*>/, '').trim();
  if (!baseName || /^[a-z]/.test(baseName)) return null; // skip lowercase (primitives)

  return baseName;
}

function classifyFile(filePath) {
  if (/\.(test|spec)\.[^.]+$/.test(filePath) || filePath.includes('__tests__')) {
    return 'test';
  }
  if (/\.(md|mdx)$/.test(filePath) || filePath.startsWith('doc')) {
    return 'doc';
  }
  if (/\.svelte\.(ts|js)$/.test(filePath)) {
    return 'reactive-module';
  }
  // .svelte files are classified as 'component' directly in the mining loop
  return 'code';
}

function findTsConfig(projectPath) {
  const candidates = ['tsconfig.json', 'tsconfig.app.json'];
  for (const name of candidates) {
    const full = path.join(projectPath, name);
    if (existsSync(full)) return full;
  }
  return undefined;
}

function relative(base, filePath) {
  return path.relative(base, filePath);
}
