# Stitch & Style — e-commerce implementation

This directory contains the Phase 0–2 implementation of the PRD at
[`../prd/stitch&style-prd.md`](../prd/stitch&style-prd.md).

- `backend/` — Spring Boot 4.1.2 / Java 25 modular monolith
- `frontend/` — Angular 22 + SSR, Tailwind 4 and DaisyUI 5
- `scripts/` — demo seed SQL and an API-level checkout smoke test

## Prerequisites

- Java 25
- Node.js 24+ and npm
- Docker (PostgreSQL container)
- Gradle wrapper is committed (Gradle 9.9.0)

## Run locally

```bash
# 1. Database (shared PostgreSQL on localhost:5432, user/password lemon)
#    Create it once if it does not exist:
docker exec bigtheta-db-postgresql-latest-container \
  psql -U lemon -d postgres -c "CREATE DATABASE stitchstyle OWNER lemon;"

# 2. Demo data (the schema is created by Flyway on first boot)
cd backend
./gradlew bootRun
# in another terminal, after the app is healthy:
docker exec -i bigtheta-db-postgresql-latest-container \
  psql -U lemon -d stitchstyle < ../scripts/demo-data.sql
```

The backend listens on `http://localhost:38133` (the usual 8080–8084 range is
often occupied by other local services). Override with `SERVER_PORT` if needed,
and keep `frontend/proxy.conf.json` plus the SSR `API_PROXY_TARGET` on the same
port.

```bash
# 3. Storefront (dev server, proxied to the backend)
cd ../frontend
npm install
npm start
# http://localhost:4200

# Or run the production SSR bundle
npm run build
PORT=4000 node dist/frontend/server/server.mjs
# http://localhost:4000
```

### API documentation

With the backend running, the interactive contract is served by springdoc:

- Swagger UI — <http://localhost:38133/swagger-ui.html>
- OpenAPI JSON — <http://localhost:38133/v3/api-docs>

Operations are grouped by area (`Storefront`, `Customer`, `Admin`, `Authentication`,
`Developer`). `Customer` and `Admin` calls need a signed-in session; state-changing
calls also need the `X-XSRF-TOKEN` header. Fetch the token with
`GET /api/v1/public/csrf`, then copy the value of the `XSRF-TOKEN` cookie into the
`X-XSRF-TOKEN` field Swagger UI shows on each POST/PUT/PATCH/DELETE.

Swagger UI and `/v3/api-docs` are **ADMIN-only**. Sign in as an admin and open
**API docs** from the admin navigation bar (the frontend proxies `/swagger-ui/**`
and `/v3/api-docs/**` to the backend, so the link works on both the dev server and
the SSR server). They remain disabled entirely in the `prod` profile.

## Port-less local URL (Docker + Nginx)

```bash
cd frontend
docker compose up -d --build
```

Then open <http://localhost> (or `http://stitchandstyle.local`,
`http://stitch-and-style.local`, `http://stitchstyle.local` after adding the hosts
entry). Nginx terminates port 80 and proxies to the Angular dev server with HMR.
Full setup, hostname and browser-keyword instructions: [`frontend/DOCKER.md`](frontend/DOCKER.md).

## Configuration

Copy `.env.example` values into your shell or an untracked `.env` loader.
Google OIDC, bKash/SSLCommerz and S3 are integration boundaries in this phase;
the app starts with placeholder credentials and the local media adapter.

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/stitchstyle` | PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `lemon` | Database credentials |
| `SERVER_PORT` | `38133` | Backend port |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | dev placeholders | Google OIDC |
| `BOOTSTRAP_ADMIN_EMAILS` | `admin@example.com` | First Admin allow-list |
| `MEDIA_STORAGE` | `local` | `local` or `s3` (stub in this phase) |
| `MEDIA_LOCAL_ROOT` | `./data/media` | Uploaded media folder. A relative path resolves against the **launch directory**, so use an absolute path when the backend is started from more than one place (Gradle, IDE, SSR). |
| `PAYMENT_CALLBACK_SECRET` | `dev-callback-secret` | Stub callback signature |
| `PUBLIC_BASE_URL` | `http://localhost:4000` | Sitemap base URL |

### Google sign-in

Put the client id/secret pair from one Google Cloud OAuth client in `.env`
(`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) — never in `application.yml`, and
never mix the id from one client with the secret from another.

The browser talks to the frontend origin, so register the frontend callback
URLs in Google Cloud Console:

```
http://localhost:4200/login/oauth2/code/google   # npm start (dev proxy)
http://localhost:4000/login/oauth2/code/google   # SSR server
```

Only add `http://localhost:38133/login/oauth2/code/google` if you open the
backend directly (bypassing the frontend proxy). The session cookie is named
`STITCHSTYLE_SESSION`, not `SESSION`, so other local apps cannot overwrite it.

## Verification

```bash
# Backend unit, architecture and Testcontainers integration tests
cd backend
./gradlew test

# Same, plus the JaCoCo HTML/XML coverage report (build/reports/jacoco/test/)
./gradlew test jacocoTestReport

# Optional coverage gate (LINE >= 70%, not wired into `check`)
./gradlew jacocoTestCoverageVerification

# Frontend production/SSR build
cd ../frontend
npm run build

# Browser E2E (real backend, real DB, real sessions) + axe accessibility
npm run e2e:all              # builds/starts the stack, seeds, runs Playwright
npm run e2e -- --headed      # re-run against the stack already started
npm run e2e:stop             # stop the E2E backend (:38134) and SSR server (:4100)

# API-level browse → price → order → track smoke
cd ..
BASE_URL=http://localhost:38133 ./scripts/smoke.sh

# Dependency-free load smoke
BASE_URL=http://localhost:38133 ./scripts/load-smoke.sh

# Grafana k6 load test + dashboard (tests/k6)
cd tests/k6
docker compose up -d          # InfluxDB :8086 + Grafana :3000
./run.sh smoke                # or: ./run.sh catalog / checkout
# dashboard: http://localhost:3000/d/k6-load-testing
cd ../..

# Two replicas sharing Redis: cache coherence + failover
./scripts/two-replica-check.sh
```

The smoke test covers the guest checkout path, idempotency key, stock
decrement, order number/token generation and tracking. Playwright covers
size validation, guest checkout, Bangla switching and serious/critical axe
violations. k6 catalog/checkout profiles and a ZAP baseline script are under
`tests/`; the ZAP script needs Docker and a running backend.

## What is implemented

- Dynamic categories/cards, products, variants, sizes, media metadata,
  banners, notices, settings and delivery areas.
- Public catalog API, caching with after-commit eviction, SSR storefront,
  collection/product pages, size validation, cart, selective checkout,
  COD/prepaid order placement, stock reservations, idempotency, payment
  callback stub, tracking, sitemap and robots.
- Google OIDC login skeleton, server-side session, CSRF, RBAC URL rules,
  RFC 9457 problem details, rate limiting, audit log, notification outbox.
- Angular storefront with wishlist, reviews, Bangla switching, a customer
  returns dashboard (eligibility, history, live refund quote), complaints and a
  full admin console (orders, invoices, returns with quote-based partial
  refunds, complaints, coupons, reviews, staff, audit, settings,
  banners/notices, analytics, catalogue and courier shipment creation).
- Real-time support chat: a customer↔manager support inbox plus manager↔admin
  internal DMs, with REST history, STOMP/WebSocket push, read receipts, typing,
  unread badges, optional Redis fan-out, offline email through the outbox and a
  24-month retention job.
- Transactional outbox notifications with SMS/email adapters and retries;
  bKash/SSLCommerz sandbox-shaped adapters; Redis cache/rate-limit profile;
  two-replica coherence/failover verification; k6 and ZAP scripts.

The original build track is complete: 71 / 71 items in
[`.docs/tracks/stitch-style.md`](../.docs/tracks/stitch-style.md). The
messages/returns track is complete: 40 / 40 items in
[`.docs/tracks/add-message-and-return.md`](../.docs/tracks/add-message-and-return.md).

### Browser E2E

The Playwright suite lives in `frontend/e2e/` and runs the real application:

```bash
cd frontend
npm run e2e:install   # once, downloads Chromium
npm run e2e:all       # stack + seed + tests
npm run e2e:stop      # when finished
```

It is isolated from local development: a dedicated `stitchstyle_e2e` database is
dropped and re-migrated on every setup, the backend runs on **38134** and the SSR
storefront on **4100**, so your dev servers on 38133/4200 are untouched. The demo
catalogue is seeded with `scripts/demo-data.sql`.

Authentication is real (session cookie, CSRF header, URL roles), but Google OIDC
cannot be driven headlessly. Instead, `global-setup.ts` calls
`POST /api/v1/dev/session` — a dev-tools-only endpoint that creates the same
server-side session OIDC would. It does not exist unless
`app.dev-tools.enabled=true`, and it is never enabled in the `prod` profile.

Layout: `e2e/src/pages/` (page objects with verified selectors), `e2e/src/tests/`
(storefront, checkout, account, admin, accessibility), `e2e/global-setup.ts`
(admin + customer storage states). Every test records a video in
`frontend/test-results/`; failures also keep a trace and screenshot.
