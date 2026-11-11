#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:38133}"
REQUESTS="${REQUESTS:-100}"
FAILURES=0
export BASE_URL FAILURES
# Use curl in parallel as a dependency-free load smoke. The k6 scripts under
# e-commerce/tests/k6 are the full load profile for CI.
seq 1 "$REQUESTS" | xargs -P 20 -I{} bash -c '
  code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/public/home")
  if [ "$code" != "200" ]; then echo "FAIL $code" >&2; exit 1; fi
' || FAILURES=$?
if [ "$FAILURES" -ne 0 ]; then echo "load smoke FAILED" >&2; exit 1; fi
echo "load smoke: $REQUESTS requests, all 200"
