# Grafana k6 load testing — Stitch & Style

Load profiles for the public API plus a provisioned Grafana + InfluxDB stack that
plots every run.

```
tests/k6/
├── smoke.js          1 VU, 20 iterations — sanity / CI gate
├── catalog.js        ramp to 50 VUs — home, category tree, collection, product detail
├── checkout.js       cart re-pricing (POST /public/cart/price, no writes)
├── orders.js         full journey — browse, price, PLACE ORDER, track (writes!)
├── cart.js           cart pricing (guest) or persistent /me/cart (customer)
├── payment.js        prepaid checkout + payment callback -> PAID (writes!)
├── dashboard.js      admin dashboard — analytics summary + order/product lists
├── stress.js         stepped read-only stress: 500 -> 1000 -> 2000 VUs
├── lib/common.js     shared helper module (CSRF, dev login, catalogue)
├── docker-compose.yml  InfluxDB + Grafana (+ an on-demand k6 service)
├── grafana/          provisioned datasource + "Stitch & Style — k6 load test" dashboard
├── results/          end-of-test summary JSON files
└── run.sh            one-command runner
```

`smoke`, `catalog` and `checkout` are **read-only**. `orders` is **not** — it
places real orders and decrements real stock, so read the next two sections
before running it.

## Quick start

```bash
cd e-commerce/tests/k6

docker compose up -d                 # InfluxDB :8086 + Grafana :3000
./run.sh smoke                       # 3-second sanity run
./run.sh catalog                     # 80-second ramp to 50 VUs
```

Open the dashboard: <http://localhost:3000/d/k6-load-testing> (anonymous admin —
no login).

```bash
./run.sh checkout                    # cart-pricing profile
VUS_MAX=100 RAMP=30 ./run.sh catalog # heavier/shorter ramp
docker compose down                  # stop (add -v to wipe stored metrics)
```

## 500 concurrent users + orders

**Grafana does not generate load.** k6 generates the load, writes metrics to
InfluxDB, and Grafana draws them. So "testing Grafana" = starting that stack and
watching the dashboard while k6 runs.

`orders.js` is the 500-VU profile. It logs in (optionally), browses the home
page, prices a cart, places an order and tracks it — the whole customer journey.

### 1. Prepare the backend (three things, or the test lies)

**a. Rate limit.** `POST /api/v1/public/orders` is capped at **5 requests per
minute per IP** (`RateLimitFilter`). 500 VUs from one machine would be ~99 %
`429`, not a real test.

`orders.js` already emulates a different shopper per order by sending a unique
`Forwarded: for=10.x.x.x` header for each request (each real user has their own
IP), so it works against the default backend. If you want to load-test the
limiter itself instead, set `SPOOF_FORWARDED=false` and raise/disable it:

```bash
RATE_LIMIT_ENABLED=false ./gradlew bootRun
```

> Note: `RateLimitFilter.clientIp()` trusts the `Forwarded` header
> unconditionally. That is what lets the script work — and also means a real
> client can bypass the limiter by spoofing the header. Worth fixing before
> production (only trust it from your proxy).

**b. Stock.** Orders decrement `stock_quantity` immediately. The demo catalogue
only has ~110 units, so order 111 onward would fail with "out of stock":

```bash
# writes to the dev DB! 100k units per active variant
docker run --rm --network host -e PGPASSWORD=lemon postgres:18 \
  psql -h localhost -p 5432 -U lemon -d stitchstyle \
  -c "UPDATE product_variants SET stock_quantity = 100000, reserved_quantity = 0 WHERE is_active;"
```

**c. Concurrency knobs.** The defaults are the real bottleneck long before 500:

```bash
DB_POOL_SIZE=50 \
SERVER_TOMCAT_THREADS_MAX=500 \
./gradlew bootRun
```

`DB_POOL_SIZE` defaults to 10 and Tomcat to 200 threads, so 500 VUs with the
defaults is a *queueing* test, not a concurrent one. Raise them if you want to
measure the app rather than the queue.

### 2. Run it

```bash
cd e-commerce/tests/k6

docker compose up -d                    # InfluxDB + Grafana
./run.sh orders                         # 500 VUs, guest checkout, ~4 min
```

Equivalent explicit timings (60 s ramp up, 180 s at 500, 60 s ramp down):

```bash
VUS_MAX=500 RAMP=60 HOLD=180 ./run.sh orders
```

500 **signed-in** customers instead of guests:

```bash
# backend must be started with DEV_TOOLS_ENABLED=true
DEV_TOOLS_ENABLED=true ./gradlew bootRun
MODE=customer VUS_MAX=500 ./run.sh orders
```

`MODE=customer` calls `POST /api/v1/dev/session` once per VU (unique email
`loadtest-vu-<n>@example.com`) so you also load the session and `/api/v1/me`
paths. Without that flag the script fails fast in `setup()` with a clear message.

Browse-only 500 users (no writes) is just `catalog.js`:

```bash
VUS_MAX=500 RAMP=60 ./run.sh catalog
```

### 3. Read the dashboard

Open <http://localhost:3000/d/k6-load-testing>, set the time range to
**Last 15 minutes**, and watch while the test runs:

| Panel | What it tells you |
|---|---|
| Virtual Users | the ramp actually reached 500 |
| Requests per Second | throughput |
| Errors per Second | 429 = rate limit still on; 5xx = app/DB saturated |
| `http_req_duration` p95 / max | the latency users feel |
| Connection/queue wait | requests waiting for a free DB connection |

The per-request tags (`place-order`, `cart-price`, `track-order`, `dev-login`,
`home`, `csrf`) are in the metric name, so you can tell *which* step is slow.
The final numbers are also written to `results/orders-summary.json`.

### 4. Clean up

The run creates one real order per successful iteration. Safest reset is to
restore a database dump. If you only need to drop the load-test rows:

```sql
BEGIN;
-- give the stock back (COD orders decrement stock_quantity immediately)
UPDATE product_variants v SET stock_quantity = v.stock_quantity + x.qty
FROM (SELECT oi.variant_id, SUM(oi.quantity) AS qty
      FROM order_items oi JOIN orders o ON o.id = oi.order_id
      WHERE o.note = 'k6 load test' GROUP BY oi.variant_id) x
WHERE v.id = x.variant_id;

DELETE FROM inventory_movements
WHERE reference_type = 'ORDER'
  AND reference_id IN (SELECT id FROM orders WHERE note = 'k6 load test');

UPDATE idempotency_keys SET order_id = NULL WHERE idempotency_key LIKE 'k6-%';
UPDATE notification_outbox SET order_id = NULL
  WHERE order_id IN (SELECT id FROM orders WHERE note = 'k6 load test');
DELETE FROM orders WHERE note = 'k6 load test';            -- items/payments/reservations cascade
DELETE FROM idempotency_keys WHERE idempotency_key LIKE 'k6-%';
DELETE FROM users WHERE email LIKE 'loadtest-vu-%@example.com';
COMMIT;
```

> For a serious run, point `DB_URL` at a disposable database (e.g.
> `stitchstyle_loadtest`) so a 500-order test never touches your dev data.

## 500 VUs on cart, payment, dashboard and checkout

| Profile | What it exercises | Writes? | Needs `DEV_TOOLS_ENABLED=true` |
|---|---|---|---|
| `cart.js` (`MODE=guest`) | `POST /public/cart/price` | no | no |
| `cart.js` (`MODE=customer`) | `PUT/GET/DELETE /me/cart` | carts | **yes** |
| `payment.js` | prepaid order → gateway → callback → `PAID` | orders, payments, stock | no |
| `dashboard.js` | `GET /admin/analytics/summary` + order/product lists | no | **yes** |
| `orders.js` | COD checkout → order → track | orders, stock | no |

```bash
./run.sh cart                              # guest cart pricing, 500 VUs
MODE=customer ./run.sh cart                # persistent cart, 500 signed-in users
./run.sh payment                           # prepaid checkout + callback, 500 VUs
PAYMENT_PROVIDER=SSLCOMMERZ ./run.sh payment
./run.sh orders                            # COD checkout (see previous section)
./run.sh dashboard                         # admin dashboard, 500 VUs
```

### Payment profile

`BKASH_ENABLED` / `SSLCOMMERZ_ENABLED` are `false` by default, and in that state
the gateway classes run in **stub mode**: `initiate` returns
`/pay/stub?provider=…&txn=…` and `verifyCallback` accepts
`signature=dev-callback-secret`. So the script can drive the whole
order → `INITIATED` → callback → `PAID` chain without any external call.

The callback secret is `app.payments.callback-secret` (default
`dev-callback-secret`); override with `CALLBACK_SECRET=…` if you changed it.

Prepaid orders *reserve* stock on placement and *commit* it on callback, so
restock before the run exactly as in the checkout section, and use the same
cleanup SQL (payments and stock reservations cascade with the order).

### Dashboard and persistent cart need dev-tools

Both need a real session, and the only non-Google way in is
`POST /api/v1/dev/session`. That endpoint only exists when the backend starts
with `DEV_TOOLS_ENABLED=true`. In IntelliJ: **Run → Edit Configurations →
Environment variables → `DEV_TOOLS_ENABLED=true`**, then restart. From a shell:

```bash
DEV_TOOLS_ENABLED=true ./gradlew bootRun
```

`dashboard.js` signs in as the same admin on every iteration
(`ADMIN_EMAIL`, default `loadtest-admin@example.com`);
`MODE=customer ./run.sh cart` creates one customer per VU
(`loadtest-cart-<n>@example.com`).

> k6 **resets the VU cookie jar between iterations**, so a session established in
> iteration 1 is gone in iteration 2. These profiles therefore log in at the
> start of every iteration. Clean up afterwards:
>
> ```sql
> DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME LIKE 'loadtest-%';
> DELETE FROM users WHERE email LIKE 'loadtest-%';   -- carts cascade
> ```

> The dashboard's `analytics/summary` runs several aggregate queries over the
> `orders` table. It is the best signal for "the back-office screen is slow when
> order volume grows" — run it *after* a checkout/payment run has put data in
> the table, not against an empty database.

## Finding the ceiling — stepped stress test

`stress.js` runs the same read-only journey (home + product list + cart pricing)
at increasing concurrency. Each level is its own k6 scenario tagged `level`, so
InfluxDB and Grafana keep them separate.

```bash
./run.sh stress                              # 500 -> 1000 -> 2000 VUs
LEVELS=500,1000,2000,3000 ./run.sh stress    # push further
RAMP=10 HOLD=60 ./run.sh stress              # longer hold per level
```

Default shape per level: 10 s ramp up, 40 s hold, 10 s down; 5 s gap between
levels. Compare levels in Grafana by grouping on the `level` tag, or query
InfluxDB:

```sql
SELECT sum("value") FROM "http_reqs" GROUP BY "level";
SELECT percentile("value",95) FROM "http_req_duration" GROUP BY "level";
```

### Measured on the dev laptop (backend in IntelliJ debug mode)

| Level | Throughput | avg | p95 | Errors |
|---|---|---|---|---|
| 500 VUs | ~311 req/s | 1.33 s | 3.58 s | 0 % |
| 1000 VUs | ~381 req/s | 2.27 s | 4.76 s | 0 % |
| 2000 VUs | ~341 req/s | 5.46 s | 11.38 s | 0 % |

Throughput peaks around **1000 VUs (~380 req/s)** and *falls* at 2000 while
latency roughly doubles — the classic saturation knee. Past this point more VUs
only queue; they do not buy throughput.

Before adding VUs, remove the known bottlenecks:

```bash
DB_POOL_SIZE=50 SERVER_TOMCAT_THREADS_MAX=1000 ./gradlew bootRun
```

Also run the backend **without the debugger** and without
`-XX:TieredStopAtLevel=1` (IntelliJ's debug config used C1-only JIT here), and
run k6 from another machine for a large test — on this 8-core laptop k6, Spring,
Postgres and the browser share the same CPU.

The `run.sh` wrapper is equivalent to:

```bash
docker compose --profile run run --rm k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --summary-export=/results/catalog-summary.json \
  /scripts/catalog.js
```

With a locally installed k6, skip Docker entirely:

```bash
BASE_URL=http://localhost:38133 k6 run catalog.js
```

## Target

`BASE_URL` defaults to the Spring backend directly: `http://localhost:38133`.
That measures the API, not the Angular dev server. To exercise the whole stack
through Nginx use `BASE_URL=http://localhost` (the Angular proxy then adds its
own overhead to the numbers).

The k6 container uses `network_mode: host` on Linux because Docker's bridge
cannot reach host-local ports when UFW is enabled. On macOS/Windows run k6
locally (or point `BASE_URL` at a published port).

## Thresholds (the run fails when they are breached)

| Profile | Failed requests | Latency | Checks |
|---|---|---|---|
| `smoke.js` | `rate == 0` | `p(95) < 300 ms` | `rate == 1` |
| `catalog.js` | `rate < 1 %` | `p(95) < 500 ms` | `rate > 99 %` |
| `checkout.js` | `rate < 2 %` | `p(95) < 600 ms` | `rate > 98 %` |
| `orders.js` | `rate < 5 %` | `p(95) < 3000 ms` (place-order) | `rate > 95 %` |

The 500 ms catalogue budget is deliberately generous for a laptop running
Postgres + Spring + Angular; tighten it for a real deployment.

## Baseline (local dev machine, 50 VUs)

| Metric | Value |
|---|---|
| HTTP requests | 8,408 in 81 s (≈ 104 req/s) |
| Failed requests | 0 % |
| `http_req_duration` avg / p90 / p95 / max | 19 ms / 61 ms / **76 ms** / 524 ms |
| Checks | 8,408 / 8,408 (100 %) |

## Dashboard

The provisioned dashboard (`k6-load-testing`) shows:

- Virtual Users, Requests per Second, Errors per Second, Checks per Second
- `http_req_duration` mean/median/p90/p95/max + time series and heatmap
- `http_req_blocked` (connection/queue wait)
- a `Measurement` filter (defaults to `http_req_duration` + `http_req_blocked`)

Metrics go to InfluxDB database `k6`; the Grafana datasource is `k6-InfluxDB`
(uid `k6-influx`).

## CI

`smoke.js` is the CI-friendly gate (3 s, strict thresholds):

```bash
BASE_URL=http://localhost:38133 k6 run --summary-export=results/ci-summary.json smoke.js
```

The dependency-free fallback remains `e-commerce/scripts/load-smoke.sh`.
