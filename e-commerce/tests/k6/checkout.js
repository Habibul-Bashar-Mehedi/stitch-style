import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

/**
 * Cart-pricing profile. POST /api/v1/public/cart/price re-prices a cart on the
 * server but does not persist anything, so this is still read-only for the DB.
 *
 *   BASE_URL=http://localhost:38133 k6 run checkout.js
 */
const base = __ENV.BASE_URL || 'http://localhost:38133';
const vusMax = Number(__ENV.VUS_MAX || 20);

export const options = {
  stages: [
    { duration: '10s', target: 5 },
    { duration: '20s', target: vusMax },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<600'],
    checks: ['rate>0.98'],
  },
};

export default function () {
  const csrf = http.get(`${base}/api/v1/public/csrf`, { tags: { name: 'csrf' } });
  const token = csrf.cookies['XSRF-TOKEN']?.[0]?.value;

  const products = http.get(`${base}/api/v1/public/products?size=12`, { tags: { name: 'products' } });
  const body = products.json();
  const items = (body && body.content) || [];
  const withStock = items.filter((p) => (p.variants || []).some((v) => v.available > 0));
  if (withStock.length === 0) {
    return;
  }
  const product = withStock[randomIntBetween(0, withStock.length - 1)];
  const variant = product.variants.find((v) => v.available > 0);

  const price = http.post(
    `${base}/api/v1/public/cart/price`,
    JSON.stringify({ items: [{ variantId: variant.id, quantity: 1, selected: true }] }),
    {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
      tags: { name: 'cart-price' },
    },
  );
  check(price, { 'price 200': (r) => r.status === 200 });
  sleep(1);
}
