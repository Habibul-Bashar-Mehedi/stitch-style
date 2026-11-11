import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '20s', target: 20 },
    { duration: '40s', target: 50 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:18080';

export default function () {
  const home = http.get(`${base}/api/v1/public/home`);
  check(home, { 'home 200': (r) => r.status === 200 });
  const products = http.get(`${base}/api/v1/public/categories/men/products?size=12`);
  check(products, { 'products 200': (r) => r.status === 200 });
  sleep(1);
}
