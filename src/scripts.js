// Browser-side scripts that get injected via page.evaluate(). These are read
// from disk once at module-load time and exported as strings.

import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

export const seeFontsScript      = readFileSync(join(__dirname, 'see-fonts.js'),       'utf8');
export const seeColorsScript     = readFileSync(join(__dirname, 'see-colors.js'),      'utf8');
export const seeColorPairsScript = readFileSync(join(__dirname, 'see-color-pairs.js'), 'utf8');
export const seeDomScript        = readFileSync(join(__dirname, 'see-dom.js'),         'utf8');
