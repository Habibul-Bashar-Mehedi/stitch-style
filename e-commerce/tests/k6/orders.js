import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Full customer journey under load: browse -> price cart -> place order -> track.
 *
 *   ./run.sh orders                                   # 500 VUs, guest checkout
 *   MODE=customer ./run.sh orders                     # 500 signed-in customers
 *   VUS_MAX=500 RAMP=60 HOLD=120 ./run.sh orders      # explicit timings
 *
 * MODE=customer needs DEV_TOOLS_ENABLED=true on the backend (the dev-session
 * endpoint replaces Google OIDC for tests). MODE=guest is the default and works
 * against the normal dev backend.
 *
 * WARNING: every iteration writes a real order and decrements real stock.
 * Run it against a disposable database, or restock first (see README).
 */
const base = (__ENV.BASE_URL || 'http://localhost:38133').replace(/\/$/, '');
const VUS_MAX = Number(__ENV.VUS_MAX || 500);
const RAMP = Number(__ENV.RAMP || 60);
const HOLD = Number(__ENV.HOLD || 120);
const MODE = (__ENV.MODE || 'guest').toLowerCase();
const PAYMENT_METHOD = (__ENV.PAYMENT_METHOD || 'COD').toUpperCase();
const THINK_TIME = Number(__ENV.THINK_TIME || 1);
// POST /public/orders is limited to 5/min per client IP. The load test is about
// the order pipeline, not the anti-abuse limiter, so emulate a distinct client
// IP per order (a new shopper each time). Without this, each VU hits 429 after
// its fifth order in a minute.
// Set SPOOF_FORWARDED=false to load-test the limiter itself instead.
const SPOOF_FORWARDED = (__ENV.SPOOF_FORWARDED || 'true') !== 'false';

if (MODE !== 'guest' && MODE !== 'customer') {
  throw new Error(`MODE must be "guest" or "customer" (got "${MODE}")`);
}

export const options = {
  scenarios: {
    orders: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${RAMP}s`, target: VUS_MAX },
        { duration: `${HOLD}s`, target: VUS_MAX },
        { duration: `${RAMP}s`, target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // A local Postgres + Spring stack under 500 VUs is expected to show some
    // 5xx/429. Tighten to 0.01 on a real deployment.
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:place-order}': ['p(95)<3000'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  if (MODE === 'customer') {
    const probe = http.post(
      `${base}/api/v1/dev/session`,
      JSON.stringify({ email: 'loadtest-probe@example.com', role: 'CUSTOMER' }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'dev-login' } },
    );
    if (probe.status !== 200) {
      throw new Error(
        `MODE=customer requires DEV_TOOLS_ENABLED=true on the backend (probe got ${probe.status}). ` +
          'Restart the backend with that flag, or use MODE=guest.',
      );
    }
  }

  const areas = http.get(`${base}/api/v1/public/delivery-areas`).json();
  const products = http.get(`${base}/api/v1/public/products?size=100`).json();

  const variantIds = [];
  for (const product of (products && products.content) || []) {
    for (const variant of product.variants || []) {
      if (variant.active && variant.available > 0) variantIds.push(variant.id);
    }
  }

  if (!Array.isArray(areas) || areas.length === 0) {
    throw new Error('No delivery areas — is the backend seeded? (scripts/demo-data.sql)');
  }
  if (variantIds.length === 0) {
    throw new Error('No in-stock variants — restock before running the order profile (see README).');
  }

  return { areaId: areas[0].id, variantIds };
}

function phoneForVu() {
  return `017${String(10000000 + __VU).slice(-8)}`;
}

function xsrfToken() {
  // Any first response may already carry XSRF-TOKEN (CsrfFilter saves it on the
  // first request), in which case /csrf returns 204 with no Set-Cookie. Always
  // read the value from the VU cookie jar, never from this response.
  http.get(`${base}/api/v1/public/csrf`, { tags: { name: 'csrf' } });
  const cookies = http.cookieJar().cookiesForURL(base);
  const cookie = cookies['XSRF-TOKEN'];
  return cookie && cookie.length ? cookie[0] : null;
}

export default function (data) {
  if (MODE === 'customer') {
    // k6 resets the VU cookie jar between iterations, so sign in each time.
    const login = http.post(
      `${base}/api/v1/dev/session`,
      JSON.stringify({
        email: `loadtest-vu-${__VU}@example.com`,
        role: 'CUSTOMER',
        fullName: `Load VU ${__VU}`,
      }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'dev-login' } },
    );
    check(login, { 'signed in': (r) => r.status === 200 });
  }

  const home = http.get(`${base}/api/v1/public/home`, { tags: { name: 'home' } });
  check(home, { 'home 200': (r) => r.status === 200 });

  if (MODE === 'customer') {
    const me = http.get(`${base}/api/v1/me`, { tags: { name: 'me' } });
    check(me, { 'me 200': (r) => r.status === 200 });
  }

  const token = xsrfToken();
  const variantId = data.variantIds[Math.floor(Math.random() * data.variantIds.length)];

  const price = http.post(
    `${base}/api/v1/public/cart/price`,
    JSON.stringify({ items: [{ variantId, quantity: 1, selected: true }] }),
    {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
      tags: { name: 'cart-price' },
    },
  );
  check(price, { 'cart priced': (r) => r.status === 200 });

  const order = http.post(
    `${base}/api/v1/public/orders`,
    JSON.stringify({
      customer: { name: `Load Test ${__VU}`, phone: phoneForVu() },
      shippingAddress: `House ${__VU}, Road 1, Dhaka`,
      deliveryAreaId: data.areaId,
      paymentMethod: PAYMENT_METHOD,
      note: 'k6 load test',
      items: [{ variantId, quantity: 1, selected: true }],
    }),
    {
      headers: Object.assign(
        {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': token,
          'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}`,
        },
        SPOOF_FORWARDED
          ? {
              Forwarded:
                `for=10.${Math.floor(__VU / 250)}.${__VU % 250}.${(__ITER % 250) + 1}`,
            }
          : {},
      ),
      tags: { name: 'place-order' },
    },
  );
  check(order, {
    'order created': (r) => r.status === 201 || r.status === 200,
    'order has public token': (r) => {
      if (r.status !== 201 && r.status !== 200) return false;
      return Boolean((r.json() || {}).publicToken);
    },
  });

  if (order.status === 201 || order.status === 200) {
    const publicToken = order.json('publicToken');
    const track = http.get(`${base}/api/v1/public/orders/track/${publicToken}`, {
      tags: { name: 'track-order' },
    });
    check(track, { 'track 200': (r) => r.status === 200 });
  }

  sleep(THINK_TIME);
}
