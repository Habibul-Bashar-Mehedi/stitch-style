#!/usr/bin/env bash
# Maps the Stitch & Style local hostnames to 127.0.0.1.
# Run once:  sudo bash e-commerce/frontend/scripts/add-local-hosts.sh
# (or just:  bash e-commerce/frontend/scripts/add-local-hosts.sh — it calls sudo itself)
set -euo pipefail

MARKER="stitchandstyle.local"
ENTRIES="127.0.0.1 stitchandstyle.com www.stitchandstyle.com stichandstyle.com stich-style.com stichstyle.com stitchstyle.com www.stitchstyle.com stitchandstyle.local stitch-and-style.local stitchstyle.local"

if grep -q "$MARKER" /etc/hosts; then
  echo "✔ Stitch & Style entries already present in /etc/hosts"
else
  echo "$ENTRIES" | sudo tee -a /etc/hosts > /dev/null
  echo "✔ Added Stitch & Style entries to /etc/hosts"
fi

echo
echo "Resolution check:"
for host in stitchandstyle.com www.stitchandstyle.com stichandstyle.com stich-style.com stichstyle.com \
            stitchstyle.com stitchandstyle.local stitch-and-style.local stitchstyle.local; do
  printf '  %-26s %s\n' "$host" "$(getent hosts "$host" | awk '{print $1}' | paste -sd, -)"
done

echo
echo "Nginx check (expect 200):"
for host in stitchandstyle.com stichandstyle.com stich-style.com stichstyle.com stitchstyle.com localhost; do
  printf '  %-26s %s\n' "$host" "$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $host" http://127.0.0.1/)"
done

echo
echo "Open http://stitchandstyle.com (or http://stitchstyle.com) in the browser."
echo "If the browser insists on https://, type the http:// prefix explicitly."
echo "Note: 'stich&style.com' can never work — '&' is not a legal hostname character."
