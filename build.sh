#!/usr/bin/env bash
# Build + sign My Siddur with no Gradle: aapt2 -> javac -> d8 -> zipalign ->
# apksigner. Produces build/myflytek-mysiddur.apk, signed with the mfk app key so
# it sits alongside the rest of the de-clouded AiNote stack.
set -euo pipefail
cd "$(dirname "$0")"

MIN_SDK=26
TARGET_SDK=29
ANDROID_JAR=deps/android.jar
R8=deps/r8.jar
PKG_APK=build/myflytek-mysiddur.apk

MFK_PK8=${MFK_PK8:-$HOME/ainote-mods/capture/mfk_appkey.pk8}
MFK_CERT=${MFK_CERT:-$HOME/ainote-mods/capture/mfk_appkey.x509.pem}

for f in "$ANDROID_JAR" "$R8" content/index.json content/calendar.json; do
  [ -f "$f" ] || { echo "missing $f — run ./fetch-deps.sh first"; exit 1; }
done

rm -rf build/gen build/classes build/dex build/link build/assets
mkdir -p build/gen build/classes build/dex build/link build/assets

echo "== assets: texts, calendar, reader css, fonts =="
# content/ is made by tools/fetch-source.sh + tools/import.py (committed, so a build needs no network)
cp -r app/assets/. build/assets/
mkdir -p build/assets/siddur
cp -r content/. build/assets/siddur/
echo "assets: $(du -sh build/assets | cut -f1)"

echo "== aapt2: compile resources =="
aapt2 compile --dir app/res -o build/res.zip

echo "== aapt2: link =="
aapt2 link \
  -o build/link/base.apk \
  --manifest app/AndroidManifest.xml \
  -I "$ANDROID_JAR" \
  -A build/assets \
  --java build/gen \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  build/res.zip

echo "== javac =="
find app/src build/gen -name '*.java' > build/sources.txt
javac --release 8 -nowarn -Xlint:none \
  -classpath "$ANDROID_JAR" \
  -d build/classes @build/sources.txt
jar cf build/app-classes.jar -C build/classes .

echo "== d8 =="
java -cp "$R8" com.android.tools.r8.D8 \
  --release --min-api "$MIN_SDK" \
  --lib "$ANDROID_JAR" \
  --output build/dex \
  build/app-classes.jar

echo "== assemble apk =="
cp build/link/base.apk build/unsigned.apk
( cd build/dex && zip -q ../../build/unsigned.apk classes.dex )

echo "== zipalign =="
zipalign -f -p 4 build/unsigned.apk build/aligned.apk

echo "== apksigner (mfk key) =="
[ -f "$MFK_PK8" ]  || { echo "missing signing key $MFK_PK8"; exit 1; }
[ -f "$MFK_CERT" ] || { echo "missing signing cert $MFK_CERT"; exit 1; }
apksigner sign \
  --key "$MFK_PK8" --cert "$MFK_CERT" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$PKG_APK" build/aligned.apk
rm -f "${PKG_APK}.idsig"

echo "== verify =="
apksigner verify --print-certs "$PKG_APK" | sed -n '1,2p'
echo "md5: $(md5sum "$PKG_APK" | cut -d' ' -f1)"
echo "size: $(stat -c%s "$PKG_APK") bytes"
echo "BUILT: $PKG_APK"
