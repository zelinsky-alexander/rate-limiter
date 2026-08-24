#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
wait_for_redis() {
  local port="$1"
  for _ in $(seq 1 30); do
    if docker compose exec -T "redis-${port}" redis-cli -p "$port" ping 2>/dev/null | grep -q PONG; then return 0; fi
    sleep 1
  done
  echo "Redis on port ${port} did not become ready" >&2; return 1
}
case "${1:-}" in
  up)
    docker compose up -d redis-7000 redis-7001 redis-7002
    for port in 7000 7001 7002; do wait_for_redis "$port"; done
    if docker compose exec -T redis-7000 redis-cli -p 7000 cluster info 2>/dev/null | grep -q 'cluster_state:ok'; then echo "Redis Cluster already initialized"; exit 0; fi
    docker compose exec -T redis-7000 redis-cli --cluster create 127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 --cluster-replicas 0 --cluster-yes
    ;;
  down) docker compose down --remove-orphans ;;
  status)
    docker compose exec -T redis-7000 redis-cli -p 7000 cluster info
    docker compose exec -T redis-7000 redis-cli -p 7000 cluster nodes
    ;;
  reset)
    docker compose down --remove-orphans
    docker compose up -d redis-7000 redis-7001 redis-7002
    for port in 7000 7001 7002; do wait_for_redis "$port"; done
    docker compose exec -T redis-7000 redis-cli --cluster create 127.0.0.1:7000 127.0.0.1:7001 127.0.0.1:7002 --cluster-replicas 0 --cluster-yes
    ;;
  *) echo "usage: $0 {up|down|status|reset}" >&2; exit 2 ;;
esac
