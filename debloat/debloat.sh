#!/bin/bash
# Disable or restore TCL Google TV packages. disable-user only: a factory reset or
# "restore" brings them back. Nothing is uninstalled.
#
#   debloat/debloat.sh disable              # suggested short list
#   debloat/debloat.sh restore
#   debloat/debloat.sh disable --tier safe  # the longer "safe" tier in tcl-google-tv.txt
#
# TV address: first argument that looks like host:port, else $FAMILY_TV,
# else tv.address in config/family.json. The example address 192.0.2.10 is rejected.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ACTION="disable"
TIER=""
TV="${FAMILY_TV:-}"
for arg in "$@"; do
  case "$arg" in
    disable|restore) ACTION="$arg" ;;
    --tier) ;;
    safe|optional) TIER="$arg" ;;
    *:*) TV="$arg" ;;
  esac
done
if [ "${1:-}" = "--tier" ]; then TIER="${2:-safe}"; fi
# allow: debloat.sh disable --tier safe
prev=""
for arg in "$@"; do
  if [ "$prev" = "--tier" ]; then TIER="$arg"; fi
  prev="$arg"
done

if [ -z "$TV" ] && [ -f "$ROOT/config/family.json" ]; then
  TV="$(python3 - "$ROOT/config/family.json" <<'PY'
import json, sys
print((json.load(open(sys.argv[1], encoding="utf-8")).get("tv") or {}).get("address") or "")
PY
)"
fi
if [ -z "$TV" ] || [ "$TV" = "192.0.2.10:5555" ]; then
  echo "Set the TV address: debloat/debloat.sh disable 192.0.2.10:5555" >&2
  echo "Replace that documentation address with the TV's real adb address." >&2
  exit 1
fi

echo "This disables user-level packages. It can remove channels, accounts, or voice features."
echo "Packages marked keep in debloat/tcl-google-tv.txt are never touched."
echo "Do not disable the stock launcher until Family Home is already the home screen."
echo "com.tcl.t_solo and com.tcl.ui_mediaCenter are on the suggested list but are not in every public catalog. Confirm them on your firmware."
echo "Action: $ACTION    TV: $TV"
printf "Type YES to continue: "
read -r answer
[ "$answer" = "YES" ] || { echo "Cancelled."; exit 1; }

adb connect "$TV" >/dev/null

if [ -n "$TIER" ]; then
  PACKAGES="$(awk -F'|' -v tier="$TIER" '
    $0 ~ /^[[:space:]]*#/ { next }
    NF >= 2 {
      gsub(/^[ \t]+|[ \t]+$/, "", $1)
      gsub(/^[ \t]+|[ \t]+$/, "", $2)
      if ($2 == tier) print $1
    }
  ' "$ROOT/debloat/tcl-google-tv.txt")"
else
  PACKAGES="com.tcl.tv.plus com.tcl.t_solo com.tcl.ui_mediaCenter com.tcl.dashboard com.tcl.hearaid com.tcl.usercenter"
fi

for p in $PACKAGES; do
  hit="$(adb -s "$TV" shell pm list packages "$p" | tr -d '\r')"
  printf '%s\n' "$hit" | grep -qx "package:$p" || { echo "skip $p (not installed)"; continue; }
  if [ "$ACTION" = "restore" ]; then
    adb -s "$TV" shell pm enable --user 0 "$p" && echo "enabled $p"
  else
    adb -s "$TV" shell pm disable-user --user 0 "$p" && echo "disabled $p"
  fi
done
echo "Live TV (com.tcl.tv) and the input menu (com.tcl.suspension) stay installed. The launcher hides them."
