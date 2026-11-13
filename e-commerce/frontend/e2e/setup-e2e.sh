#!/usr/bin/env bash
# Starts the E2E stack for Stitch & Style on remapped ports against a dedicated
# database, then (with --test) runs the Playwright suite.
#
#   e2e/setup-e2e.sh            # backend 38134 + SSR storefront 4100
#   e2e/setup-e2e.sh --test     # ... and run Playwright
#   e2e/setup-e2e.sh --skip-build --test
#   e2e/setup-e2e.sh --stop     # stop both servers
#
# The developer's Postgres data and the dev servers on 38133/4200 are never touched.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # e-commerce/frontend
ECOM="$(cd "$ROOT/.." && pwd)"                            # e-commerce
BACKEND="$ECOM/backend"

PG_CONTAINER="${PG_CONTAINER:-bigtheta-db-postgresql-latest-container}"
PG_USER="${PG_USER:-lemon}"
PG_PASSWORD="${PG_PASSWORD:-lemon}"
E2E_DB="${E2E_DB:-stitchstyle_e2e}"

BACKEND_PORT="${E2E_BACKEND_PORT:-38134}"
FRONTEND_PORT="${E2E_FRONTEND_PORT:-4100}"
BASE_URL="${E2E_BASE_URL:-http://localhost:$FRONTEND_PORT}"

RUN_DIR="$ROOT/e2e/.run"
MEDIA_DIR="$ROOT/e2e/.media"

RUN_TESTS=0
SKIP_BUILD=0
PLAYWRIGHT_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --test) RUN_TESTS=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --stop) STOP=1 ;;
    *) PLAYWRIGHT_ARGS+=("$arg") ;;
  esac
done

log() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31m✖ %s\033[0m\n' "$*" >&2; exit 1; }

stop_servers() {
  fuser -k "$BACKEND_PORT/tcp" "$FRONTEND_PORT/tcp" 2>/dev/null || true
  rm -f "$RUN_DIR"/*.pid 2>/dev/null || true
}

if [[ "${STOP:-0}" == "1" ]]; then
  log "Stopping E2E servers on $BACKEND_PORT and $FRONTEND_PORT"
  stop_servers
  exit 0
fi

mkdir -p "$RUN_DIR" "$MEDIA_DIR"

log "Checking PostgreSQL container ($PG_CONTAINER)"
docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" >/dev/null 2>&1 \
  || fail "PostgreSQL container '$PG_CONTAINER' is not reachable. Start it (docker compose up -d postgres) or set PG_CONTAINER."

if ! docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -tAc \
      "SELECT 1 FROM pg_database WHERE datname='$E2E_DB'" | grep -q 1; then
  log "Creating database $E2E_DB"
  docker exec "$PG_CONTAINER" createdb -U "$PG_USER" "$E2E_DB"
fi

log "Resetting schema in $E2E_DB (E2E data only)"
docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$E2E_DB" -v ON_ERROR_STOP=1 -q \
  -c 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;'

stop_servers
rm -rf "$MEDIA_DIR"; mkdir -p "$MEDIA_DIR"

log "Starting backend on :$BACKEND_PORT"
(
  cd "$BACKEND"
  DB_URL="jdbc:postgresql://localhost:5432/$E2E_DB" \
  DB_USERNAME="$PG_USER" \
  DB_PASSWORD="$PG_PASSWORD" \
  SERVER_PORT="$BACKEND_PORT" \
  DEV_TOOLS_ENABLED=true \
  MEDIA_LOCAL_ROOT="$MEDIA_DIR" \
  PAYMENT_CALLBACK_SECRET=dev-callback-secret \
  SESSION_COOKIE_SECURE=false \
  PUBLIC_BASE_URL="$BASE_URL" \
  ./gradlew bootRun --console=plain > "$RUN_DIR/backend.log" 2>&1 &
  echo $! > "$RUN_DIR/backend.pid"
)

printf '  waiting for backend'
for _ in $(seq 1 120); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$BACKEND_PORT/api/v1/public/csrf" || true)"
  [[ "$code" == "204" ]] && break
  printf '.'
  sleep 2
done
[[ "${code:-}" == "204" ]] || { tail -40 "$RUN_DIR/backend.log"; fail "Backend did not become ready on :$BACKEND_PORT"; }
printf ' ready\n'

log "Seeding demo catalogue"
docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$E2E_DB" -v ON_ERROR_STOP=1 -q \
  < "$ECOM/scripts/demo-data.sql"

if [[ "$SKIP_BUILD" == "0" ]]; then
  log "Building the SSR frontend"
  ( cd "$ROOT" && npm run build > "$RUN_DIR/build.log" 2>&1 ) \
    || { tail -30 "$RUN_DIR/build.log"; fail "Frontend build failed"; }
fi

log "Starting SSR storefront on :$FRONTEND_PORT"
(
  cd "$ROOT"
  PORT="$FRONTEND_PORT" \
  API_PROXY_TARGET="http://localhost:$BACKEND_PORT" \
  API_BASE_URL="http://localhost:$BACKEND_PORT" \
  node dist/frontend/server/server.mjs > "$RUN_DIR/frontend.log" 2>&1 &
  echo $! > "$RUN_DIR/frontend.pid"
)

printf '  waiting for storefront'
for _ in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/" || true)"
  [[ "$code" == "200" ]] && break
  printf '.'
  sleep 1
done
[[ "${code:-}" == "200" ]] || { tail -30 "$RUN_DIR/frontend.log"; fail "Storefront did not become ready on :$FRONTEND_PORT"; }
printf ' ready\n'

log "E2E stack is ready: $BASE_URL (backend :$BACKEND_PORT, database $E2E_DB)"
log "Logs: $RUN_DIR/backend.log, $RUN_DIR/frontend.log"

if [[ "$RUN_TESTS" == "1" ]]; then
  log "Running Playwright"
  (
    cd "$ROOT"
    E2E_BASE_URL="$BASE_URL" E2E_BACKEND_PORT="$BACKEND_PORT" E2E_FRONTEND_PORT="$FRONTEND_PORT" \
      npx playwright test "${PLAYWRIGHT_ARGS[@]}"
  )
fi
