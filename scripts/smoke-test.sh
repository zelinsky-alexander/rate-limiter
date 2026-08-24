#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./scripts/redis-cluster.sh up
mvn -q -DskipTests package

log_file="${TMPDIR:-/tmp}/rate-limiter-smoke.log"
RATE_LIMIT_CAPACITY=3 \
RATE_LIMIT_REFILL_PER_SECOND=0.1 \
FALLBACK_GATEWAY_COUNT=1 \
REDIS_TIMEOUT_MS=100 \
java -jar target/rate-limiter.jar >"$log_file" 2>&1 &
app_pid=$!

cleanup() {
  kill "$app_pid" 2>/dev/null || true
  wait "$app_pid" 2>/dev/null || true
  docker compose down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

ready=false
for _ in $(seq 1 80); do
  if curl -fsS http://127.0.0.1:8080/health >/dev/null 2>&1; then
    ready=true
    break
  fi

  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "gateway exited before becoming ready" >&2
    cat "$log_file" >&2
    exit 1
  fi

  sleep 0.25
done

if [[ "$ready" != true ]]; then
  echo "gateway did not become ready within 20 seconds" >&2
  cat "$log_file" >&2
  exit 1
fi

codes=()
for _ in 1 2 3 4 5; do
  codes+=("$(curl -sS -o /dev/null -w '%{http_code}' -H 'X-API-Key: smoke-client' http://127.0.0.1:8080/limited)")
done

expected="200 200 200 429 429"
actual="${codes[*]}"
if [[ "$actual" != "$expected" ]]; then
  echo "central limiter smoke failure: expected [$expected], got [$actual]" >&2
  cat "$log_file" >&2
  exit 1
fi

docker compose stop redis-7000 redis-7001 redis-7002 >/dev/null
headers="$(curl -sS -D - -o /dev/null -H 'X-API-Key: fallback-client' http://127.0.0.1:8080/limited | tr -d '\r')"
if ! grep -qi '^X-RateLimit-Source: local-fallback$' <<<"$headers"; then
  echo "fallback smoke failure: expected X-RateLimit-Source: local-fallback" >&2
  echo "$headers" >&2
  cat "$log_file" >&2
  exit 1
fi

echo "smoke test passed: central enforcement and Redis-outage fallback both verified"
