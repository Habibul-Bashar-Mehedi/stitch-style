import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  base,
  forwardedHeader,
  jsonHeaders,
  loadCatalogue,
  pickVariant,
  rampStages,
  xsrfToken,
} from './lib/common.js';

/**
 * Prepaid checkout + payment callback, end to end.
 *
 * The bKash and SSLCommerz gateways run in stub mode while disabled (the
 * default), so this exercises the real order -> payment -> callback -> PAID
 * path without calling any external provider.
 *
 *   ./run.sh payment
 *   PAYMENT_PROVIDER=SSLCOMMERZ VUS_MAX=500 RAMP=20 HOLD=20 ./run.sh payment
 *
 * WARNING: writes real orders and payments, and moves real stock.
 */
const PROVIDER = (__ENV.PAYMENT_PROVIDER || 'BKASH').toUpperCase();
const CALLBACK_SECRET = __ENV.CALLBACK_SECRET || 'dev-callback-secret';
const THINK_TIME = Number(__ENV.THINK_TIME || 1);
const { vusMax, stages } = rampStages();

if (!['BKASH', 'SSLCOMMERZ'].includes(PROVIDER)) {
  throw new Error(`PAYMENT_PROVIDER must be BKASH or SSLCOMMERZ (got "${PROVIDER}")`);
}

export const options = {
  scenarios: {
    payment: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages,
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{name:place-order}': ['p(95)<3000'],
    'http_req_duration{name:payment-callback}': ['p(95)<2000'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  const catalogue = loadCatalogue();
  if (!catalogue.areaId) throw new Error('No delivery areas — seed the database first.');
  if (!catalogue.variantIds.length) throw new Error('No in-stock variants — restock before the test.');
  return catalogue;
}

function phoneForVu() {
  return `017${String(10000000 + __VU).slice(-8)}`;
}

function txnFromRedirect(redirectUrl) {
  const match = /[?&]txn=([^&]+)/.exec(redirectUrl || '');
  return match ? decodeURIComponent(match[1]) : null;
}

export default function (data) {
  const token = xsrfToken();
  const variantId = pickVariant(data.variantIds);

  const order = http.post(
    `${base}/api/v1/public/orders`,
    JSON.stringify({
      customer: { name: `Payment Test ${__VU}`, phone: phoneForVu() },
      shippingAddress: `House ${__VU}, Road 1, Dhaka`,
      deliveryAreaId: data.areaId,
      paymentMethod: PROVIDER,
      note: 'k6 payment test',
      items: [{ variantId, quantity: 1, selected: true }],
    }),
    {
      headers: jsonHeaders({
        'X-XSRF-TOKEN': token,
        'Idempotency-Key': `k6-pay-${__VU}-${__ITER}-${Date.now()}`,
        ...forwardedHeader(__VU, __ITER),
      }),
      tags: { name: 'place-order' },
    },
  );

  const created = check(order, {
    'order created': (r) => r.status === 201 || r.status === 200,
    'has redirect + token': (r) => {
      if (r.status !== 201 && r.status !== 200) return false;
      const b = r.json() || {};
      return Boolean(b.publicToken && b.redirectUrl);
    },
  });
  if (!created) {
    sleep(THINK_TIME);
    return;
  }

  const publicToken = order.json('publicToken');
  const amount = order.json('grandTotal');
  const txid = txnFromRedirect(order.json('redirectUrl'));

  const callbackUrl =
    `${base}/api/v1/public/payments/${PROVIDER}/callback` +
    `?signature=${encodeURIComponent(CALLBACK_SECRET)}` +
    `&status=success` +
    `&providerTxnId=${encodeURIComponent(txid || '')}` +
    `&amount=${encodeURIComponent(amount)}` +
    `&currency=BDT`;

  const callback = http.post(callbackUrl, null, { tags: { name: 'payment-callback' } });
  check(callback, { 'callback 200': (r) => r.status === 200 });

  const track = http.get(`${base}/api/v1/public/orders/track/${publicToken}`, {
    tags: { name: 'track-order' },
  });
  check(track, {
    'track 200': (r) => r.status === 200,
    'payment PAID': (r) => r.status === 200 && r.json('paymentStatus') === 'PAID',
  });

  sleep(THINK_TIME);
}
