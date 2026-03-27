import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['test/**/*.test.js'],
    globalSetup: ['test/global-setup.js'],
    testTimeout: 60_000,   // browser ops can be slow
    hookTimeout: 30_000,
    fileParallelism: false, // sequential — each file owns the browser session
    reporter: ['verbose'],
  },
});
