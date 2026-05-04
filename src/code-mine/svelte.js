import { readFileSync, readdirSync, statSync } from 'fs';
import path from 'path';

/**
 * Extract the <script lang="ts"> content from a .svelte file.
 * Returns { scriptContent, startLine } or null if no TS script block.
 */
export function extractSvelteScript(filePath) {
  const source = readFileSync(filePath, 'utf8');
  // Match <script lang="ts"> or <script lang="ts" ...> (with module, generics, etc.)
  const re = /<script\b[^>]*\blang=["']ts["'][^>]*>([\s\S]*?)<\/script>/g;
  let match;
  const blocks = [];
  while ((match = re.exec(source)) !== null) {
    const tag = match[0];
    const isModule = /\bmodule\b/.test(tag.slice(0, tag.indexOf('>')));
    blocks.push({
      content: match[1],
      isModule,
      startLine: source.slice(0, match.index).split('\n').length,
    });
  }
  if (blocks.length === 0) return null;

  // Combine module + instance scripts (module first)
  const moduleBlocks = blocks.filter(b => b.isModule);
  const instanceBlocks = blocks.filter(b => !b.isModule);
  const combined = [...moduleBlocks, ...instanceBlocks].map(b => b.content).join('\n');
  return { scriptContent: combined, startLine: blocks[0].startLine };
}

/**
 * Walk a directory tree and collect all .svelte files.
 */
export function findSvelteFiles(dir) {
  const results = [];
  function walk(d) {
    for (const entry of readdirSync(d)) {
      if (entry === 'node_modules' || entry === '.svelte-kit' || entry === 'build') continue;
      const full = path.join(d, entry);
      const stat = statSync(full);
      if (stat.isDirectory()) {
        walk(full);
      } else if (entry.endsWith('.svelte')) {
        results.push(full);
      }
    }
  }
  walk(dir);
  return results;
}

/**
 * Parse import specifiers from raw script text using regex.
 * Returns array of { specifier, isType, names }.
 * This avoids needing ts-morph to resolve .svelte imports (which it can't).
 */
export function parseImportsFromScript(scriptContent) {
  const imports = [];
  // Match: import [type] { names } from 'specifier'
  // Match: import [type] Name from 'specifier'
  // Match: import 'specifier' (side-effect)
  const re = /import\s+(?:(type)\s+)?(?:\{([^}]*)\}|(\w+)(?:\s*,\s*\{([^}]*)\})?)\s+from\s+['"]([^'"]+)['"]/g;
  let m;
  while ((m = re.exec(scriptContent)) !== null) {
    const isType = !!m[1];
    const names = (m[2] || m[3] || '').trim();
    const specifier = m[5];
    imports.push({ specifier, isType, names });
  }
  // Side-effect imports
  const sideRe = /import\s+['"]([^'"]+)['"]/g;
  while ((m = sideRe.exec(scriptContent)) !== null) {
    imports.push({ specifier: m[1], isType: false, names: '' });
  }
  return imports;
}

/**
 * Extract Svelte 5 rune usages from script content.
 * Returns array of { variable, rune, expression }.
 *
 * Detects: $state, $derived, $derived.by, $props, $bindable, $effect
 */
export function extractRunes(scriptContent) {
  const runes = [];
  let m;

  // Match: let/const varName = $rune(...) or $rune.by(...)
  // Also handles destructured: let { a, b } = $props()
  const letRe = /(?:let|const)\s+(?:\{[^}]*\}|(\w+))(?:\s*:\s*[^=]+)?\s*=\s*(\$(?:state|derived|props|bindable)(?:\.by)?)\s*\(/g;
  while ((m = letRe.exec(scriptContent)) !== null) {
    const variable = m[1] || null;
    const rune = m[2];
    runes.push({ variable, rune });
  }

  // Match class property: fieldName: Type = $rune(...) or fieldName = $rune(...)
  const propRe = /^\s+(\w+)(?:\s*:\s*[^=]+)?\s*=\s*(\$(?:state|derived|props|bindable)(?:\.by)?)\s*\(/gm;
  while ((m = propRe.exec(scriptContent)) !== null) {
    const variable = m[1];
    const rune = m[2];
    // Avoid duplicating let/const matches
    if (!/(?:let|const)\s/.test(scriptContent.slice(Math.max(0, m.index - 10), m.index))) {
      runes.push({ variable, rune });
    }
  }

  // Match $effect(() => ...) — standalone, no assignment
  const effectRe = /\$effect\s*\(/g;
  while ((m = effectRe.exec(scriptContent)) !== null) {
    runes.push({ variable: null, rune: '$effect' });
  }

  return runes;
}

/**
 * Resolve a $lib/... import specifier to a relative file path.
 * Also resolves relative .svelte imports.
 */
export function resolveImportSpecifier(specifier, fromFile, projectPath) {
  let resolved = specifier;

  // Handle $lib alias
  if (resolved.startsWith('$lib/')) {
    resolved = resolved.replace('$lib/', 'src/lib/');
  } else if (resolved.startsWith('./') || resolved.startsWith('../')) {
    // Relative import — resolve from the importing file's directory
    const fromDir = path.dirname(fromFile);
    resolved = path.relative(projectPath, path.resolve(projectPath, fromDir, resolved));
  } else {
    // External package — skip
    return null;
  }

  // Try direct match, then with extensions
  const extensions = ['', '.ts', '.js', '.svelte', '.svelte.ts', '/index.ts', '/index.js'];
  for (const ext of extensions) {
    const candidate = path.join(projectPath, resolved + ext);
    try {
      statSync(candidate);
      return path.relative(projectPath, candidate);
    } catch {
      // continue
    }
  }
  return null;
}
