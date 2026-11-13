# Stitch & Style — API endpoint list

Generated from the controllers in `backend/src/main/java/com/stitchstyle/app/controller`.
**105 controller endpoints** plus OAuth, WebSocket, Actuator and static routes.

- Direct backend: `http://localhost:38133`
- Through Nginx / Docker: `http://localhost` (same paths)
- Interactive docs (ADMIN session): `http://localhost/swagger-ui/index.html`

Auth is a server-side session cookie (`STITCHSTYLE_SESSION`) created by Google
OIDC. Storefront routes are public; `/api/v1/me/**` needs a customer session;
`/api/v1/admin/**` needs ADMIN (or MANAGER where noted). State-changing calls
need the `X-XSRF-TOKEN` header from `GET /api/v1/public/csrf` (payment callbacks
and `/api/v1/dev/**` are exempt). Errors are RFC 9457 `application/problem+json`.

## Storefront (public)

| Method | Endpoint | Working name | Access |
|---|---|---|---|
| `POST` | `/api/v1/public/cart/price` | Price | public |
| `GET` | `/api/v1/public/categories/tree` | Tree | public |
| `GET` | `/api/v1/public/categories/{slug}` | Category | public |
| `GET` | `/api/v1/public/categories/{slug}/products` | Category Products | public |
| `POST` | `/api/v1/public/complaints` | Create | public |
| `POST` | `/api/v1/public/coupons/validate` | Validate | public |
| `GET` | `/api/v1/public/csrf` | Csrf | public |
| `GET` | `/api/v1/public/delivery-areas` | Delivery Areas | public |
| `GET` | `/api/v1/public/home` | Home | public |
| `POST` | `/api/v1/public/orders` | Place | public |
| `POST` | `/api/v1/public/orders/track` | Track By Phone | public |
| `GET` | `/api/v1/public/orders/track/{publicToken}` | Track | public |
| `GET` | `/api/v1/public/payment-methods` | Payment Methods | public |
| `POST` | `/api/v1/public/payments/{provider}/callback` | Callback | public |
| `GET` | `/api/v1/public/products` | Products | public |
| `GET` | `/api/v1/public/products/{slug}` | Product | public |
| `GET` | `/api/v1/public/products/{slug}/related` | Related | public |
| `GET` | `/api/v1/public/products/{slug}/reviews` | Approved | public |

## Customer (`/api/v1/me`)

| Method | Endpoint | Working name | Access |
|---|---|---|---|
| `GET` | `/api/v1/me` | Me | CUSTOMER session |
| `DELETE` | `/api/v1/me/cart` | Clear Cart | CUSTOMER session |
| `GET` | `/api/v1/me/cart` | Cart | CUSTOMER session |
| `PUT` | `/api/v1/me/cart` | Replace | CUSTOMER session |
| `POST` | `/api/v1/me/cart/merge` | Merge | CUSTOMER session |
| `DELETE` | `/api/v1/me/cart/{variantId}` | Remove Cart Line | CUSTOMER session |
| `GET` | `/api/v1/me/chat/conversations` | Conversations | CUSTOMER session |
| `POST` | `/api/v1/me/chat/conversations` | Create | CUSTOMER session |
| `GET` | `/api/v1/me/chat/conversations/{id}` | Conversation | CUSTOMER session |
| `GET` | `/api/v1/me/chat/conversations/{id}/messages` | Messages | CUSTOMER session |
| `POST` | `/api/v1/me/chat/conversations/{id}/messages` | Send | CUSTOMER session |
| `POST` | `/api/v1/me/chat/conversations/{id}/read` | Read | CUSTOMER session |
| `GET` | `/api/v1/me/orders` | Orders | CUSTOMER session |
| `GET` | `/api/v1/me/orders/{orderNumber}` | Order | CUSTOMER session |
| `POST` | `/api/v1/me/orders/{orderNumber}/return-quote` | Return Quote | CUSTOMER session |
| `POST` | `/api/v1/me/orders/{orderNumber}/returns` | Request Return | CUSTOMER session |
| `POST` | `/api/v1/me/products/{slug}/reviews` | Submit | CUSTOMER session |
| `GET` | `/api/v1/me/returns` | Returns | CUSTOMER session |

## Admin (`/api/v1/admin`)

| Method | Endpoint | Working name | Access |
|---|---|---|---|
| `GET` | `/api/v1/admin/analytics/summary` | Summary | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/audit` | List | ADMIN |
| `GET` | `/api/v1/admin/banners` | List | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/banners` | Create | ADMIN / MANAGER |
| `DELETE` | `/api/v1/admin/banners/{id}` | Delete | ADMIN / MANAGER |
| `PUT` | `/api/v1/admin/banners/{id}` | Update | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/categories` | List | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/categories` | Create | ADMIN / MANAGER |
| `PUT` | `/api/v1/admin/categories/reorder` | Reorder | ADMIN / MANAGER |
| `DELETE` | `/api/v1/admin/categories/{id}` | Delete | ADMIN / MANAGER |
| `PUT` | `/api/v1/admin/categories/{id}` | Update | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/categories/{id}/visibility` | Visibility | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/chat/contacts` | Contacts | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/chat/conversations` | Conversations | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/chat/conversations/{id}` | Conversation | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/chat/conversations/{id}/assign` | Assign | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/chat/conversations/{id}/close` | Close | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/chat/conversations/{id}/messages` | Messages | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/chat/conversations/{id}/messages` | Send | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/chat/conversations/{id}/read` | Read | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/chat/internal` | Internal | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/complaints` | List | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/complaints/{id}` | Respond | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/coupons` | List | ADMIN |
| `POST` | `/api/v1/admin/coupons` | Create | ADMIN |
| `DELETE` | `/api/v1/admin/coupons/{id}` | Delete | ADMIN |
| `PUT` | `/api/v1/admin/coupons/{id}` | Update | ADMIN |
| `GET` | `/api/v1/admin/delivery-areas` | List | ADMIN |
| `POST` | `/api/v1/admin/delivery-areas` | Create | ADMIN |
| `DELETE` | `/api/v1/admin/delivery-areas/{id}` | Delete | ADMIN |
| `PUT` | `/api/v1/admin/delivery-areas/{id}` | Update | ADMIN |
| `GET` | `/api/v1/admin/media` | List | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/media` | Upload | ADMIN / MANAGER |
| `DELETE` | `/api/v1/admin/media/{id}` | Delete | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/notices` | List | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/notices` | Create | ADMIN / MANAGER |
| `DELETE` | `/api/v1/admin/notices/{id}` | Delete | ADMIN / MANAGER |
| `PUT` | `/api/v1/admin/notices/{id}` | Update | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/orders` | List | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/orders/invoices` | Invoices | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/orders/{id}` | Get | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/orders/{id}/courier` | Courier | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/orders/{id}/notifications/resend` | Resend Notification | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/orders/{id}/shipment` | Create Shipment | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/orders/{id}/status` | Status | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/products` | List | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/products` | Create | ADMIN / MANAGER |
| `DELETE` | `/api/v1/admin/products/{id}` | Delete | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/products/{id}` | Get | ADMIN / MANAGER |
| `PUT` | `/api/v1/admin/products/{id}` | Update | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/products/{id}/variants/{variantId}/stock` | Stock | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/returns` | List | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/returns/{id}` | Get | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/returns/{id}` | Advance | ADMIN / MANAGER |
| `POST` | `/api/v1/admin/returns/{id}/refund` | Refund | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/reviews` | List | ADMIN / MANAGER |
| `PATCH` | `/api/v1/admin/reviews/{id}` | Moderate | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/session` | Session | ADMIN / MANAGER |
| `GET` | `/api/v1/admin/settings` | List | ADMIN |
| `PUT` | `/api/v1/admin/settings` | Update | ADMIN |
| `GET` | `/api/v1/admin/staff` | List | ADMIN |
| `POST` | `/api/v1/admin/staff` | Create | ADMIN |
| `PUT` | `/api/v1/admin/staff/{id}` | Update | ADMIN |
| `PATCH` | `/api/v1/admin/staff/{id}/active` | Active | ADMIN |

## Developer (only when `app.dev-tools.enabled=true`)

| Method | Endpoint | Working name | Access |
|---|---|---|---|
| `POST` | `/api/v1/dev/cache/evict` | Evict | dev-tools only |
| `GET` | `/api/v1/dev/ping` | Ping | dev-tools only |
| `POST` | `/api/v1/dev/session` | Login | dev-tools only |
| `POST` | `/api/v1/dev/settings` | Setting | dev-tools only |

## Non-controller endpoints

| Method | Endpoint | Working name | Notes |
|---|---|---|---|
| `GET` | `/sitemap.xml` | Sitemap | XML, public |
| `GET` | `/robots.txt` | Robots | static, public |
| `GET` | `/media/**` | Media files | product/banner images, public |
| `GET` | `/oauth2/authorization/google` | Google sign-in | redirects to Google |
| `GET` | `/login/oauth2/code/google` | OIDC callback | registered redirect URI |
| `POST` | `/api/v1/auth/logout` | Logout | session required, `204` |
| `WS` | `/ws` (STOMP) | Chat WebSocket | session required; `/app/chat/{conversationId}/typing` |
| `GET` | `/actuator/health` | Health | public (details when authorized) |
| `GET` | `/actuator/info`, `/actuator/metrics`, `/actuator/caches` | Actuator | metrics/caches need admin |
| `GET` | `/v3/api-docs` | OpenAPI JSON | ADMIN only |
| `GET` | `/swagger-ui/index.html` | Swagger UI | ADMIN only |

## Verified live (backend on :38133)

| Endpoint | Status |
|---|---|
| `GET /api/v1/public/home` | 200 |
| `GET /api/v1/public/categories/tree` | 200 |
| `GET /api/v1/public/categories/men` | 200 |
| `GET /api/v1/public/categories/men/products?size=2` | 200 |
| `GET /api/v1/public/products?size=2` | 200 |
| `GET /api/v1/public/products/24k-gold` | 200 |
| `GET /api/v1/public/products/24k-gold/related` | 200 |
| `GET /api/v1/public/products/24k-gold/reviews` | 200 |
| `GET /api/v1/public/delivery-areas` | 200 |
| `GET /api/v1/public/payment-methods` | 200 |
| `GET /api/v1/public/csrf` | 204 |
| `POST /api/v1/public/cart/price` | 200 |
| `POST /api/v1/public/orders/track` | 200 (real order + phone) |
| `GET /api/v1/public/orders/track/{token}` | 200 |
| `POST /api/v1/public/coupons/validate` | 400 for an unknown code (route works) |
| `GET /sitemap.xml`, `/robots.txt` | 200 |
| `GET /media/<existing image>` | 200 |
| `GET /api/v1/me` without a session | 401 |
| `GET /api/v1/admin/orders` without a session | 401 |
| `GET /v3/api-docs` without an ADMIN session | 401 |

## Examples

```bash
# catalogue
curl -s http://localhost:38133/api/v1/public/home | jq '.banners, .cards[0]'
curl -s 'http://localhost:38133/api/v1/public/products?q=panjabi&size=10' | jq '.content[0].slug'
curl -s http://localhost:38133/api/v1/public/products/24k-gold | jq '.name, .effectivePrice'

# cart re-pricing (CSRF required)
curl -s -c /tmp/cookies http://localhost:38133/api/v1/public/csrf -o /dev/null
XSRF=$(awk '/XSRF-TOKEN/{print $7}' /tmp/cookies | tail -1)
curl -s -b /tmp/cookies -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"items":[{"variantId":12,"quantity":1,"selected":true}]}' \
  http://localhost:38133/api/v1/public/cart/price | jq

# guest order tracking by token
curl -s http://localhost:38133/api/v1/public/orders/track/1a26239f63804fdba1d3d3040297a702 | jq '.status'

# track by order number + phone (CSRF required)
curl -s -b /tmp/cookies -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"orderNumber":"ORD-20261110-QPEUTR","phone":"+8801952403830"}' \
  http://localhost:38133/api/v1/public/orders/track | jq '.status'
```
