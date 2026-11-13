import http from 'k6/http';
import { check, sleep } from 'k6';
import { base, devLogin, rampStages } from './lib/common.js';

/**
 * Admin dashboard load profile.
 *
 * The dashboard's first screen is GET /admin/analytics/summary, which runs
 * several aggregate queries over orders (count, revenue, top products, status
 * breakdown, SLA). We also hit the board's order and product lists.
 *
 * Requires DEV_TOOLS_ENABLED=true on the backend, because the real sign-in is
 * Google OIDC and cannot be automated.
 *
 *   DEV_TOOLS_ENABLED=true ./gradlew bootRun
 *   ./run.sh dashboard
 */
const THINK_TIME = Number(__ENV.THINK_TIME || 1);
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'loadtest-admin@example.com';
const { vusMax, stages } = rampStages();

export const options = {
  scenarios: {
    dashboard: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages,
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{name:analytics-summary}': ['p(95)<2000'],
    checks: ['rate>0.98'],
  },
};

export function setup() {
  const probe = devLogin(ADMIN_EMAIL, 'ADMIN', 'Load test admin');
  if (probe.status !== 200) {
    throw new Error(
      `dashboard requires DEV_TOOLS_ENABLED=true on the backend (probe got ${probe.status}). ` +
        'Restart the backend with dev-tools enabled, then re-run.',
    );
  }
  return { ready: true };
}

export default function () {
  // k6 resets the VU cookie jar between iterations, so sign in each time.
  const login = devLogin(ADMIN_EMAIL, 'ADMIN', 'Load test admin');
  check(login, { 'admin signed in': (r) => r.status === 200 });

  const summary = http.get(`${base}/api/v1/admin/analytics/summary`, {
    tags: { name: 'analytics-summary' },
  });
  check(summary, { 'analytics 200': (r) => r.status === 200 });

  const orders = http.get(`${base}/api/v1/admin/orders?size=20`, {
    tags: { name: 'admin-orders' },
  });
  check(orders, { 'orders 200': (r) => r.status === 200 });

  const products = http.get(`${base}/api/v1/admin/products?size=20`, {
    tags: { name: 'admin-products' },
  });
  check(products, { 'products 200': (r) => r.status === 200 });

  const notices = http.get(`${base}/api/v1/admin/notices`, {
    tags: { name: 'admin-notices' },
  });
  check(notices, { 'notices 200': (r) => r.status === 200 });

  sleep(THINK_TIME);
}
