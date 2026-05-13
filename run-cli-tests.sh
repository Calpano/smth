#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Ensure the container is up. Unlike run-tests.sh, we don't force-recreate —
# the CLI tests run against whatever smth is currently serving on localhost:3000.
# This makes the suite cheap and repeatable: no rebuild, no PAGES_DIR shuffle.
echo "Checking smth container..."
if ! curl --silent --fail --max-time 2 http://localhost:3000/health > /dev/null; then
  echo "smth not healthy — starting it..."
  docker compose up -d smth
  for i in {1..30}; do
    if curl --silent --fail --max-time 2 http://localhost:3000/health > /dev/null; then break; fi
    sleep 1
  done
  curl --silent --fail --max-time 2 http://localhost:3000/health > /dev/null
fi

echo "Running CLI tests..."
# parseArgs.test.mjs is a pure unit test (no server); cli.test.mjs and
# session.test.mjs are integration tests that subprocess `node bin/smth.js`.
node --test \
  test-cli/parseArgs.test.mjs \
  test-cli/cli.test.mjs \
  test-cli/session.test.mjs
