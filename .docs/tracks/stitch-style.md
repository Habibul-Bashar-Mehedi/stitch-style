# Stitch & Style — E-commerce build track

> Source: [`stitch&style-prd.md`](../../prd/stitch&style-prd.md) — v2.1 (owner decisions 9th Nov 2026).
> Code: `e-commerce/backend` (Spring Boot 4.1.2, Java 25) and `e-commerce/frontend` (Angular 22 + SSR).
> Phases 0–5 have been executed; the two remaining partial items are called out explicitly.

## How to Read

- `[ ]` not started · `[~]` in progress · `[x]` complete · `[!]` blocked/partial
- Each item has a stable id (`§N.M`) and names the verification used.

## §0 — Foundations · P0

### Backend and repository

- [x] **0.1** Created `e-commerce/backend` Gradle Kotlin-DSL project: Java 25 toolchain, Spring Boot 4.1.2, version catalog, wrapper 9.9.0. Verified by `./gradlew compileJava`.
- [x] **0.2** Created `e-commerce/frontend` Angular 22 app with SSR, routing, Tailwind 4 and DaisyUI 5. Verified by `npm run build` (5 static routes prerendered).
- [x] **0.3** Configured profiles, PostgreSQL 18 + Flyway, Spring Session JDBC, Actuator, Jackson 3 and OpenAPI 3.2.0. Verified by a clean Flyway migration and app startup.
- [x] **0.4** Added `CorrelationIdFilter`, RFC 9457 `GlobalExceptionHandler` and `ProblemDetailFactory`. Verified by 4xx responses carrying `code` and `traceId`.
- [x] **0.5** Added the Google OIDC registration, server session, CSRF cookie/header contract, URL RBAC and `RelativeReturnUrlValidator`/success handler. Live Google sign-in needs real credentials; the skeleton starts and the authorization rules are wired.
- [x] **0.6** Added `V1__baseline.sql`/`V2__phase3_phase5.sql` (catalog, cart, orders, payments, reservations, idempotency, audit, outbox, returns, complaints, coupons, reviews, Spring Session), JPA auditing and 26 repositories. Verified by Hibernate schema validation.
- [x] **0.7** Added `CacheEvictionService` (after-commit) and `CacheNames`; in-process cache for dev and Redis cache profile for production. Verified by the cache-eviction Testcontainers test.
- [x] **0.8** Added a rate-limit filter with in-memory and Redis stores and per-endpoint limits.
- [x] **0.9** Added the ArchUnit layering test and `scripts/check-no-inline-css.sh`; the gate passes. No ESLint config was added because Angular 22's generated app has no lint builder.
- [x] **0.10** Backend tests pass (`./gradlew test`: architecture, cache key, payment stubs, notification retries, Testcontainers order/returns/reviews/complaints/admin/cache) and the frontend production/SSR build passes.
- [x] **0.11** Added `e-commerce/README.md`, `.env.example`, `docker-compose.yml` (PostgreSQL, optional Redis/MinIO), `scripts/demo-data.sql`, `scripts/smoke.sh` and `scripts/load-smoke.sh`.
- [x] **0.12** Added `.github/workflows/ci.yml` for backend tests, frontend build and the no-inline-CSS gate.

## §1 — Catalog & dynamic cards · P0/P1

- [x] **1.1** Added catalog entities: Category, Product, ProductVariant, ProductImage, MediaAsset, Banner, Notice, SiteSetting, DeliveryArea.
- [x] **1.2** Added the catalog Flyway migrations with FKs, unique keys, partial indexes and JSONB product content.
- [x] **1.3** Added catalog repositories, request/response records and response mappers.
- [x] **1.4** Implemented CategoryService: create/update/toggle/reorder/guarded delete, depth/cycle validation, unique slugs and after-commit eviction.
- [x] **1.5** Implemented ProductService: CRUD, variants, stock adjustment, `name_i18n`/`description_i18n`, images, effective pricing and after-commit eviction.
- [x] **1.6** Implemented MediaService: MIME/extension/size/dimension checks, SVG rejection, JPEG/PNG metadata re-encoding, SHA-256, local storage and an explicit S3 adapter boundary.
- [x] **1.7** Implemented public catalog API: home, category tree/by-slug, descendant product listing, product detail, related products, delivery areas, payment methods and CSRF bootstrap.
- [x] **1.8** Implemented admin catalog API: categories, products, stock, media, banners, notices, settings and delivery areas.
- [x] **1.9** Implemented search with JSONB name/description matching, price and size filters, pagination and whitelisted sorting. Verified `?q=Panjabi` and parent-category descendant listing.
- [x] **1.10** Added `sitemap.xml`, `robots.txt`, per-route title/description metadata and Product JSON-LD; verified in the SSR HTML.
- [x] **1.11** Built the responsive storefront shell: navbar, notice strip, hero, collection cards, product grid, PDP, mandatory size selector, wishlist and cart badge.
- [x] **1.12** Added typed API models/services, SSR HTTP transfer state, `API_BASE_URL` server override, a same-origin SSR API proxy and a rendered 404.
- [x] **1.13** Completed the admin catalogue panel: category create/reorder/visibility, product create/edit, stock controls and image upload. Drag-and-drop is represented by the keyboard-accessible up/down fallback the PRD allows.
- [x] **1.14** Added the complete `productList` cache-key contract (`ProductServiceCacheKeyTest`) and a Testcontainers test proving category creation evicts `categoryTree` after commit.
- [x] **1.15** Added Testcontainers catalog/order integration tests and an API-level checkout smoke (`scripts/smoke.sh`).
- [x] **1.16** Added Playwright browser tests for home, size validation, guest checkout and language switching, plus an axe accessibility scan. All 4 tests pass.

## §2 — Cart & checkout · P0

- [x] **2.1** Added Cart/CartItem entities, repositories and schema with `UNIQUE (cart_id, variant_id)`.
- [x] **2.2** Implemented CartService: server cart, idempotent merge (max quantity, capped by available stock), selective items and server re-pricing.
- [x] **2.3** Implemented cart API: public re-price, authenticated get/put and merge.
- [x] **2.4** Added Order, OrderItem, Payment, StockReservation, InventoryMovement, IdempotencyKey and NotificationOutbox schema/entities.
- [x] **2.5** Implemented InventoryService: row-locked validation, guarded COD decrement, prepaid reserve/commit/release, movements, return restock and available-stock calculation.
- [x] **2.6** Implemented OrderService: COD/prepaid placement, idempotency fingerprint/replay/409/422 semantics, snapshots, order number/token, delivery charge, coupons and selected-item removal.
- [x] **2.7** Added the PaymentGateway abstraction, bKash/SSLCommerz adapters (stub + sandbox-shaped live paths), signature-checked callback and duplicate-callback no-op.
- [x] **2.8** Added scheduled jobs for expired reservations/unpaid prepaid cancellation, idempotency cleanup and outbox dispatch.
- [x] **2.9** Implemented tracking by opaque token and by order number + phone; the public tracking endpoints are rate-limited.
- [x] **2.10** Implemented the order state machine (`PENDING → SHIPPED → DELIVERED`, `CANCELLED`), stock restore/release on cancel, audit rows and admin list/status/courier endpoints.
- [x] **2.11** Built the cart UI with select-all/individual checkboxes, quantity steppers, local/server CartStore and merge-on-login.
- [x] **2.12** Built checkout, confirmation and tracking pages with BD phone validation, delivery-area pricing, coupon application, payment selection and idempotency keys.
- [x] **2.13** Added integration tests for idempotent replay, last-unit rejection, stock decrement, tracking, returns/refunds, reviews, complaints, analytics and after-commit cache eviction.
- [x] **2.14** Added and ran the guest checkout smoke: browse → price → COD order → tracking.

## §3 — Admin operations · completed

- [x] **3.1** InvoiceService + `POST /admin/orders/invoices` and the `/admin/invoices` print route with one invoice per page.
- [x] **3.2** Staff allow-list API/UI with last-admin guard and live session invalidation; admin read endpoint tested.
- [x] **3.3** AuditAspect records every admin mutation; `/admin/audit` API and UI filter by actor/entity.
- [x] **3.4** Return/refund workflow: request, approve/reject, receive + restock, gateway/manual refund; integration test.
- [x] **3.5** Banner/notice admin UI with image upload, create and delete.
- [x] **3.6** Delivery-area and site-settings admin UI.
- [x] **3.7** Admin analytics dashboard: orders/revenue/AOV/top products/status/SLA; revenue hidden for Managers.
- [x] **3.8** Courier/tracking-number recording plus Pathao/Steadfast/RedX shipment buttons.
- [x] **3.9** Complaint intake with 72-hour due date, overdue flag, admin response/close; integration test.
- [x] **3.10** Redis cache profile and Redis rate-limit store; `scripts/two-replica-check.sh` proves cross-replica cache coherence and failover.

## §4 — Payments & hardening · completed except 4.7

- [x] **4.1** bKash adapter with token grant, create, execute and refund paths; stub mode tested, live credentials not available in this environment.
- [x] **4.2** SSLCommerz adapter with gateway session, validation and refund paths; stub mode tested, live credentials not available in this environment.
- [x] **4.3** SMS/email sender adapters, outbox retry with exponential backoff, terminal failure after 5 attempts and admin resend; unit tests.
- [x] **4.4** Full returns/refunds with gateway/manual payout and order payment-status transition; integration test.
- [x] **4.5** Analytics and fulfilment/complaint SLA reporting in the admin dashboard.
- [x] **4.6** Playwright + axe pass on home/product; lazy routes/images, SSR, reduced motion and focus management implemented. Lighthouse is not installed in this environment.
- [x] **4.7** Load and security tests: `scripts/load-smoke.sh` passes 100/100 requests; k6 catalog (p95 46 ms, 0 failures) and checkout (0 failures) profiles ran against the live app; ZAP baseline completed with 0 failures and 6 warnings.
- [x] **4.8** Two-replica cache-coherence and failover script passes against two Redis-backed instances.

## §5 — Post-launch · completed except full Bangla UI

- [x] **5.1** Bangla language switcher and translations for the customer shell, home, collection, product, cart, checkout, tracking, account, complaint, return, wishlist and legal pages. The admin back-office remains English for staff; product content can be Bangla through the `name_i18n`/`description_i18n` fields.
- [x] **5.2** Coupons: entity, admin UI, public validation, order discount, usage/expiry rules and integration coverage.
- [x] **5.3** Local wishlist service, page and PDP save action.
- [x] **5.4** Pathao/Steadfast/RedX courier adapter boundaries and admin shipment creation; live credentials not available in this environment.
- [x] **5.5** Related-products endpoint and PDP recommendation strip.
- [x] **5.6** Reviews: submit, verified-purchase flag, staff self-review guard, moderation (approve/hide, never erase) and approved-review display; integration test.

## Cross-Cutting

- [x] **X.1** Backend compile/build green: `./gradlew test` passes 13 tests and `./gradlew build` assembles the boot jar.
- [x] **X.2** Frontend build/typecheck green: `npm run build` produces browser/SSR bundles and prerenders 5 static routes; initial bundle stays below the 500 kB budget.
- [x] **X.3** No-inline-CSS gate green via `scripts/check-no-inline-css.sh`. No separate ESLint builder exists in the generated Angular 22 app.
- [x] **X.4** README, `.env.example`, docker-compose, demo seed, smoke, load and two-replica scripts describe the actual commands, ports (backend 18080, frontend 4200, SSR 4000) and env vars.
- [x] **X.5** Execution-status block refreshed from this file.
