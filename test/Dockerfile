FROM node:20-bookworm-slim
WORKDIR /app
COPY test/package.json test/package-lock.json ./
# vitest.config.js must be writable (Vite writes a .timestamp-*.mjs file next to it).
# Copy it into the image so it lives outside the read-only volume mount.
COPY test/vitest.config.js ./vitest.config.js
RUN npm ci
# Test source files (helpers.js, global-setup.js, *.test.js) are mounted at runtime.
CMD ["npx", "vitest", "run"]
