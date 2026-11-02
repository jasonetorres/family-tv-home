#!/bin/bash
# Build the Family Home launcher (a fork of Luma TV). No Gradle: aapt2, javac, d8, zipalign, apksigner.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/android-sdk}}"
BT="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
mkdir -p build/classes build/dex dist
rm -rf build/resources.zip build/resources.apk build/unsigned.apk build/overlay build/overlay.zip
python3 ../scripts/sync_config.py launcher "$PWD" >/tmp/family-greeting.txt
GREETING="${FAMILY_GREETING:-$(head -n 1 .local/greeting.txt 2>/dev/null || true)}"
GREETING="${GREETING:-$(head -n 1 /tmp/family-greeting.txt)}"
mkdir -p build/overlay/values
python3 - "$GREETING" > build/overlay/values/greeting.xml <<'PY'
import sys, html
text = html.escape(sys.argv[1], quote=False)
for raw, escaped in (("\\", "\\\\"), ("'", "\\'"), ('"', '\\"'), ("@", "\\@"), ("?", "\\?")):
    text = text.replace(raw, escaped)
print('<resources><string name="greeting">' + text + '</string></resources>')
PY
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" compile --dir build/overlay -o build/overlay.zip
"$BT/aapt2" link -o build/resources.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml --java build/generated --auto-add-overlay build/resources.zip -R build/overlay.zip
find src build/generated -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" -d build/classes @build/sources.txt
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output build/dex @build/classes.txt
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
KEYSTORE="${FAMILY_KEYSTORE:-.signing/local.keystore}"
if [ ! -f .signing/local.keystore ] && [ "$KEYSTORE" = .signing/local.keystore ]; then
    mkdir -p .signing
    keytool -genkeypair -keystore .signing/local.keystore -storepass android -keypass android \
      -alias family-home -keyalg RSA -keysize 2048 -validity 10000 \
      -dname 'CN=Family Home, O=Local build'
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --ks-key-alias family-home --out dist/family-home.apk build/aligned.apk
"$BT/apksigner" verify dist/family-home.apk
ls -lh dist/family-home.apk
