#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../frontend"
if grep -RInE 'style=|\[style\.|\[ngStyle\]|styles:[[:space:]]*\[' src --include='*.html' --include='*.ts'; then
  echo "Inline CSS is forbidden by the PRD (§10.1)." >&2
  exit 1
fi
echo "No inline CSS found."
