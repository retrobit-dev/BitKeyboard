#!/usr/bin/env bash
# Bangun demo web: kompilasi inti Kotlin + JsBridge ke JavaScript, lalu salin
# kamus. Keluaran ada di demo/dist/ (tidak masuk Git).
#
#   tools/build_demo.sh
#
# Variabel lingkungan:
#   KOTLINC_JS  path ke skrip kotlinc-js (default: kotlinc-js dari PATH)
#   JAVA_HOME   JDK 17+ yang bisa dipakai kotlinc
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/demo/dist"
BUILD="$(mktemp -d)"
KOTLINC_JS="${KOTLINC_JS:-kotlinc-js}"

if ! command -v "$KOTLINC_JS" >/dev/null 2>&1; then
  echo "kotlinc-js tidak ditemukan. Set KOTLINC_JS=/path/ke/kotlinc-js" >&2
  exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

STDLIB="${KOTLIN_JS_STDLIB:-}"
if [ -z "$STDLIB" ]; then
  for candidate in \
    "$(dirname "$(command -v "$KOTLINC_JS")")/../lib/kotlin-stdlib-js.klib" \
    "$HOME/.konan/kotlin-native-prebuilt-linux-x86_64-2.1.0/klib/common/stdlib" \
    "/usr/share/kotlin/lib/kotlin-stdlib-js.klib"; do
    if [ -f "$candidate" ]; then STDLIB="$candidate"; break; fi
  done
fi

echo "==> kompilasi klib"
ARGS=(
  -kotlin-home "$(dirname "$(dirname "$(command -v "$KOTLINC_JS")")")"
  -ir-output-dir "$BUILD/klib"
  -ir-output-name bitcore
)
if [ -n "$STDLIB" ]; then ARGS+=(-libraries "$STDLIB"); fi
"$KOTLINC_JS" "${ARGS[@]}" \
  "$ROOT"/app/src/main/java/dev/retrobit/bitkeyboard/core/*.kt \
  "$ROOT"/demo/src/JsBridge.kt

echo "==> hasilkan JavaScript"
mkdir -p "$OUT"
JS_ARGS=(
  -Xir-produce-js
  -Xinclude="$BUILD/klib/bitcore.klib"
  -kotlin-home "$(dirname "$(dirname "$(command -v "$KOTLINC_JS")")")"
  -ir-output-dir "$OUT"
  -ir-output-name bitcore
  -module-kind umd
  -target es2015
)
if [ -n "$STDLIB" ]; then JS_ARGS+=(-libraries "$STDLIB"); fi
"$KOTLINC_JS" "${JS_ARGS[@]}"

echo "==> salin kamus"
cp "$ROOT/app/src/main/assets/dictionary_id.txt" "$OUT/dictionary.txt"

rm -rf "$BUILD"
printf 'demo siap: %s (bitcore.js %s, kamus %s)\n' \
  "$OUT" \
  "$(du -h "$OUT/bitcore.js" | cut -f1)" \
  "$(du -h "$OUT/dictionary.txt" | cut -f1)"
