import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  base,
  devLogin,
  forwardedHeader,
  jsonHeaders,
  loadCatalogue,
  pickVariant,
  rampStages,
  xsrfToken,
} from './lib/common.js';

/**
 * Cart load profile.
 *
 *   MODE=guest    (default) POST /public/cart/price — read-only, no session.
 *   MODE=customer PUT/GET/DELETE /me/cart          — persistent cart, needs
 *                                                    DEV_TOOLS_ENABLED=true.
 *
 *   ./run.sh cart
 *   MODE=customer VUS_MAX=500 RAMP=20 HOLD=20 ./run.sh cart
 */
const MODE = (__ENV.MODE || 'guest').toLowerCase();
const THINK_TIME = Number(__ENV.THINK_TIME || 1);
const { vusMax, stages } = rampStages();

if (MODE !== 'guest' && MODE !== 'customer') {
  throw new Error(`MODE must be "guest" or "customer" (got "${MODE}")`);
}

export const options = {
  scenarios: {
    cart: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages,
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{name:cart-price}': ['p(95)<800'],
    checks: ['rate>0.98'],
  },
};

export function setup() {
  if (MODE === 'customer') {
    const probe = devLogin('loadtest-cart-probe@example.com', 'CUSTOMER', 'Cart probe');
    if (probe.status !== 200) {
      throw new Error(
        `MODE=customer requires DEV_TOOLS_ENABLED=true on the backend (probe got ${probe.status}). ` +
          'Use MODE=guest, or restart the backend with dev-tools enabled.',
      );
    }
  }
  const catalogue = loadCatalogue();
  if (!catalogue.variantIds.length) throw new Error('No in-stock variants — seed the database first.');
  return catalogue;
}

export default function (data) {
  if (MODE === 'customer') {
    // k6 resets the VU cookie jar between iterations, so sign in each time.
    const login = devLogin(`loadtest-cart-${__VU}@example.com`, 'CUSTOMER', `Cart VU ${__VU}`);
    check(login, { 'customer signed in': (r) => r.status === 200 });

    const token = xsrfToken();
    const variantId = pickVariant(data.variantIds);

    const replace = http.put(
      `${base}/api/v1/me/cart`,
      JSON.stringify({ items: [{ variantId, quantity: 2, selected: true }] }),
      {
        headers: jsonHeaders({ 'X-XSRF-TOKEN': token }),
        tags: { name: 'cart-replace' },
      },
    );
    check(replace, { 'cart replace 200': (r) => r.status === 200 });

    const cart = http.get(`${base}/api/v1/me/cart`, { tags: { name: 'cart-get' } });
    check(cart, { 'cart get 200': (r) => r.status === 200 });

    const remove = http.del(`${base}/api/v1/me/cart/${variantId}`, null, {
      headers: { 'X-XSRF-TOKEN': token },
      tags: { name: 'cart-remove' },
    });
    check(remove, { 'cart remove 200': (r) => r.status === 200 });
  }

  const token = xsrfToken();
  const lines = [];
  const count = 1 + Math.floor(Math.random() * 2);
  for (let i = 0; i < count; i++) {
    lines.push({ variantId: pickVariant(data.variantIds), quantity: 1, selected: true });
  }

  const price = http.post(
    `${base}/api/v1/public/cart/price`,
    JSON.stringify({ items: lines }),
    {
      headers: jsonHeaders({ 'X-XSRF-TOKEN': token, ...forwardedHeader(__VU, __ITER) }),
      tags: { name: 'cart-price' },
    },
  );
  check(price, { 'cart price 200': (r) => r.status === 200 });

  sleep(THINK_TIME);
}
