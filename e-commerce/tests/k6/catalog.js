import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Stitch & Style catalogue load profile.
 *
 * Read-only traffic against the public API: home aggregate, a collection page,
 * a product detail and the category tree. Safe to run against the dev database
 * (it never writes).
 *
 *   BASE_URL=http://localhost:38133 k6 run catalog.js
 *   docker compose run --rm k6 run --out influxdb=http://localhost:8086/k6 /scripts/catalog.js
 *
 * Override the ramp with VUS_MAX / RAMP:
 *   VUS_MAX=100 RAMP=60 k6 run catalog.js
 */
const base = __ENV.BASE_URL || 'http://localhost:38133';
const vusMax = Number(__ENV.VUS_MAX || 50);
const ramp = Number(__ENV.RAMP || 20);

export const options = {
  stages: [
    { duration: `${ramp}s`, target: Math.max(1, Math.floor(vusMax / 2)) },
    { duration: `${Math.max(10, ramp * 2)}s`, target: vusMax },
    { duration: `${ramp}s`, target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    // A local dev laptop running Postgres + Spring + ng serve: keep this
    // generous. Tighten to 300 ms against a real deployment.
    http_req_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const home = http.get(`${base}/api/v1/public/home`, { tags: { name: 'home' } });
  check(home, { 'home 200': (r) => r.status === 200 });

  const tree = http.get(`${base}/api/v1/public/categories/tree`, { tags: { name: 'category-tree' } });
  check(tree, { 'tree 200': (r) => r.status === 200 });

  const products = http.get(`${base}/api/v1/public/categories/men/products?size=12`, {
    tags: { name: 'collection-products' },
  });
  check(products, { 'products 200': (r) => r.status === 200 });

  const body = products.json();
  const items = (body && body.content) || [];
  if (items.length > 0) {
    const product = items[Math.floor(Math.random() * items.length)];
    const detail = http.get(`${base}/api/v1/public/products/${product.slug}`, {
      tags: { name: 'product-detail' },
    });
    check(detail, { 'detail 200': (r) => r.status === 200 });
  }

  sleep(1);
}
