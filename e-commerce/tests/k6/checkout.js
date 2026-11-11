import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '30s', target: 20 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
  },
};

const base = __ENV.BASE_URL || 'http://localhost:18080';

export default function () {
  const jar = http.cookieJar();
  const csrf = http.get(`${base}/api/v1/public/csrf`);
  const token = csrf.cookies['XSRF-TOKEN']?.[0]?.value;
  const products = http.get(`${base}/api/v1/public/products?size=12`).json();
  const product = products.content[randomIntBetween(0, products.content.length - 1)];
  const variant = product.variants.find((v) => v.available > 0);
  if (!variant) return;
  const price = http.post(`${base}/api/v1/public/cart/price`,
    JSON.stringify({ items: [{ variantId: variant.id, quantity: 1, selected: true }] }),
    { headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': token } });
  check(price, { 'price 200': (r) => r.status === 200 });
  sleep(1);
}
