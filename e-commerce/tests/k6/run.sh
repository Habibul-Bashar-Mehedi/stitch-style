#!/usr/bin/env bash
# Run a k6 profile against the local backend and stream metrics to Grafana.
#
#   ./run.sh smoke
#   ./run.sh catalog
#   ./run.sh checkout
#   ./run.sh orders                       # 500 VUs, full order journey (writes!)
#   VUS_MAX=100 RAMP=30 ./run.sh catalog
#   MODE=customer VUS_MAX=500 ./run.sh orders
set -euo pipefail

PROFILE="${1:-catalog}"
[[ -f "$(dirname "$0")/${PROFILE}.js" ]] || { echo "Unknown profile: $PROFILE (use smoke|catalog|checkout|orders|cart|payment|dashboard|stress)"; exit 1; }

cd "$(dirname "$0")"
docker compose up -d influxdb grafana

docker compose --profile run run --rm k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --summary-export="/results/${PROFILE}-summary.json" \
  "/scripts/${PROFILE}.js"

echo
echo "Done. Dashboard: http://localhost:3000/d/k6-load-testing"
echo "Summary:   $(pwd)/results/${PROFILE}-summary.json"
