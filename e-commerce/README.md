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

# Frontend production/SSR build
cd ../frontend
npm run build

# Browser E2E + axe accessibility
npx playwright test

# API-level browse → price → order → track smoke
cd ..
BASE_URL=http://localhost:38133 ./scripts/smoke.sh

# Dependency-free load smoke (k6 profiles are in tests/k6)
BASE_URL=http://localhost:38133 ./scripts/load-smoke.sh

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
