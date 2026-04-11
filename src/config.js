// Runtime configuration read from environment variables.

export const CHROMIUM_PATH  = process.env.CHROMIUM_PATH  || '/usr/bin/chromium';
export const PAGES_DIR      = process.env.PAGES_DIR      || '/pages';
export const HOST_PAGES_DIR = process.env.HOST_PAGES_DIR || '';
export const PORT           = parseInt(process.env.PORT || '3000');
