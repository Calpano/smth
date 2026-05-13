// Top-level CLI dispatcher.

import { up, down, probe, ensureUp, DEFAULT_URL } from './container.js';
import { callTool, listTools, clearSessionId, currentSessionId } from './client.js';
import { parseArgs, formatToolHelp } from './args.js';
import { renderResult } from './render.js';

const USAGE = `smth — CLI for the smth MCP browser stack

Usage:
  smth <tool> [--key=value ...]     Call a tool (auto-starts container if down)
  smth list                         List available tools
  smth help <tool>                  Show a tool's arguments
  smth up                           Start the docker container
  smth down                         Stop the docker container
  smth status                       Probe /health
  smth session reset                Forget the saved session id (next call gets a fresh browser)

Environment:
  SMTH_URL                          Override the server URL (default: ${DEFAULT_URL})`;

export async function main(argv) {
  const [cmd, ...rest] = argv;
  if (!cmd || cmd === '--help' || cmd === '-h' || cmd === 'help' && rest.length === 0) {
    process.stdout.write(USAGE + '\n');
    return;
  }

  switch (cmd) {
    case 'up':      return up();
    case 'down':    return down();
    case 'status':  return showStatus();
    case 'list':    return showList();
    case 'help':    return showToolHelp(rest[0]);
    case 'session': return sessionCmd(rest);
    default:        return runTool(cmd, rest);
  }
}

async function showStatus() {
  const body = await probe();
  if (!body) {
    process.stdout.write(`smth is DOWN at ${DEFAULT_URL}\n`);
    process.exitCode = 1;
    return;
  }
  process.stdout.write(`smth is UP at ${DEFAULT_URL}\n`);
  process.stdout.write(JSON.stringify(body, null, 2) + '\n');
  const sid = currentSessionId();
  process.stdout.write(`Local session id: ${sid ?? '(none)'}\n`);
}

async function showList() {
  await ensureUp();
  const tools = await listTools();
  for (const t of tools) {
    process.stdout.write(`  ${t.name.padEnd(28)} ${t.description?.split('\n')[0] ?? ''}\n`);
  }
}

async function showToolHelp(name) {
  if (!name) throw new Error('Usage: smth help <tool>');
  await ensureUp();
  const tools = await listTools();
  const tool = tools.find(t => t.name === name);
  if (!tool) throw new Error(`Unknown tool: ${name}. Run 'smth list' to see available tools.`);
  process.stdout.write(formatToolHelp(tool) + '\n');
}

async function sessionCmd(args) {
  const [sub] = args;
  if (sub === 'reset') {
    clearSessionId();
    process.stdout.write('Cleared saved session id.\n');
    return;
  }
  if (sub === undefined || sub === 'show') {
    process.stdout.write(`Saved session id: ${currentSessionId() ?? '(none)'}\n`);
    return;
  }
  throw new Error(`Unknown session subcommand: ${sub}. Use 'reset' or 'show'.`);
}

async function runTool(name, restArgv) {
  await ensureUp();
  const tools = await listTools();
  const tool = tools.find(t => t.name === name);
  if (!tool) {
    process.stderr.write(`Unknown command or tool: ${name}\nRun 'smth list' to see available tools.\n`);
    process.exitCode = 1;
    return;
  }
  const args = parseArgs(restArgv, tool.inputSchema ?? {});
  const result = await callTool(name, args);
  renderResult(result);
}
