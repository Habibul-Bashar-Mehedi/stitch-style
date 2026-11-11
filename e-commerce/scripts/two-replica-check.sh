#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT/backend/build/libs/stitch-style-backend-0.1.0.jar"
LOG1=/tmp/stitchstyle-r1.log
LOG2=/tmp/stitchstyle-r2.log
BASE1=http://localhost:18090
BASE2=http://localhost:18091

if [ ! -f "$JAR" ]; then
  echo "Build the jar first: (cd $ROOT/backend && ./gradlew bootJar)" >&2
  exit 1
fi

cleanup() {
  [ -n "${P1:-}" ] && kill "$P1" 2>/dev/null || true
  [ -n "${P2:-}" ] && kill "$P2" 2>/dev/null || true
}
trap cleanup EXIT

start() {
  local port="$1" log="$2"
  SERVER_PORT="$port" java -jar "$JAR" \
    --spring.profiles.active=redis \
    --app.dev-tools.enabled=true \
    --app.cache.redis.enabled=true \
    --spring.data.redis.host=localhost \
    --spring.data.redis.port=6379 \
    --spring.datasource.url=jdbc:postgresql://localhost:5432/stitchstyle \
    > "$log" 2>&1 &
  echo $!
}

wait_up() {
  local base="$1"
  for _ in $(seq 1 60); do
    if curl -fsS "$base/actuator/health" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "Replica did not start: $base" >&2
  return 1
}

P1=$(start 18090 "$LOG1")
P2=$(start 18091 "$LOG2")
wait_up "$BASE1"
wait_up "$BASE2"
echo "replicas: $(curl -fsS "$BASE1/api/v1/dev/ping") / $(curl -fsS "$BASE2/api/v1/dev/ping")"

VALUE="Coherence-$(date +%s)"
curl -fsS -X POST "$BASE1/api/v1/dev/settings" -H 'Content-Type: application/json' \
  -d "{\"key\":\"brand.name\",\"value\":\"$VALUE\"}" >/dev/null

BRAND2=$(curl -fsS "$BASE2/api/v1/public/home" | python3 -c 'import sys,json; print(json.load(sys.stdin)["settings"].get("brand.name",""))')
if [ "$BRAND2" != "$VALUE" ]; then
  echo "cache coherence FAILED: replica 2 saw '$BRAND2', expected '$VALUE'" >&2
  exit 1
fi
echo "cache coherence: replica 2 saw the new value"

kill "$P1" 2>/dev/null || true
sleep 2
if ! curl -fsS "$BASE2/actuator/health" >/dev/null 2>&1; then
  echo "failover FAILED: replica 2 is not healthy after replica 1 stopped" >&2
  exit 1
fi
echo "failover: replica 2 still serves after replica 1 stopped"
