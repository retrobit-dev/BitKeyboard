#!/usr/bin/env bash
# Menjalankan uji inti (tanpa Android) memakai kotlinc + JDK.
# Pemakaian: tools/jvmtest/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

KOTLINC="${KOTLINC:-kotlinc}"
OUT="build/jvmtest"
mkdir -p "$OUT"

echo "== Kompilasi inti + uji =="
"$KOTLINC" app/src/main/java/dev/retrobit/bitkeyboard/core/*.kt \
    tools/jvmtest/CoreTests.kt -include-runtime -d "$OUT/bitcore-tests.jar"

echo "== Jalankan =="
java -jar "$OUT/bitcore-tests.jar"
