import http from 'k6/http';

/** Shared helpers for the storefront/admin load profiles. */

export const base = (__ENV.BASE_URL || 'http://localhost:38133').replace(/\/$/, '');

export function jsonHeaders(extra) {
  return Object.assign({ 'Content-Type': 'application/json' }, extra || {});
}

/** Test-only sign-in (requires DEV_TOOLS_ENABLED=true on the backend). */
export function devLogin(email, role, fullName) {
  return http.post(`${base}/api/v1/dev/session`, JSON.stringify({ email, role, fullName }), {
    headers: jsonHeaders(),
    tags: { name: 'dev-login' },
  });
}

/**
 * The XSRF-TOKEN cookie may be set by ANY first response (the CSRF filter saves
 * it on the first request), so /csrf can return 204 with no Set-Cookie. Read it
 * from the VU cookie jar, not from the /csrf response.
 */
export function xsrfToken() {
  http.get(`${base}/api/v1/public/csrf`, { tags: { name: 'csrf' } });
  const cookie = http.cookieJar().cookiesForURL(base)['XSRF-TOKEN'];
  return cookie && cookie.length ? cookie[0] : null;
}

/** Unique emulated client IP per request, so the per-IP order limit does not bite. */
export function forwardedHeader(vu, iter) {
  if ((__ENV.SPOOF_FORWARDED || 'true') === 'false') return {};
  return { Forwarded: `for=10.${Math.floor(vu / 250)}.${vu % 250}.${(iter % 250) + 1}` };
}

/** In-stock variants + an active delivery area, fetched once in setup(). */
export function loadCatalogue() {
  const areas = http.get(`${base}/api/v1/public/delivery-areas`).json();
  const products = http.get(`${base}/api/v1/public/products?size=100`).json();

  const variantIds = [];
  for (const product of (products && products.content) || []) {
    for (const variant of product.variants || []) {
      if (variant.active && variant.available > 0) variantIds.push(variant.id);
    }
  }

  return {
    areaId: Array.isArray(areas) && areas.length ? areas[0].id : null,
    variantIds,
  };
}

export function pickVariant(variantIds) {
  return variantIds[Math.floor(Math.random() * variantIds.length)];
}

/** Base ramp used by every profile: RAMP up, HOLD at VUS_MAX, RAMP down. */
export function rampStages() {
  const vusMax = Number(__ENV.VUS_MAX || 500);
  const ramp = Number(__ENV.RAMP || 60);
  const hold = Number(__ENV.HOLD || 120);
  return {
    vusMax,
    stages: [
      { duration: `${ramp}s`, target: vusMax },
      { duration: `${hold}s`, target: vusMax },
      { duration: `${ramp}s`, target: 0 },
    ],
  };
}
