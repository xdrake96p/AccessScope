#!/usr/bin/env bash
# Install debug AccessScope senza force-stop (rompe il bind a11y su Samsung).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DEVICE="${1:-}"
ADB=(adb)
if [[ -n "$DEVICE" ]]; then
  ADB=(adb -s "$DEVICE")
fi

if [[ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

echo "==> assembleDebug"
./gradlew :app:assembleDebug -q

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
echo "==> adb install -r -g (NO force-stop)"
"${ADB[@]}" install -r -g "$APK"

echo "==> soft start MainActivity"
"${ADB[@]}" shell am start -n dev.accessscope.scanner/.MainActivity >/dev/null

echo "==> a11y dump (enabled / bound)"
"${ADB[@]}" shell settings get secure enabled_accessibility_services || true
"${ADB[@]}" shell dumpsys accessibility | sed -n '/enabled services:/,/crashed services:/p' | head -20 || true

echo ""
echo "Se enabled ma bound vuoto: Impostazioni → Accessibilità → AccessScope → OFF → 2s → ON → Consenti"
echo "Wipe completo (solo se serve):"
echo "  1) Disattiva AccessScope in Accessibilità"
echo "  2) adb shell pm uninstall --user 0 dev.accessscope.scanner"
echo "  3) riesegui questo script"
