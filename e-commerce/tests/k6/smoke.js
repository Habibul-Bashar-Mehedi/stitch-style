import http from 'k6/http';
import { check } from 'k6';

/**
 * Quick sanity profile: 1 VU, 20 iterations. Use it before a full ramp to prove
 * the target is up and the thresholds pass.
 *
 *   BASE_URL=http://localhost:38133 k6 run smoke.js
 */
const base = __ENV.BASE_URL || 'http://localhost:38133';

export const options = {
  vus: 1,
  iterations: 20,
  thresholds: {
    http_req_failed: ['rate==0'],
    http_req_duration: ['p(95)<300'],
    checks: ['rate==1'],
  },
};

export default function () {
  const home = http.get(`${base}/api/v1/public/home`);
  check(home, { 'home 200': (r) => r.status === 200 });

  const products = http.get(`${base}/api/v1/public/products?size=5`);
  check(products, {
    'products 200': (r) => r.status === 200,
    'products have content': (r) => (r.json().content || []).length > 0,
  });
}
