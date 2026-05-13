// Unit tests for src/cli/args.js — no subprocess, no server.
//
// Run from repo root with:  node --test test-cli/parseArgs.test.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { parseArgs, formatToolHelp } from '../src/cli/args.js';

const TOOL_SCHEMA = {
  type: 'object',
  properties: {
    url:        { type: 'string',  description: 'Target URL.' },
    timeout_ms: { type: 'number',  description: 'Timeout in ms.' },
    include:    { type: 'array',   items: { type: 'string' } },
    getConsoleLogs: { type: 'boolean' },
  },
  required: ['url'],
};

test('parseArgs: --key=value form', () => {
  assert.deepEqual(
    parseArgs(['--url=https://example.com'], TOOL_SCHEMA),
    { url: 'https://example.com' },
  );
});

test('parseArgs: --key value form', () => {
  assert.deepEqual(
    parseArgs(['--url', 'https://example.com'], TOOL_SCHEMA),
    { url: 'https://example.com' },
  );
});

test('parseArgs: coerces number type', () => {
  assert.deepEqual(
    parseArgs(['--timeout_ms=15000'], TOOL_SCHEMA),
    { timeout_ms: 15000 },
  );
});

test('parseArgs: rejects non-numeric for number type', () => {
  assert.throws(
    () => parseArgs(['--timeout_ms=abc'], TOOL_SCHEMA),
    /expects a number/,
  );
});

test('parseArgs: bare boolean flag is true', () => {
  assert.deepEqual(
    parseArgs(['--getConsoleLogs'], TOOL_SCHEMA),
    { getConsoleLogs: true },
  );
});

test('parseArgs: --no- prefix sets boolean false', () => {
  assert.deepEqual(
    parseArgs(['--no-getConsoleLogs'], TOOL_SCHEMA),
    { getConsoleLogs: false },
  );
});

test('parseArgs: explicit boolean values', () => {
  assert.deepEqual(parseArgs(['--getConsoleLogs=true'], TOOL_SCHEMA), { getConsoleLogs: true });
  assert.deepEqual(parseArgs(['--getConsoleLogs=false'], TOOL_SCHEMA), { getConsoleLogs: false });
  assert.deepEqual(parseArgs(['--getConsoleLogs=1'], TOOL_SCHEMA), { getConsoleLogs: true });
});

test('parseArgs: array via repeated flag', () => {
  assert.deepEqual(
    parseArgs(['--include', 'error', '--include', 'warning'], TOOL_SCHEMA),
    { include: ['error', 'warning'] },
  );
});

test('parseArgs: array via comma split', () => {
  assert.deepEqual(
    parseArgs(['--include=error,warning,pageerror'], TOOL_SCHEMA),
    { include: ['error', 'warning', 'pageerror'] },
  );
});

test('parseArgs: array combines repeat + comma', () => {
  assert.deepEqual(
    parseArgs(['--include=error,warning', '--include=pageerror'], TOOL_SCHEMA),
    { include: ['error', 'warning', 'pageerror'] },
  );
});

test('parseArgs: positional argument rejected', () => {
  assert.throws(
    () => parseArgs(['posarg'], TOOL_SCHEMA),
    /Positional arguments are not supported/,
  );
});

test('parseArgs: missing value for non-= flag', () => {
  assert.throws(
    () => parseArgs(['--url'], TOOL_SCHEMA),
    /Missing value for --url/,
  );
});

test('parseArgs: unknown key with string default (passes through)', () => {
  // We don't validate against the schema strictly — unknown keys default to string.
  // This is intentional so tools can accept extra args without the CLI gatekeeping.
  assert.deepEqual(
    parseArgs(['--unknown=hi'], TOOL_SCHEMA),
    { unknown: 'hi' },
  );
});

test('parseArgs: empty argv yields empty object', () => {
  assert.deepEqual(parseArgs([], TOOL_SCHEMA), {});
});

test('formatToolHelp: includes name, description, arguments, required marker', () => {
  const help = formatToolHelp({
    name: 'demo',
    description: 'A demo tool.',
    inputSchema: TOOL_SCHEMA,
  });
  assert.match(help, /^demo/);
  assert.match(help, /A demo tool\./);
  assert.match(help, /--url\s+string\s+\(required\)/);
  assert.match(help, /--timeout_ms\s+number\s+\(optional\)/);
  assert.match(help, /Target URL\./);
});

test('formatToolHelp: tool with no arguments still prints name + description', () => {
  const help = formatToolHelp({
    name: 'noargs',
    description: 'No args here.',
    inputSchema: { type: 'object', properties: {} },
  });
  assert.match(help, /^noargs/);
  assert.match(help, /No args here\./);
  assert.doesNotMatch(help, /Arguments:/);
});
