#!/usr/bin/env bash
# Requires Docker and network access to pull the ZAP image.
set -euo pipefail
TARGET="${1:-http://host.docker.internal:38133}"
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t "$TARGET" -r /zap/wrk/zap-report.html
