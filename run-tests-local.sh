#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

PORT=3001   # use 3001 to avoid clashing with Docker container on 3000

# ── Find Chrome/Chromium ───────────────────────────────────────────────────────
find_chrome() {
  local candidates=(
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    "/Applications/Chromium.app/Contents/MacOS/Chromium"
    "/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary"
    "/usr/bin/google-chrome"
    "/usr/bin/google-chrome-stable"
    "/usr/bin/chromium-browser"
    "/usr/bin/chromium"
    "/snap/bin/chromium"
  )
  # Also check $PATH
  for cmd in google-chrome google-chrome-stable chromium-browser chromium; do
    if command -v "$cmd" &>/dev/null; then
      echo "$(command -v "$cmd")"
      return
    fi
  done
  for path in "${candidates[@]}"; do
    if [ -x "$path" ]; then
      echo "$path"
      return
    fi
  done
  echo ""
}

CHROMIUM_PATH="${CHROMIUM_PATH:-$(find_chrome)}"

if [ -z "$CHROMIUM_PATH" ]; then
  echo "ERROR: Could not find Chrome or Chromium."
  echo "  Install Google Chrome, or set CHROMIUM_PATH=/path/to/chrome before running."
  exit 1
fi

echo "Using Chrome: $CHROMIUM_PATH"

# ── Start server ───────────────────────────────────────────────────────────────
SERVER_PID=""

cleanup() {
  if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
    echo ""
    echo "Stopping server (pid $SERVER_PID)..."
    kill "$SERVER_PID"
    wait "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "Starting smth on port $PORT..."
CHROMIUM_PATH="$CHROMIUM_PATH" \
PORT="$PORT" \
PAGES_DIR="$(pwd)/test-pages" \
  node src/server.js &
SERVER_PID=$!

# ── Wait for /health ───────────────────────────────────────────────────────────
echo "Waiting for server to be ready..."
elapsed=0
until curl -sf "http://localhost:$PORT/health" > /dev/null 2>&1; do
  sleep 1; elapsed=$((elapsed + 1))
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "ERROR: server process exited unexpectedly"
    exit 1
  fi
  if [ $elapsed -ge 30 ]; then
    echo "ERROR: timed out waiting for server on port $PORT"
    exit 1
  fi
done
echo "Server ready."

# ── Install test deps (test/package.json) if needed ───────────────────────────
if [ ! -d "test/node_modules" ]; then
  echo "Installing test dependencies..."
  npm install --prefix test
fi

# ── Run tests ──────────────────────────────────────────────────────────────────
echo ""
SMTH_URL="http://localhost:$PORT" \
  test/node_modules/.bin/vitest run --config test/vitest.config.js "$@"
