FROM node:20-bookworm-slim

# Install Chromium and its dependencies
RUN apt-get update && apt-get install -y \
    chromium \
    && rm -rf /var/lib/apt/lists/*

ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV CHROMIUM_PATH=/usr/bin/chromium
ENV PAGES_DIR=/pages
ENV PORT=3000

WORKDIR /app

COPY package.json .
RUN npm install --omit=dev

COPY src/ ./src/

RUN mkdir -p /pages

VOLUME ["/pages"]

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=5s \
  CMD node -e "fetch('http://localhost:3000/health').then(r=>r.ok?process.exit(0):process.exit(1)).catch(()=>process.exit(1))"

CMD ["node", "src/server.js"]
