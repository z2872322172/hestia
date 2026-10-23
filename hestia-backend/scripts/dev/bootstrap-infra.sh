#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
fi

"$SCRIPT_DIR/cleanup-legacy-tianji.sh" "$@"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  exit 0
fi

if [[ "$DRY_RUN" == "false" ]]; then
  echo
  "$SCRIPT_DIR/start-infra.sh"
fi
