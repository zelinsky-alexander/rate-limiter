#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./scripts/redis-cluster.sh up
mvn -q -DskipTests package

ports=(8080 8081 8082)
pids=()
logs=()

cleanup() {
  for pid in "${pids[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
  for pid in "${pids[@]:-}"; do
    wait "$pid" 2>/dev/null || true
  done
  docker compose down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

for port in "${ports[@]}"; do
  log_file="${TMPDIR:-/tmp}/rate-limiter-e2e-${port}.log"
  logs+=("$log_file")
  RATE_LIMIT_PORT="$port" \
  RATE_LIMIT_CAPACITY=6 \
  RATE_LIMIT_REFILL_PER_SECOND=0.01 \
  FALLBACK_GATEWAY_COUNT=3 \
  REDIS_TIMEOUT_MS=100 \
  java -jar target/rate-limiter.jar >"$log_file" 2>&1 &
  pids+=("$!")
done

for i in "${!ports[@]}"; do
  port="${ports[$i]}"
  pid="${pids[$i]}"
  log_file="${logs[$i]}"
  ready=0
  for _ in $(seq 1 80); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "gateway on port $port exited before becoming ready" >&2
      cat "$log_file" >&2
      exit 1
    fi
    if curl -fsS "http://127.0.0.1:${port}/health" >/dev/null; then
      ready=1
      break
    fi
    sleep 0.25
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "gateway on port $port did not become ready" >&2
    cat "$log_file" >&2
    exit 1
  fi
done

api_key="shared-client-${RANDOM}-${RANDOM}"
codes=()
for port in 8080 8081 8082 8080 8081 8082; do
  codes+=("$(curl -sS -o /dev/null -w '%{http_code}' -H "X-API-Key: ${api_key}" "http://127.0.0.1:${port}/limited")")
done
codes+=("$(curl -sS -o /dev/null -w '%{http_code}' -H "X-API-Key: ${api_key}" "http://127.0.0.1:8080/limited")")

expected="200 200 200 200 200 200 429"
actual="${codes[*]}"
if [[ "$actual" != "$expected" ]]; then
  echo "multi-gateway e2e failure: expected [$expected], got [$actual]" >&2
  for log_file in "${logs[@]}"; do
    echo "--- $log_file ---" >&2
    cat "$log_file" >&2
  done
  exit 1
fi

echo "multi-gateway e2e passed: three gateway processes consumed one shared Redis bucket"
