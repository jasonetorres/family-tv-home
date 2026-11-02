#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
bash scripts/configure.sh
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
if [ ! -f local.properties ]; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
fi
python3 scripts/make_sounds.py
echo "Building launcher"
bash launcher/build.sh
echo "Building Android apps"
chmod +x ./gradlew
./gradlew assembleDebug --offline 2>/dev/null || ./gradlew assembleDebug
echo "APKs:"
ls -lh launcher/dist/family-home.apk \
  notes/app/build/outputs/apk/debug/notes-debug.apk \
  weather/app/build/outputs/apk/debug/weather-debug.apk \
  where/app/build/outputs/apk/debug/where-debug.apk \
  ticker/app/build/outputs/apk/debug/ticker-debug.apk
