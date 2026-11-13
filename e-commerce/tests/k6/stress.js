import http from 'k6/http';
import { check, sleep } from 'k6';
import { base, loadCatalogue, pickVariant, xsrfToken } from './lib/common.js';

/**
 * Stepped stress test, read-only: browse + price a cart at increasing
 * concurrency. Each level is a separate k6 scenario, so every metric carries a
 * `level` tag and the dashboard/results can be compared per step.
 *
 * Default: 500 -> 1000 -> 2000 VUs, 10 s ramp / 40 s hold / 10 s down each.
 *
 *   ./run.sh stress
 *   LEVELS=500,1000,2000,3000 RAMP=10 HOLD=30 ./run.sh stress
 *
 * Read-only on purpose: we want the capacity curve before adding write load.
 */
const LEVELS = (__ENV.LEVELS || '500,1000,2000')
  .split(',')
  .map((v) => Number(v.trim()))
  .filter((v) => v > 0);
const RAMP = Number(__ENV.RAMP || 10);
const HOLD = Number(__ENV.HOLD || 40);
const THINK_TIME = Number(__ENV.THINK_TIME || 0.2);
const LEVEL_SECONDS = RAMP + HOLD + RAMP;

if (!LEVELS.length) throw new Error('LEVELS must contain at least one number');

const scenarios = {};
LEVELS.forEach((level, i) => {
  scenarios[`level_${level}`] = {
    executor: 'ramping-vus',
    exec: 'journey',
    startTime: `${i * (LEVEL_SECONDS + 5)}s`,
    startVUs: 0,
    stages: [
      { duration: `${RAMP}s`, target: level },
      { duration: `${HOLD}s`, target: level },
      { duration: `${RAMP}s`, target: 0 },
    ],
    gracefulRampDown: '10s',
    tags: { level: String(level) },
  };
});

export const options = {
  scenarios,
  // Informational: the run is allowed to show degradation, that is the point.
  thresholds: {
    http_req_failed: ['rate<0.20'],
  },
};

export function setup() {
  const catalogue = loadCatalogue();
  if (!catalogue.variantIds.length) throw new Error('No in-stock variants — seed the database first.');
  return catalogue;
}

export function journey(data) {
  const home = http.get(`${base}/api/v1/public/home`, { tags: { name: 'home' } });
  check(home, { 'home 200': (r) => r.status === 200 });

  const products = http.get(`${base}/api/v1/public/products?size=12`, {
    tags: { name: 'products' },
  });
  check(products, { 'products 200': (r) => r.status === 200 });

  const token = xsrfToken();
  const price = http.post(
    `${base}/api/v1/public/cart/price`,
    JSON.stringify({ items: [{ variantId: pickVariant(data.variantIds), quantity: 1, selected: true }] }),
    {
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token },
      tags: { name: 'cart-price' },
    },
  );
  check(price, { 'cart priced': (r) => r.status === 200 });

  sleep(THINK_TIME);
}
