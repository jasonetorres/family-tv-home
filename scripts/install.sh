#!/bin/bash
# Install every Family Home app on a Google TV. Usage: scripts/install.sh [IP:5555]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TV="${1:-${FAMILY_TV:-}}"
if [ -z "$TV" ] && [ -f "$ROOT/config/family.json" ]; then
  TV="$(python3 - "$ROOT/config/family.json" <<'PY'
import json, sys
print((json.load(open(sys.argv[1])).get("tv") or {}).get("address") or "")
PY
)"
fi
if [ -z "$TV" ] || [ "$TV" = "192.0.2.10:5555" ]; then
  echo "Pass the TV address: scripts/install.sh 192.168.1.20:5555" >&2
  echo "Or set tv.address in config/family.json. The example address is a documentation placeholder." >&2
  exit 1
fi
adb connect "$TV"
DEV="$TV"
adb -s "$DEV" install -r "$ROOT/launcher/dist/family-home.apk"
adb -s "$DEV" install -r "$ROOT/weather/app/build/outputs/apk/debug/weather-debug.apk"
adb -s "$DEV" install -r "$ROOT/notes/app/build/outputs/apk/debug/notes-debug.apk"
adb -s "$DEV" install -r "$ROOT/where/app/build/outputs/apk/debug/where-debug.apk"
adb -s "$DEV" install -r "$ROOT/ticker/app/build/outputs/apk/debug/ticker-debug.apk"
adb -s "$DEV" shell appops set app.familytv.notes SYSTEM_ALERT_WINDOW allow || true
adb -s "$DEV" shell appops set app.familytv.where SYSTEM_ALERT_WINDOW allow || true
adb -s "$DEV" shell appops set app.familytv.ticker SYSTEM_ALERT_WINDOW allow || true
echo "Installed. Make Family Home the launcher with: launcher/family-home.sh -s $TV home"
echo "First launch of Notes, Travel, and Score Ticker starts their listeners. They also start on boot."
