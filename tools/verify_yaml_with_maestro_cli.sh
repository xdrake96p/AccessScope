#!/usr/bin/env bash
# Verifica un YAML esportato da AccessScope con Maestro CLI (piano M1-A4).
# Non bloccante in CI: esecuzione manuale / nightly.
#
# Uso:
#   tools/verify_yaml_with_maestro_cli.sh path/to/flow.yaml
#   DEVICE_ID=emulator-5554 tools/verify_yaml_with_maestro_cli.sh flow.yaml
#
set -euo pipefail

YAML="${1:-}"
if [[ -z "$YAML" || ! -f "$YAML" ]]; then
  echo "Uso: $0 <flow.yaml>" >&2
  exit 2
fi

if ! command -v maestro >/dev/null 2>&1; then
  echo "Maestro CLI non trovato (installa: https://docs.maestro.dev)." >&2
  exit 2
fi

ARGS=(test "$YAML")
if [[ -n "${DEVICE_ID:-}" ]]; then
  ARGS=(--device "$DEVICE_ID" "${ARGS[@]}")
fi

echo ">> maestro ${ARGS[*]}"
maestro "${ARGS[@]}"
echo "OK: Maestro CLI ha eseguito $YAML senza errori di parsing/run."
