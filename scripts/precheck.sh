#!/data/data/com.termux/files/usr/bin/bash
# app 源码语法预检（kotlinc 无依赖全量编译，过滤依赖噪声，只报语法类错误）
cd "$(dirname "$0")/.."
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
OUT=build/precheck_out
LOG=build/precheck.log
mkdir -p build
rm -rf "$OUT"

SRCS=$(find app/src/main/java -name '*.kt' 2>/dev/null || true)
if [ -z "$SRCS" ]; then echo "[precheck] no sources"; exit 0; fi

kotlinc -nowarn -d "$OUT" $SRCS > "$LOG" 2>&1 || true

# 依赖缺失类噪声（无 android/compose classpath 时必然全报）
NOISE='unresolved reference|cannot access|cannot infer|illegal annotation|type mismatch|type argument|too many arguments|no value passed|cannot be called|receiver type mismatch|argument type mismatch|cannot infer a type|overload resolution|selector in an import|classpath entry'
SYNTAX='Expecting|Unexpected|expected|unterminated|syntax|Missing|unclosed|Name expected|Expecting an element|Expected [a-z]|Encountered'

BAD=$(grep "error:" "$LOG" | grep -vE "$NOISE" | grep -E "$SYNTAX" || true)
if [ -n "$BAD" ]; then
  echo "[precheck] SYNTAX ERRORS:"
  echo "$BAD"
  exit 1
fi
echo "[precheck] OK ($(grep -c 'error:' "$LOG" 2>/dev/null || echo 0) 依赖噪声已过滤)"
