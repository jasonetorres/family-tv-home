#!/bin/bash
# Create config/family.json from the example and fill a fresh ntfy topic when it is blank.
# Re-running keeps an existing config/family.json so rebuilds stay on the same topic.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/config/family.json"
EX="$ROOT/config/family.example.json"
if [ ! -f "$OUT" ]; then
  python3 - "$EX" "$OUT" <<'PY'
import json, secrets, sys
cfg = json.load(open(sys.argv[1], encoding="utf-8"))
topic = (cfg.get("ntfy") or {}).get("topic") or ""
if not str(topic).strip():
    cfg.setdefault("ntfy", {})["topic"] = "family-" + secrets.token_hex(12)
json.dump(cfg, open(sys.argv[2], "w", encoding="utf-8"), indent=2)
open(sys.argv[2], "a", encoding="utf-8").write("\n")
print(cfg["ntfy"]["topic"])
PY
  echo "Wrote config/family.json"
else
  python3 - "$OUT" <<'PY'
import json, sys
cfg = json.load(open(sys.argv[1], encoding="utf-8"))
print((cfg.get("ntfy") or {}).get("topic") or "(topic still blank)")
PY
  echo "Kept existing config/family.json"
fi
echo "Subscribe the phone to that topic, the topic with -replies, and the topic with -where."
echo "config/family.json is gitignored. Edit names, home, teams, and apps there, then rebuild."
