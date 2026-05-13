#!/usr/bin/env node
import { main } from '../src/cli/index.js';

main(process.argv.slice(2)).catch(err => {
  process.stderr.write(`smth: ${err.message}\n`);
  process.exit(1);
});
