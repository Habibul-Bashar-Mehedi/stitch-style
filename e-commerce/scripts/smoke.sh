#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:38133}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

curl -fsS -c "$COOKIE_JAR" "$BASE_URL/api/v1/public/csrf" -o /dev/null
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' "$COOKIE_JAR")

HOME_JSON=$(curl -fsS "$BASE_URL/api/v1/public/home")
PRODUCT_SLUG=$(printf '%s' "$HOME_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["latestProducts"][0]["slug"])')
PRODUCT_JSON=$(curl -fsS "$BASE_URL/api/v1/public/products/$PRODUCT_SLUG")
VARIANT_ID=$(printf '%s' "$PRODUCT_JSON" | python3 -c 'import sys,json; p=json.load(sys.stdin); print(next(v["id"] for v in p["variants"] if v["available"] > 0))')
AREA_ID=$(curl -fsS "$BASE_URL/api/v1/public/delivery-areas" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["id"])')
echo "smoke: product=$PRODUCT_SLUG variant=$VARIANT_ID area=$AREA_ID"

PRICE_JSON=$(curl -fsS -b "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/public/cart/price" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" \
  -d "{\"items\":[{\"variantId\":$VARIANT_ID,\"quantity\":1,\"selected\":true}]}")
printf '%s' "$PRICE_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["lines"][0]["lineTotal"] > 0; print("smoke: cart priced", d["grandTotal"])'

KEY="smoke-$(date +%s)-$RANDOM"
ORDER_JSON=$(curl -fsS -b "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/public/orders" \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" -H "Idempotency-Key: $KEY" \
  -d "{\"customer\":{\"name\":\"Smoke Test\",\"phone\":\"01712345678\"},\"shippingAddress\":\"House 1, Road 1, Dhaka 1205\",\"deliveryAreaId\":$AREA_ID,\"paymentMethod\":\"COD\",\"items\":[{\"variantId\":$VARIANT_ID,\"quantity\":1,\"selected\":true}]}")
ORDER_NUMBER=$(printf '%s' "$ORDER_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["orderNumber"])')
PUBLIC_TOKEN=$(printf '%s' "$ORDER_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["publicToken"])')
echo "smoke: order=$ORDER_NUMBER"

TRACK_JSON=$(curl -fsS "$BASE_URL/api/v1/public/orders/track/$PUBLIC_TOKEN")
printf '%s' "$TRACK_JSON" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['orderNumber']=='$ORDER_NUMBER'; print('smoke: tracking ok', d['status'])"
