# Gatling load tests — Stitch & Style

Java-DSL Gatling project, mirroring the k6 profiles under `../k6/`.
Simulations live in `src/test/java/com/stitchstyle/gatling/simulations/`.

```
tests/gatling/
├── pom.xml
└── src/test/java/com/stitchstyle/gatling/
    ├── Config.java              env-var / system-property config
    ├── Catalogue.java           loads areas + in-stock variants into a feeder
    ├── Protocols.java           baseUrl, JSON, shared connections
    ├── Journeys.java            reusable request chains (CSRF, order, payment...)
    └── simulations/
        ├── CatalogueSimulation.java   read-only browse + cart pricing
        ├── CartSimulation.java        persistent /me/cart (dev-tools)
        ├── CheckoutSimulation.java    COD checkout + tracking (writes)
        ├── PaymentSimulation.java     prepaid -> callback -> PAID (writes)
        ├── DashboardSimulation.java   admin analytics/lists (dev-tools)
        └── StressSimulation.java      stepped 500 -> 1000 -> 2000 (read-only)
```

## Prerequisites

- A **full JDK** (not a JRE-only install) — Gatling compiles the simulations.
  On this machine `/usr/lib/jvm/java-25-openjdk-amd64` has no `javac`, so use 21:

  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ```

- Maven 3.6.3+ (`mvn -v`).

## Run

`run.sh` picks a full JDK (one with `javac`) for you, then delegates to Maven:

```bash
cd e-commerce/tests/gatling

# read-only browse + cart pricing at 500 concurrent users
VUS=500 RAMP=20 HOLD=20 \
  ./run.sh com.stitchstyle.gatling.simulations.CatalogueSimulation
```

Raw Maven equivalent (export a **full** JDK first — the default `java 25` on this
machine is JRE-only):

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
VUS=500 mvn gatling:test -Dgatling.simulationClass=com.stitchstyle.gatling.simulations.CatalogueSimulation
```

Open the HTML report printed at the end:

```
target/gatling/cataloguesimulation-<timestamp>/index.html
```

### Simulations

| Simulation | What it does | Writes? | Needs dev-tools |
|---|---|---|---|
| `CatalogueSimulation` | home, product list, cart pricing | no | no |
| `CartSimulation` | `PUT/GET/DELETE /me/cart` | carts | **yes** |
| `CheckoutSimulation` | COD order + tracking | orders, stock | no |
| `PaymentSimulation` | prepaid order → gateway → callback → `PAID` | orders, payments, stock | no |
| `DashboardSimulation` | analytics summary + admin lists | no | **yes** |
| `StressSimulation` | 500 → 1000 → 2000 VUs, read-only | no | no |

```bash
# stress
LEVELS=500,1000,2000 RAMP=10 HOLD=40 \
  ./run.sh com.stitchstyle.gatling.simulations.StressSimulation

# COD checkout (restock first — see ../k6/README.md)
VUS=500 RAMP=20 HOLD=20 \
  ./run.sh com.stitchstyle.gatling.simulations.CheckoutSimulation

# prepaid payment
PAYMENT_PROVIDER=BKASH VUS=500 \
  ./run.sh com.stitchstyle.gatling.simulations.PaymentSimulation

# admin dashboard + persistent cart (backend must run with DEV_TOOLS_ENABLED=true)
VUS=500 ./run.sh com.stitchstyle.gatling.simulations.DashboardSimulation
MODE=customer VUS=500 ./run.sh com.stitchstyle.gatling.simulations.CartSimulation
```

## Configuration

Environment variable first, JVM system property second (`VUS=500` or `-Dvus=500`).

| Variable | Property | Default | Meaning |
|---|---|---|---|
| `BASE_URL` | `baseUrl` | `http://localhost:38133` | target |
| `VUS` | `vus` | `500` | concurrent users |
| `RAMP` | `ramp` | `20` | seconds to reach `VUS` |
| `HOLD` | `hold` | `20` | seconds at `VUS` |
| `THINK` | `think` | `1` | pause between iterations (s) |
| `MODE` | `mode` | `guest` | `customer` enables `CartSimulation` |
| `PAYMENT_PROVIDER` | `paymentProvider` | `BKASH` | `BKASH` or `SSLCOMMERZ` |
| `CALLBACK_SECRET` | `callbackSecret` | `dev-callback-secret` | stub callback signature |
| `ADMIN_EMAIL` | `adminEmail` | `loadtest-admin@example.com` | dashboard login |
| `LEVELS` | `levels` | `500,1000,2000` | stress steps |
| `SPOOF` | — | `true` | unique `Forwarded` IP per order |

## Notes that cost time

- **CSRF**: the token may be issued on the first response of any request, so
  `getCookieValue(CookieKey("XSRF-TOKEN"))` reads it from the jar instead of
  parsing the `/csrf` response.
- **Order limit**: `POST /public/orders` is 5/min per IP. The simulations send a
  unique `Forwarded: for=10.x.x.x` per order; set `SPOOF=false` to test the
  limiter itself.
- **Payment stub**: with `BKASH_ENABLED=false` / `SSLCOMMERZ_ENABLED=false` the
  gateways return `/pay/stub?...&txn=...` and accept
  `signature=dev-callback-secret`, so the whole PAID path runs locally.
- **Dashboard / persistent cart** need `DEV_TOOLS_ENABLED=true`; Google OIDC
  cannot be automated.
- Running `mvn gatling:test` with **no** `-Dgatling.simulationClass` executes
  **all** simulations, including the write-heavy ones. Always pass the class.

## Measured on this machine

| Run | Shape | Requests | KO | mean | p95 | Throughput |
|---|---|---|---|---|---|---|
| `CatalogueSimulation` | 500 VUs, 20/20/20 s | 15,576 | 0 | 1.02 s | 3.25 s | 255 req/s |
| `StressSimulation` | 500 → 1000, 10/20/10 s per level | 21,740 | 0 | 1.82 s | 3.97 s | 306 req/s |

This matches the k6 curve (`tests/k6/stress.js`): the backend saturates around
1000 VUs with the default Hikari pool of 10 and Tomcat's 200 threads. Run with
`DB_POOL_SIZE=50 SERVER_TOMCAT_THREADS_MAX=1000` (and no debugger) to move the
knee before increasing VUs further.
