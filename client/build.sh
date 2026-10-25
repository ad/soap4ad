#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AJ="$SDK/platforms/android-36/android.jar"
[ -f "$AJ" ] || { echo "Missing platform: sdkmanager \"platforms;android-36\""; exit 1; }
JBIN="${JAVA_HOME:+$JAVA_HOME/bin/}"

CERT_SHA256=3d2c93f53d41c7fe731f76d5aee83405cbb023c1e3b6f0dffc9eb4385e6ed8be

VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-0.1.$VERSION_CODE}"

if [ -z "${KEYSTORE_FILE:-}" ] && [ -f ../signing.env ]; then
  set -a; . ../signing.env; set +a
fi

mkdir -p libs
LIBS=()
while read -r url sum; do
  file="libs/$(basename "$url")"
  [ -f "$file" ] || curl -fsSL "$url" -o "$file"
  echo "$sum  $file" | shasum -a 256 -c --quiet || { rm -f "$file"; echo "Checksum mismatch: $url"; exit 1; }
  if [[ "$file" == *.aar ]]; then
    unzip -qo "$file" classes.jar -d "${file%.aar}" && LIBS+=("${file%.aar}/classes.jar")
    unzip -qo "$file" proguard.txt -d "${file%.aar}" 2>/dev/null || true
  else
    LIBS+=("$file")
  fi
done < deps.txt
CP="$AJ:$(IFS=:; echo "${LIBS[*]}")"

rm -rf out && mkdir -p out/classes
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$AJ" --manifest AndroidManifest.xml out/res.zip \
  --min-sdk-version 24 --target-sdk-version 36 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"
"${JBIN}javac" -nowarn -source 17 -target 17 -classpath "$CP" -encoding UTF-8 -d out/classes $(find src -name '*.java')
(cd out/classes && zip -qr ../app.jar .)
"${JBIN}java" -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release --min-api 24 --lib "$AJ" --output out --map-diagnostics info none \
  --pg-conf proguard.pro $(find libs -name proguard.txt -exec echo --pg-conf {} \;) out/app.jar "${LIBS[@]}"
(cd out && zip -qj base.apk classes*.dex)
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk
if [ -z "${KEYSTORE_FILE:-}" ]; then
  cp out/aligned.apk soap-tv-unsigned.apk
  echo "Built soap-tv-unsigned.apk $VERSION_NAME: no signing key, set KEYSTORE_* or create ../signing.env"
  exit 0
fi
"$BT/apksigner" sign --ks "$KEYSTORE_FILE" --ks-pass env:KEYSTORE_PASSWORD --ks-key-alias "$KEY_ALIAS" \
  --key-pass env:KEY_PASSWORD --out soap-tv.apk out/aligned.apk
"$BT/apksigner" verify --print-certs soap-tv.apk | grep -q "SHA-256 digest: $CERT_SHA256" ||
  { rm soap-tv.apk; echo "soap-tv.apk is not signed with the release key $CERT_SHA256"; exit 1; }
echo "Built soap-tv.apk $VERSION_NAME ($(du -h soap-tv.apk | cut -f1))"
