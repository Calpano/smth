import express from 'express';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { SSEServerTransport } from '@modelcontextprotocol/sdk/server/sse.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import puppeteer, { KnownDevices } from 'puppeteer-core';
import { randomUUID } from 'crypto';
import { join, isAbsolute } from 'path';
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const seeFontsScript      = readFileSync(join(__dirname, 'see-fonts.js'),       'utf8');
const seeColorsScript     = readFileSync(join(__dirname, 'see-colors.js'),      'utf8');
const seeColorPairsScript = readFileSync(join(__dirname, 'see-color-pairs.js'), 'utf8');
const seeDomScript        = readFileSync(join(__dirname, 'see-dom.js'),         'utf8');

const CHROMIUM_PATH = process.env.CHROMIUM_PATH || '/usr/bin/chromium';
const PAGES_DIR = process.env.PAGES_DIR || '/pages';
const HOST_PAGES_DIR = process.env.HOST_PAGES_DIR || '';
const PORT = parseInt(process.env.PORT || '3000');

const app = express();

// Active transports keyed by sessionId (SSE and Streamable HTTP share the same map)
const transports = new Map();
// SSE transports need separate tracking for the /messages POST route
const sseTransports = new Map();

// Active browser sessions keyed by sessionId
// Each session: { browser, page, snapshots: Map<name, { html, lens, timestamp, chars }> }
const browserSessions = new Map();

function resolveTarget(target) {
  if (/^https?:\/\//i.test(target)) return target;
  if (/^file:\/\//i.test(target)) {
    if (HOST_PAGES_DIR) {
      const hostPrefix = `file://${HOST_PAGES_DIR}`;
      if (target.startsWith(hostPrefix)) {
        return `file://${join(PAGES_DIR, target.slice(hostPrefix.length))}`;
      }
    }
    return target;
  }
  if (isAbsolute(target)) {
    if (HOST_PAGES_DIR && target.startsWith(HOST_PAGES_DIR)) {
      return `file://${join(PAGES_DIR, target.slice(HOST_PAGES_DIR.length))}`;
    }
    return `file://${target}`;
  }
  return `file://${join(PAGES_DIR, target)}`;
}

function lineDiff(before, after) {
  const setA = new Set(before.split('\n'));
  const setB = new Set(after.split('\n'));
  const removed = before.split('\n').filter(l => !setB.has(l)).map(l => `- ${l}`);
  const added = after.split('\n').filter(l => !setA.has(l)).map(l => `+ ${l}`);
  return [...removed, ...added].join('\n') || '(no diff)';
}

// Serialize the live DOM as compact HTML (used by browser_hover).
async function serializeDOM(page) {
  return page.evaluate(() => {
    const DEFAULT_ATTRS = {
      INPUT: { type: 'text' },
      FORM: { method: 'get' },
      SCRIPT: { type: 'text/javascript' },
      LINK: { media: 'all' },
    };
    const VOID = new Set(['area','base','br','col','embed','hr','img','input','link','meta','param','source','track','wbr']);

    function serializeNode(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        return node.textContent.replace(/\s+/g, ' ').trim();
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return '';

      const tag = node.tagName.toLowerCase();
      const skipContent = tag === 'script' || tag === 'style';
      const defaults = DEFAULT_ATTRS[node.tagName] || {};

      const attrs = Array.from(node.attributes)
        .filter(a => !(a.name in defaults && defaults[a.name] === a.value))
        .map(a => a.value === '' ? a.name : `${a.name}="${a.value}"`)
        .join(' ');

      const openTag = attrs ? `<${tag} ${attrs}>` : `<${tag}>`;
      if (VOID.has(tag)) return openTag;
      if (skipContent) return `${openTag}</${tag}>`;

      const children = Array.from(node.childNodes).map(serializeNode).filter(Boolean).join('');
      return `${openTag}${children}</${tag}>`;
    }

    return serializeNode(document.documentElement);
  });
}

// Run see-dom.js with given params and return the result string.
// Output has newlines between tags so line-level diffs (snapshots) are meaningful.
async function captureDom(page, { lens = null, custom = null, maxChars = null, exclude = null } = {}) {
  if (lens && lens.includes('none')) return '';
  const script = seeDomScript
    .replaceAll('__LENS__',       JSON.stringify(lens))
    .replaceAll('__CUSTOM__',     JSON.stringify(custom))
    .replaceAll('__JUST_COUNT__', 'false')
    .replaceAll('__MAX_CHARS__',  JSON.stringify(maxChars))
    .replaceAll('__EXCLUDE__',    JSON.stringify(exclude));
  const result = await page.evaluate(script);
  const html = typeof result === 'string' ? result : JSON.stringify(result, null, 2);
  // Put each opening tag on its own line for meaningful line-level diffs.
  // This ensures orphaned text (e.g. from <title>) doesn't get merged with the next tag.
  return html.replace(/<(?!\/)/g, '\n<').trim();
}

async function readPageText(page) {
  return page.evaluate(() => {
    const HEADING_MAP = { H1: '#', H2: '##', H3: '###', H4: '####', H5: '#####', H6: '######' };
    const lines = [];

    function walkText(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(text);
        return;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) return;
      const tag = node.tagName;
      if (tag === 'SCRIPT' || tag === 'STYLE') return;

      const idSuffix = node.id ? ` {#${node.id}}` : '';

      if (HEADING_MAP[tag]) {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(`\n${HEADING_MAP[tag]} ${text}${idSuffix}\n`);
        return;
      }
      if (tag === 'P') {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(`\n${text}${idSuffix}\n`);
        return;
      }
      if (tag === 'LI') {
        const text = node.textContent.replace(/\s+/g, ' ').trim();
        if (text) lines.push(`- ${text}${idSuffix}`);
        return;
      }
      Array.from(node.childNodes).forEach(walkText);
    }

    walkText(document.body);

    // Generate a minimal unique CSS selector for an element.
    function getSelector(el) {
      if (el.id) return '#' + el.id;
      const parts = [];
      let node = el;
      while (node && node.tagName && node !== document.body) {
        if (node.id) { parts.unshift('#' + node.id); break; }
        let part = node.tagName.toLowerCase();
        const same = Array.from(node.parentElement?.children ?? []).filter(c => c.tagName === node.tagName);
        if (same.length > 1) part += ':nth-of-type(' + (same.indexOf(node) + 1) + ')';
        parts.unshift(part);
        node = node.parentElement;
      }
      return parts.join(' > ');
    }

    const interactive = [];
    document.querySelectorAll('a[href], button, input, select, textarea, [onclick], [onmouseover]').forEach(el => {
      const label = el.getAttribute('aria-label') || el.getAttribute('placeholder') ||
        el.getAttribute('title') || el.textContent.replace(/\s+/g, ' ').trim().slice(0, 60) || '';
      interactive.push({ id: el.id || null, selector: getSelector(el), tag: el.tagName.toLowerCase(), label });
    });

    let result = lines.join('\n');
    if (interactive.length) {
      result += '\n\n## Interactive Elements\n';
      interactive.forEach(({ id, selector, tag, label }) => {
        const ref = id ? '#' + id : selector;
        result += `\n- [${tag}] ${ref}${label ? ': ' + label : ''}`;
      });
    }
    return result;
  });
}

// Search page innerText for terms and return surrounding context lines.
async function searchPageText(page, terms, context = 3) {
  const text = await page.evaluate(() => document.body.innerText);
  const lines = text.split('\n');
  const results = [];
  const covered = []; // [start, end] ranges already included

  for (const term of terms) {
    const lower = term.toLowerCase();
    for (let i = 0; i < lines.length; i++) {
      if (!lines[i].toLowerCase().includes(lower)) continue;
      const start = Math.max(0, i - context);
      const end = Math.min(lines.length - 1, i + context);
      if (covered.some(([s, e]) => start <= e && end >= s)) continue;
      covered.push([start, end]);
      const chunk = lines.slice(start, end + 1).map((l, idx) => {
        const lineNo = start + idx;
        return (lineNo === i ? '>>> ' : '    ') + l;
      }).join('\n');
      results.push({ term, context: chunk });
    }
  }
  return results;
}

async function launchBrowser(sessionId, url) {
  const existing = browserSessions.get(sessionId);
  if (existing) {
    await existing.browser.close().catch(() => {});
    browserSessions.delete(sessionId);
  }
  const browser = await puppeteer.launch({
    executablePath: CHROMIUM_PATH,
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage', '--disable-gpu', '--allow-file-access-from-files'],
    headless: true,
  });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 720 });

  const resolved = resolveTarget(url);
  let finalUrl = resolved;
  try {
    await page.goto(resolved, { waitUntil: 'networkidle0', timeout: 30000 });
  } catch (err) {
    const isConnRefused = err.message && (
      err.message.includes('ERR_CONNECTION_REFUSED') ||
      err.message.includes('ECONNREFUSED')
    );
    const isLocalhost = /^https?:\/\/localhost[:/]/i.test(resolved);
    if (isConnRefused && isLocalhost) {
      finalUrl = resolved.replace(/^(https?:\/\/)localhost([:/])/i, '$1host.docker.internal$2');
      await page.goto(finalUrl, { waitUntil: 'networkidle0', timeout: 30000 });
    } else {
      throw err;
    }
  }

  const consoleLogs = [];
  page.on('console', msg => consoleLogs.push(msg));
  browserSessions.set(sessionId, { browser, page, snapshots: new Map(), consoleLogs });
  const title = await page.title();
  const note = finalUrl !== resolved ? ` (localhost unreachable; use host.docker.internal)` : '';
  return `Launched: ${title}${note}`;
}

function drainLogs(session) {
  if (!session?.consoleLogs?.length) return '';
  const out = session.consoleLogs.map(m => `[console.${m.type()}] ${m.text()}`).join('\n');
  session.consoleLogs = [];
  return '\n\n---\nconsole:\n' + out;
}

function resolveSelector(args) {
  const { id, selector } = args;
  if (id) return `#${id}`;
  if (selector) return selector;
  throw new Error('Provide id or selector.');
}

// Navigate page to a URL, handling the localhost→host.docker.internal fallback.
// Returns the final URL navigated to.
async function gotoPage(page, url) {
  const resolved = resolveTarget(url);
  try {
    await page.goto(resolved, { waitUntil: 'networkidle0', timeout: 30000 });
    return resolved;
  } catch (err) {
    const isConnRefused = err.message && (
      err.message.includes('ERR_CONNECTION_REFUSED') ||
      err.message.includes('ECONNREFUSED')
    );
    const isLocalhost = /^https?:\/\/localhost[:/]/i.test(resolved);
    if (isConnRefused && isLocalhost) {
      const fallback = resolved.replace(/^(https?:\/\/)localhost([:/])/i, '$1host.docker.internal$2');
      await page.goto(fallback, { waitUntil: 'networkidle0', timeout: 30000 });
      return fallback;
    }
    throw err;
  }
}

// Given a base URL and a list of candidate URLs, return the candidate on the same
// host with the most similar path (longest common path prefix, penalising depth diff).
// Supports both http(s):// and file:// URLs.
function findMostSimilarUrl(baseUrl, candidates) {
  let base;
  try { base = new URL(baseUrl); } catch { return null; }
  if (!/^(https?|file):$/.test(base.protocol)) return null;
  const baseParts = base.pathname.split('/').filter(Boolean);

  let best = null, bestScore = -Infinity;
  for (const url of candidates) {
    let u;
    try { u = new URL(url); } catch { continue; }
    if (u.protocol !== base.protocol) continue;
    if (u.host !== base.host) continue;
    if (u.pathname === base.pathname) continue;   // skip same-path (hash variants)

    const parts = u.pathname.split('/').filter(Boolean);
    let common = 0;
    for (let i = 0; i < Math.min(baseParts.length, parts.length); i++) {
      if (baseParts[i] === parts[i]) common++; else break;
    }
    const score = common * 10 - Math.abs(parts.length - baseParts.length);
    if (score > bestScore) { bestScore = score; best = url; }
  }
  return best;
}

// Compute line-level intersection of two HTML strings (the "background").
function lineIntersection(htmlA, htmlB) {
  const setB = new Set(htmlB.split('\n').filter(l => l.trim()));
  return htmlA.split('\n').filter(l => l.trim() && setB.has(l)).join('\n');
}

// Remove lines in subtractHtml from html.
function subtractLines(html, subtractHtml) {
  const bg = new Set(subtractHtml.split('\n').filter(l => l.trim()));
  return html.split('\n').filter(l => !bg.has(l)).join('\n');
}

function createMcpServer(sessionId) {
  const server = new Server(
    { name: 'smth', version: '1.0.0' },
    { capabilities: { tools: {} } }
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: [
      {
        name: 'browser_list_devices',
        description: 'List all available device names that can be passed to the browser_see_visual "device" parameter.',
        annotations: { readOnlyHint: true },
        inputSchema: { type: 'object', properties: {} },
      },
      {
        name: 'browser_launch',
        description: 'Launch a new browser session and open a URL. Always call this before any other browser_ tool. Replaces any existing session.',
        annotations: { readOnlyHint: false, destructiveHint: true },
        inputSchema: {
          type: 'object',
          properties: {
            url: { type: 'string', description: 'URL or file path to navigate to.' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
          required: ['url'],
        },
      },
      {
        name: 'browser_goto',
        description: 'Navigate the existing browser session to a new URL without relaunching the browser.',
        annotations: { readOnlyHint: false },
        inputSchema: {
          type: 'object',
          properties: {
            url: { type: 'string', description: 'URL or file path to navigate to.' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
          required: ['url'],
        },
      },
      {
        name: 'browser_read_text',
        description: 'Return the visible text of the current page as Markdown, plus a list of interactive elements each annotated with a CSS selector usable with browser_click, browser_hover, and browser_type.',
        annotations: { readOnlyHint: true },
        inputSchema: { type: 'object', properties: { getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' } } },
      },
      {
        name: 'browser_see_fonts',
        description: 'Return a JSON report of all fonts in use on the current page, grouped by family with size, weight, and usage count.',
        annotations: { readOnlyHint: true },
        inputSchema: { type: 'object', properties: { getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' } } },
      },
      {
        name: 'browser_see_colors',
        description: 'Return a JSON report of all computed colors in use on the current page — text, background, and border — with usage counts per color.',
        annotations: { readOnlyHint: true },
        inputSchema: {
          type: 'object',
          properties: {
            only: {
              type: 'array',
              items: { type: 'string', enum: ['text', 'background', 'border'] },
              description: 'Restrict results to these usage categories. Omit to include all three.',
            },
            colors: {
              type: 'array',
              items: { type: 'string' },
              description: 'Filter to specific colors given as hex strings (e.g. "#ff0000", "#abc"). Only entries matching these colors are returned.',
            },
            where: {
              type: 'boolean',
              description: 'When true, each color entry gains a "where" key listing the element selectors (#id, .class, or tag) where that color is used, grouped by category.',
            },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
        },
      },
      {
        name: 'browser_see_color_pairs',
        description: 'For every visible text element, find its text color and effective background color (walking up the DOM), compute the WCAG 2.2 contrast ratio, and return all pairs with AA/AAA pass/fail flags sorted by usage count.',
        annotations: { readOnlyHint: true },
        inputSchema: { type: 'object', properties: { getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' } } },
      },
      {
        name: 'browser_see_dom',
        description: 'Return a filtered view of the live DOM as compact HTML. Pass "search" to instead find text matches with surrounding context lines.',
        annotations: { readOnlyHint: true },
        inputSchema: {
          type: 'object',
          properties: {
            lens: {
              type: 'array',
              items: { type: 'string', enum: ['text', 'media', 'layout', 'code', 'css-classes', 'none'] },
              description: 'Active lenses. Omit for the full DOM. One or more of: "text" (visible text, labels, values), "media" (images, SVG, canvas), "layout" (structure, positioning), "code" (event handlers, data-* attributes, JS hooks), "css-classes" (returns [{class, count}] sorted by frequency instead of HTML), "none" (returns empty DOM — useful when only console logs are needed).',
            },
            search: {
              type: 'array',
              items: { type: 'string' },
              description: 'Search terms. When provided, returns matching lines from page text with 3 lines of context above and below each match instead of serialized HTML.',
            },
            custom: {
              type: 'object',
              description: 'Extra classifications merged (union) with the built-ins. Shape: { elements?: { tagName: category[] }, attributes?: { global?: { attrName: category[] }, by_element?: { tagName: { attrName: category[] } } } }',
            },
            justCount: {
              type: 'boolean',
              description: 'If true, return only counts: { chars, elements, attributes }. Useful for estimating token cost before fetching.',
            },
            max_chars: {
              type: 'number',
              description: 'Character budget. Deepens the DOM one level at a time until the output would exceed this limit.',
            },
            exclude: {
              type: 'string',
              description: 'Comma-separated CSS selectors. Matching elements and their entire subtrees are removed before lens filtering. Example: ".sidebar, #cookie-banner, script"',
            },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
        },
      },
      {
        name: 'browser_click',
        description: 'Click an element on the current page. Waits for any navigation triggered by the click to complete before returning.',
        annotations: { readOnlyHint: false, destructiveHint: true },
        inputSchema: {
          type: 'object',
          properties: {
            id:       { type: 'string', description: 'HTML element id (without #). Preferred when available.' },
            selector: { type: 'string', description: 'CSS selector. Used when the element has no id.' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
        },
      },
      {
        name: 'browser_hover',
        description: 'Hover over an element and return a before/after diff of visible text and interactive attributes (text+code lens).',
        annotations: { readOnlyHint: false },
        inputSchema: {
          type: 'object',
          properties: {
            id:       { type: 'string', description: 'HTML element id (without #). Preferred when available.' },
            selector: { type: 'string', description: 'CSS selector. Used when the element has no id.' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
        },
      },
      {
        name: 'browser_type',
        description: 'Type text into a form field. Clears the field first by default.',
        annotations: { readOnlyHint: false, destructiveHint: true },
        inputSchema: {
          type: 'object',
          properties: {
            id:       { type: 'string', description: 'HTML element id (without #). Preferred when available.' },
            selector: { type: 'string', description: 'CSS selector. Used when the element has no id.' },
            text:     { type: 'string', description: 'Text to type into the element.' },
            clear:    { type: 'boolean', description: 'Clear the field before typing (default: true).' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
          required: ['text'],
        },
      },
      {
        name: 'browser_remember_dom',
        description: 'Capture a named snapshot of the current DOM for later comparison with browser_dom_compare. Use the same lens params for both snapshots when planning to compare.',
        annotations: { readOnlyHint: false },
        inputSchema: {
          type: 'object',
          properties: {
            name:      { type: 'string', description: 'Name for this snapshot.' },
            lens:      { type: 'array', items: { type: 'string', enum: ['text', 'media', 'layout', 'code', 'none'] }, description: 'Lenses to apply at capture time. Omit for the full DOM. Use "none" to capture an empty snapshot (useful when only console logs are needed).' },
            exclude:   { type: 'string', description: 'Comma-separated CSS selectors to exclude before capturing.' },
            max_chars: { type: 'number', description: 'Character budget (same as browser_see_dom).' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
          required: ['name'],
        },
      },
      {
        name: 'browser_doms',
        description: 'List all named DOM snapshots stored in this session.',
        annotations: { readOnlyHint: true },
        inputSchema: { type: 'object', properties: {} },
      },
      {
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
      {
        name: 'fetch_dom_content',
        description: 'All-in-one: open a URL, auto-detect background by visiting a similar page on the same site, and return the unique (foreground) DOM content of the original page. Also stores named snapshots: "<prefix>_page", "<prefix>_peer", "<prefix>_background" for further inspection.',
        annotations: { readOnlyHint: false },
        inputSchema: {
          type: 'object',
          properties: {
            url:    { type: 'string', description: 'URL to fetch content from.' },
            lens:   { type: 'array', items: { type: 'string', enum: ['text', 'media', 'layout', 'code', 'none'] }, description: 'Lenses for DOM capture (default: ["text", "layout"]). Use "none" to skip DOM output (useful when only console logs are needed).' },
            prefix: { type: 'string', description: 'Prefix for stored snapshot names (default: "fetch").' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
          required: ['url'],
        },
      },
      {
        name: 'browser_see_visual',
        description: 'Screenshot the current page and return the image as embedded content visible to the model.',
        annotations: { readOnlyHint: true },
        inputSchema: {
          type: 'object',
          properties: {
            width:      { type: 'number', description: 'Viewport width in pixels (default: current viewport).' },
            height:     { type: 'number', description: 'Viewport height in pixels (default: current viewport).' },
            full_page:  { type: 'boolean', description: 'Capture full scrollable page (default: true, or false when element_id is set).' },
            zoom:       { type: 'number', description: 'CSS zoom factor. 1 = no zoom, 1.5 = 150%, 2 = 200% etc. (default: 1).' },
            element_id: { type: 'string', description: 'Scroll to and center this element id before capturing. Implies full_page: false.' },
            device:     { type: 'string', description: 'Emulate a named device (sets viewport, pixel ratio, user-agent). Overrides width/height. Call browser_list_devices to see options.' },
            getConsoleLogs: { type: 'boolean', description: 'If true, append buffered browser console output to the response.' },
          },
        },
      },
    ],
  }));

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const tool = request.params.name;
    const args = request.params.arguments ?? {};

    if (tool === 'browser_list_devices') {
      return { content: [{ type: 'text', text: Object.keys(KnownDevices).sort().join('\n') }] };
    }

    if (tool === 'browser_launch' || tool === 'browser_open') {
      const msg = await launchBrowser(sessionId, args.url);
      const logs = args.getConsoleLogs ? drainLogs(browserSessions.get(sessionId)) : '';
      return { content: [{ type: 'text', text: msg + logs }] };
    }

    if (tool === 'fetch_dom_content') {
      const { url, lens = ['text', 'layout'], prefix = 'fetch' } = args;
      const resolvedUrl = resolveTarget(url);

      // 1. Launch fresh session and load the target URL.
      await launchBrowser(sessionId, url);
      // Re-read session after launch (launchBrowser replaces it).
      const freshSession = browserSessions.get(sessionId);
      const freshPage = freshSession.page;
      const freshSnapshots = freshSession.snapshots;

      // 2. Capture DOM of page A.
      const htmlA = await captureDom(freshPage, { lens });
      freshSnapshots.set(`${prefix}_page`, { html: htmlA, lens, timestamp: new Date().toISOString(), chars: htmlA.length });

      // 3. Find the most similar same-site link.
      const allLinks = await freshPage.evaluate(() =>
        Array.from(document.querySelectorAll('a[href]')).map(a => a.href)
      );
      const peerUrl = findMostSimilarUrl(resolvedUrl, allLinks);
      if (!peerUrl) {
        const logs = args.getConsoleLogs ? drainLogs(freshSession) : '';
        return { content: [{ type: 'text', text: `No same-site peer link found. Returning full DOM.\n\n${htmlA}` + logs }] };
      }

      // 4. Navigate to peer and capture DOM of page B.
      await gotoPage(freshPage, peerUrl);
      const htmlB = await captureDom(freshPage, { lens });
      freshSnapshots.set(`${prefix}_peer`, { html: htmlB, lens, timestamp: new Date().toISOString(), chars: htmlB.length });

      // 5. Compute background (lines common to both pages).
      const bgHtml = lineIntersection(htmlA, htmlB);
      freshSnapshots.set(`${prefix}_background`, { html: bgHtml, lens, timestamp: new Date().toISOString(), chars: bgHtml.length });

      // 6. Navigate back to the original URL.
      await gotoPage(freshPage, url);

      // 7. Return page A content minus background.
      const content = subtractLines(htmlA, bgHtml);
      const bgLines = bgHtml.split('\n').filter(l => l.trim()).length;
      const contentLines = content.split('\n').filter(l => l.trim()).length;
      const header = `# fetch_dom_content: ${url}\n# peer: ${peerUrl}\n# background: ${bgLines} lines stripped, ${contentLines} lines remain\n# snapshots: ${prefix}_page, ${prefix}_peer, ${prefix}_background\n\n`;
      const logs = args.getConsoleLogs ? drainLogs(freshSession) : '';
      return { content: [{ type: 'text', text: header + (content || '(no content after background subtraction)') + logs }] };
    }

    const session = browserSessions.get(sessionId);
    if (!session) throw new Error('No open browser session. Call browser_launch first.');
    const { page, snapshots } = session;

    if (tool === 'browser_goto') {
      const finalUrl = await gotoPage(page, args.url);
      const title = await page.title();
      const note = finalUrl !== resolveTarget(args.url) ? ` (localhost unreachable; use host.docker.internal)` : '';
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: `Navigated to: ${title}${note}` + logs }] };
    }

    if (tool === 'browser_read_text') {
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: await readPageText(page) + logs }] };
    }

    if (tool === 'browser_see_fonts') {
      const result = await page.evaluate(seeFontsScript);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) + logs }] };
    }

    if (tool === 'browser_see_colors') {
      const script = seeColorsScript
        .replaceAll('__ONLY__',   JSON.stringify(args.only   ?? null))
        .replaceAll('__COLORS__', JSON.stringify(args.colors ?? null))
        .replaceAll('__WHERE__',  JSON.stringify(args.where  ?? false));
      const result = await page.evaluate(script);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) + logs }] };
    }

    if (tool === 'browser_see_color_pairs') {
      const result = await page.evaluate(seeColorPairsScript);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: JSON.stringify(result, null, 2) + logs }] };
    }

    if (tool === 'browser_see_dom') {
      if (args.search) {
        const terms = Array.isArray(args.search) ? args.search : [args.search];
        const results = await searchPageText(page, terms);
        const logs = args.getConsoleLogs ? drainLogs(session) : '';
        if (!results.length) return { content: [{ type: 'text', text: '(no matches)' + logs }] };
        const out = results.map(r => `[${r.term}]\n${r.context}`).join('\n\n---\n\n');
        return { content: [{ type: 'text', text: out + logs }] };
      }

      const script = seeDomScript
        .replaceAll('__LENS__',       JSON.stringify(args.lens      ?? null))
        .replaceAll('__CUSTOM__',     JSON.stringify(args.custom    ?? null))
        .replaceAll('__JUST_COUNT__', JSON.stringify(args.justCount ?? false))
        .replaceAll('__MAX_CHARS__',  JSON.stringify(args.max_chars ?? null))
        .replaceAll('__EXCLUDE__',    JSON.stringify(args.exclude   ?? null));
      const result = await page.evaluate(script);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: (typeof result === 'string' ? result : JSON.stringify(result, null, 2)) + logs }] };
    }

    if (tool === 'browser_click') {
      const sel = resolveSelector(args);
      const exists = await page.evaluate(s => !!document.querySelector(s), sel);
      if (!exists) throw new Error(`Element not found: ${sel}`);
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => null),
        page.click(sel),
      ]);
      const title = await page.title();
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: `Clicked ${sel}. Page: ${title}` + logs }] };
    }

    if (tool === 'browser_hover') {
      const sel = resolveSelector(args);
      const exists = await page.evaluate(s => !!document.querySelector(s), sel);
      if (!exists) throw new Error(`Element not found: ${sel}`);
      // Move to (0, 0) first so any CSS hover effects from the previous action are cleared,
      // giving a consistent "before" baseline regardless of prior mouse position.
      await page.mouse.move(0, 0);
      await new Promise(r => setTimeout(r, 100));
      // Use innerText (visibility-aware) so CSS-toggled content (e.g. tooltips) shows in the diff.
      const before = await page.evaluate(() => document.body.innerText);
      await page.hover(sel);
      await new Promise(r => setTimeout(r, 300));
      const after = await page.evaluate(() => document.body.innerText);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: lineDiff(before, after) + logs }] };
    }

    if (tool === 'browser_type') {
      const sel = resolveSelector(args);
      const exists = await page.evaluate(s => !!document.querySelector(s), sel);
      if (!exists) throw new Error(`Element not found: ${sel}`);
      const clear = args.clear ?? true;
      if (clear) {
        await page.evaluate((s) => {
          const el = document.querySelector(s);
          if (el) el.value = '';
        }, sel);
      }
      await page.focus(sel);
      await page.type(sel, args.text);
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: `Typed into ${sel}` + logs }] };
    }

    if (tool === 'browser_remember_dom') {
      const { name, lens = null, exclude = null, max_chars = null } = args;
      const html = await captureDom(page, { lens, exclude, maxChars: max_chars });
      snapshots.set(name, { html, lens, timestamp: new Date().toISOString(), chars: html.length });
      const logs = args.getConsoleLogs ? drainLogs(session) : '';
      return { content: [{ type: 'text', text: `Snapshot "${name}" saved (${html.length} chars, lens: ${lens ? lens.join('+') : 'full'})` + logs }] };
    }

    if (tool === 'browser_doms') {
      if (!snapshots.size) return { content: [{ type: 'text', text: '(no snapshots)' }] };
      const lines = [...snapshots.entries()].map(([name, s]) =>
        `${name}  ${s.chars} chars  lens:${s.lens ? s.lens.join('+') : 'full'}  ${s.timestamp}`
      );
      return { content: [{ type: 'text', text: lines.join('\n') }] };
    }

    if (tool === 'browser_dom_compare') {
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
    }

    if (tool === 'browser_see_visual') {
      const { zoom = 1, device, width, height, element_id } = args;
      const full_page = args.full_page ?? (element_id ? false : true);

      if (device) {
        if (!KnownDevices[device]) throw new Error(`Unknown device "${device}". Call browser_list_devices to see available options.`);
        await page.emulate(KnownDevices[device]);
      } else if (width || height) {
        const vp = page.viewport();
        await page.setViewport({ width: width || vp.width, height: height || vp.height });
      }

      if (zoom !== 1) {
        await page.evaluate((z) => { document.documentElement.style.zoom = `${z * 100}%`; }, zoom);
      }

      if (element_id) {
        const found = await page.evaluate((elId) => {
          const el = document.getElementById(elId);
          if (!el) return false;
          el.scrollIntoView({ behavior: 'instant', block: 'center', inline: 'center' });
          return true;
        }, element_id);
        if (!found) throw new Error(`Element #${element_id} not found on page`);
        await new Promise(r => setTimeout(r, 150));
      }

      const buffer = await page.screenshot({ fullPage: full_page });

      if (zoom !== 1) {
        await page.evaluate(() => { document.documentElement.style.zoom = ''; });
      }

      const content = [{ type: 'image', data: buffer.toString('base64'), mimeType: 'image/png' }];
      if (args.getConsoleLogs) {
        const logs = drainLogs(session);
        if (logs) content.push({ type: 'text', text: logs.trimStart() });
      }
      return { content };
    }

    throw new Error(`Unknown tool: ${tool}`);
  });

  return server;
}

// Streamable HTTP endpoint — handles all MCP traffic (POST, GET, DELETE)
app.all('/mcp', express.json(), async (req, res) => {
  if (req.method === 'POST') {
    const sessionId = req.headers['mcp-session-id'];

    if (sessionId) {
      // Route to existing session
      const transport = transports.get(sessionId);
      if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
      await transport.handleRequest(req, res, req.body);
      return;
    }

    // New session — generate ID upfront so createMcpServer can capture it
    const newSessionId = randomUUID();
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: () => newSessionId,
    });

    transport.onclose = async () => {
      transports.delete(newSessionId);
      const bs = browserSessions.get(newSessionId);
      if (bs) {
        await bs.browser.close().catch(() => {});
        browserSessions.delete(newSessionId);
      }
    };

    const server = createMcpServer(newSessionId);
    transports.set(newSessionId, transport);
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
    return;
  }

  if (req.method === 'GET' || req.method === 'DELETE') {
    const sessionId = req.headers['mcp-session-id'];
    const transport = transports.get(sessionId);
    if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
    await transport.handleRequest(req, res);
    return;
  }

  res.status(405).json({ error: 'Method not allowed' });
});

// Legacy SSE endpoint — for clients that don't support Streamable HTTP (e.g. Claude Code)
app.get('/sse', async (req, res) => {
  const transport = new SSEServerTransport('/messages', res);
  const server = createMcpServer(transport.sessionId);
  sseTransports.set(transport.sessionId, transport);

  res.on('close', async () => {
    sseTransports.delete(transport.sessionId);
    const session = browserSessions.get(transport.sessionId);
    if (session) {
      await session.browser.close().catch(() => {});
      browserSessions.delete(transport.sessionId);
    }
  });

  await server.connect(transport);
});

app.post('/messages', express.json(), async (req, res) => {
  const transport = sseTransports.get(req.query.sessionId);
  if (!transport) { res.status(404).json({ error: 'Session not found' }); return; }
  await transport.handlePostMessage(req, res, req.body);
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', service: 'smth', sessions: transports.size + sseTransports.size });
});

app.listen(PORT, () => {
  console.log(`smth ready on port ${PORT}`);
  console.log(`  Streamable HTTP: http://localhost:${PORT}/mcp`);
  console.log(`  SSE (legacy):    http://localhost:${PORT}/sse`);
  console.log(`  Health check:    http://localhost:${PORT}/health`);
  console.log(`  Pages dir:       ${PAGES_DIR}`);
});
