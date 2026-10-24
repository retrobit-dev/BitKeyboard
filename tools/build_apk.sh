#!/usr/bin/env bash
# Bangun APK release tanpa Gradle/Android SDK penuh.
#
# Skrip ini memakai aapt2 (Linux x86_64), d8, apksigner, dan android.jar.
# Berguna di lingkungan terbatas atau untuk memverifikasi build dengan cepat.
# Untuk rilis resmi, `./gradlew assembleRelease` tetap menjadi jalur utama.
#
# Pemakaian:
#   tools/build_apk.sh [versi]
#
# Variabel lingkungan:
#   JAVA_HOME    JDK 17+ (wajib)
#   ANDROID_JAR  path android.jar (default: $ANDROID_HOME/platforms/android-35/android.jar)
#   AAPT2        path aapt2 (default: aapt2 dari PATH)
#   D8_JAR       path d8.jar (default: $ANDROID_HOME/build-tools/*/lib/d8.jar)
#   APKSIGNER    path apksigner.jar
#   KOTLINC      path kotlinc (default: kotlinc dari PATH)
#   KEYSTORE     keystore rilis (default: keystore/bitkeyboard-release.jks)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:-1.1.0}"
VERSION_CODE="${VERSION_CODE:-2}"
OUT="$ROOT/dist"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

: "${JAVA_HOME:?JAVA_HOME belum diatur}"
export PATH="$JAVA_HOME/bin:$PATH"
JAVA="$JAVA_HOME/bin/java"

KOTLINC="${KOTLINC:-kotlinc}"
AAPT2="${AAPT2:-aapt2}"
KEYSTORE="${KEYSTORE:-$ROOT/keystore/bitkeyboard-release.jks}"
STORE_PASS="${STORE_PASS:-bitkeyboard}"
KEY_ALIAS="${KEY_ALIAS:-bitkeyboard}"

if [ -z "${ANDROID_JAR:-}" ]; then
  ANDROID_JAR="${ANDROID_HOME:-}/platforms/android-35/android.jar"
fi
if [ ! -f "$ANDROID_JAR" ]; then
  echo "android.jar tidak ditemukan. Set ANDROID_JAR ke platform android-35." >&2
  exit 1
fi

if [ -z "${D8_JAR:-}" ]; then
  D8_JAR="$(ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/lib/d8.jar 2>/dev/null | sort -V | tail -1 || true)"
fi
if [ -z "${APKSIGNER:-}" ]; then
  APKSIGNER="$(ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/lib/apksigner.jar 2>/dev/null | sort -V | tail -1 || true)"
fi
for tool in "$D8_JAR" "$APKSIGNER"; do
  if [ ! -f "$tool" ]; then
    echo "Alat build tidak ditemukan: $tool" >&2
    exit 1
  fi
done
if [ ! -f "$KEYSTORE" ]; then
  echo "Keystore tidak ditemukan: $KEYSTORE" >&2
  exit 1
fi

echo "==> aapt2 compile"
"$AAPT2" compile --dir "$ROOT/app/src/main/res" -o "$BUILD/res.zip"

# aapt2 link membutuhkan atribut package pada manifest; Gradle menambahkannya
# otomatis. Salinan sementara di bawah ini tidak mengubah berkas sumber.
python3 - "$ROOT/app/src/main/AndroidManifest.xml" "$BUILD/AndroidManifest.xml" <<'PY'
import sys
src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding='utf-8').read()
if 'package=' not in text.split('>', 1)[0]:
    text = text.replace(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android"',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        'package="dev.retrobit.bitkeyboard"',
        1,
    )
open(dst, 'w', encoding='utf-8').write(text)
PY

echo "==> aapt2 link"
"$AAPT2" link \
  -o "$BUILD/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$BUILD/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  -A "$ROOT/app/src/main/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version 21 \
  --target-sdk-version 35 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION" \
  --auto-add-overlay

echo "==> R.java"
mkdir -p "$BUILD/genclasses"
JC="${JAVAC:-}"
if [ -z "$JC" ]; then
  if command -v javac >/dev/null 2>&1; then
    javac -source 17 -target 17 -cp "$ANDROID_JAR" -d "$BUILD/genclasses" "$(find "$BUILD/gen" -name '*.java')"
  elif [ -n "${ECJ_JAR:-}" ] && [ -f "$ECJ_JAR" ]; then
    "$JAVA" -jar "$ECJ_JAR" -source 17 -target 17 -cp "$ANDROID_JAR" \
      -d "$BUILD/genclasses" "$(find "$BUILD/gen" -name '*.java')"
  else
    echo "Butuh javac atau ECJ_JAR untuk mengompilasi R.java" >&2
    exit 1
  fi
fi

echo "==> kotlinc"
mkdir -p "$BUILD/classes"
"$KOTLINC" -cp "$ANDROID_JAR:$BUILD/genclasses" -d "$BUILD/classes" \
  "$ROOT"/app/src/main/java/dev/retrobit/bitkeyboard/core/*.kt \
  "$ROOT"/app/src/main/java/dev/retrobit/bitkeyboard/ime/*.kt \
  "$ROOT"/app/src/main/java/dev/retrobit/bitkeyboard/settings/*.kt

echo "==> d8"
mkdir -p "$BUILD/dex"
STDLIB="${KOTLIN_STDLIB:-$(dirname "$(command -v "$KOTLINC")")/../lib/kotlin-stdlib.jar}"
if [ ! -f "$STDLIB" ]; then
  echo "kotlin-stdlib.jar tidak ditemukan. Set KOTLIN_STDLIB." >&2
  exit 1
fi
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 \
  --release --lib "$ANDROID_JAR" --min-api 21 \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class') "$STDLIB" 2>&1 | grep -v "kotlin.Metadata\|An error occurred when parsing kotlin metadata\|Should never be called\|^\s*at \|malformed" || true

echo "==> paket & selaras"
python3 "$ROOT/tools/apkpack.py" "$BUILD/base.apk" "$BUILD/unsigned.apk" \
  classes.dex:"$BUILD/dex/classes.dex"

echo "==> tanda tangan"
mkdir -p "$OUT"
UNSIGNED="$OUT/BitKeyboard-$VERSION-release-unsigned.apk"
"$JAVA" -jar "$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-pass "pass:$STORE_PASS" --key-pass "pass:$STORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" \
  --min-sdk-version 21 --v1-signing-enabled true --v2-signing-enabled true \
  --out "$OUT/BitKeyboard-$VERSION-release.apk" "$BUILD/unsigned.apk" 2>&1 | grep -v "^WARNING" || true

"$JAVA" -jar "$APKSIGNER" verify --min-sdk-version 21 \
  "$OUT/BitKeyboard-$VERSION-release.apk" 2>&1 | grep -v "^WARNING" || true

rm -f "$UNSIGNED"
echo
echo "APK: $OUT/BitKeyboard-$VERSION-release.apk ($(du -h "$OUT/BitKeyboard-$VERSION-release.apk" | cut -f1))"
