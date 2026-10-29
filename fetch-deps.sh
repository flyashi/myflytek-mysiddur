#!/usr/bin/env bash
# Fetch the build dependencies into deps/ (all gitignored). Versions are pinned.
#   - r8.jar         : Google's D8 dexer (compiler, not shipped in the app)
#   - android.jar    : an API>=30 platform stub to compile/link against
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p deps

R8_VER=8.13.25

fetch() { echo ">> $2"; curl -fsSL --retry 3 -o "$1" "$2"; }

[ -f deps/r8.jar ] || \
  fetch deps/r8.jar "https://dl.google.com/dl/android/maven2/com/android/tools/r8/${R8_VER}/r8-${R8_VER}.jar"

if [ ! -f deps/android.jar ]; then
  if [ -n "${ANDROID_HOME:-}" ] && [ -f "$ANDROID_HOME/platforms/android-30/android.jar" ]; then
    cp "$ANDROID_HOME/platforms/android-30/android.jar" deps/android.jar
  else
    echo ">> android platform-30 (first run only, ~60MB)"
    tmp=$(mktemp -d)
    fetch "$tmp/p30.zip" "https://dl.google.com/android/repository/platform-30_r03.zip"
    (cd "$tmp" && unzip -q p30.zip)
    cp "$tmp"/android-*/android.jar deps/android.jar
    rm -rf "$tmp"
  fi
fi

echo "deps ready:"; ls -l deps/
