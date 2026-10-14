#!/data/data/com.termux/files/usr/bin/bash
# core 纯逻辑本地测试循环: kotlinc 编译 core/src + core/test -> JUnitCore 运行
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
OUT=core/build/classes
rm -rf core/build && mkdir -p "$OUT"

CP="core/lib/junit-4.13.2.jar:core/lib/hamcrest-core-1.3.jar"
# kotlinc 自带 stdlib 供 JVM 运行期使用
KOTLINC_LIB="$(cd "$(dirname "$(readlink -f "$(command -v kotlinc)")")/.." && pwd)/lib"
STDLIB="$KOTLINC_LIB/kotlin-stdlib.jar"

SRCS=$(find core/src core/test -name '*.kt' 2>/dev/null || true)
if [ -z "$SRCS" ]; then echo "[core-test] no sources, skip"; exit 0; fi

echo "[core-test] compiling..."
if ! kotlinc -nowarn -cp "$CP" -d "$OUT" $SRCS 2> core/build/compile.log; then
  echo "[core-test] COMPILE FAILED:"
  cat core/build/compile.log
  exit 1
fi

TESTS=$(cd "$OUT" && find . -name '*Test.class' ! -name '*$*' | sed 's|^\./||;s|\.class$||;s|/|.|g' | tr '\n' ' ')
if [ -z "${TESTS// /}" ]; then echo "[core-test] no test classes"; exit 0; fi

echo "[core-test] running: $TESTS"
java -cp "$OUT:$CP:$STDLIB" org.junit.runner.JUnitCore $TESTS
