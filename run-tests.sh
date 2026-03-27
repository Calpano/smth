#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Force PAGES_DIR to this project's pages so tests always get the right volume,
# regardless of any PAGES_DIR set in the caller's environment.
export PAGES_DIR="$(pwd)/test-pages"

# ── Build images ──────────────────────────────────────────────────────────────
echo "Building images..."
docker compose build smth test

# ── (Re)start smth so it runs the freshly built image ─────────────────────────
echo "Starting smth..."
docker compose up -d --force-recreate smth

echo "Waiting for smth to be healthy..."
elapsed=0
until docker compose ps smth | grep -q "healthy"; do
  sleep 2; elapsed=$((elapsed + 2))
  if [ $elapsed -ge 60 ]; then echo "Timed out waiting for smth"; exit 1; fi
done

# ── Run tests ─────────────────────────────────────────────────────────────────
echo "Running tests..."
docker compose --profile test run --rm test
