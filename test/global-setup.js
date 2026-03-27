export async function setup() {
  const url = process.env.SMTH_URL ?? 'http://localhost:3000';
  try {
    const res = await fetch(`${url}/health`, { signal: AbortSignal.timeout(5000) });
    if (!res.ok) throw new Error(`status ${res.status}`);
    const body = await res.json();
    console.log(`\n  smth server ready at ${url} (${JSON.stringify(body)})\n`);
  } catch (err) {
    throw new Error(
      `smth server not reachable at ${url}.\n` +
      `Start it with:  docker compose up -d\n` +
      `Then run tests: docker compose --profile test run --rm test\n` +
      `Cause: ${err.message}`
    );
  }
}
