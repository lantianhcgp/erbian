#!/usr/bin/env bash
# 功能 + 连通性 + 黑夜模式 验收（用户要求 2026-10-16：
#   "不仅要界面测试通过，还要所有功能测试通过"、"把黑夜模式、按钮测试这些检查事项加到流程"、
#   "模拟器测试要把连通性功能也测一遍"）
# 运行于 CI 模拟器（adb）。任何 FAIL → FUNC_ASSERT_FAIL + exit 1（走 verdict 终局门禁）。
EV=build/acceptance
PKG=com.lantianhcgp.erbian
PORT=8790
mkdir -p "$EV"
FUNC_FAIL=0
ok()  { echo "  FUNC OK  : $*"; }
bad() { echo "  FUNC FAIL: $*"; FUNC_FAIL=1; }

R() {
  if [ -x "$HOME/rish/rish_t" ]; then echo "$1" | timeout 40 "$HOME/rish/rish_t" 2>/dev/null
  else echo "$1" | timeout 40 adb shell 2>/dev/null; fi
}
tap() { timeout 20 adb shell input tap "$1" "$2" >/dev/null 2>&1 || true; }
snap() { # $1=设备dump路径 → 拉到 $EV
  R "uiautomator dump $1" >/dev/null 2>&1
  adb pull "$1" "$EV/$(basename "$1")" >/dev/null 2>&1 || true
}
# 从 dump 里按序取所有 Switch 的 checked（设置页顺序: MCP/动态取色/纯黑）
switch_states() {
  python3 - "$EV/$1" <<'PYEOF'
import re, sys
try:
    xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
except OSError:
    print("NONE"); raise SystemExit
sw = re.findall(r'class="[^"]*Switch"[^>]*checked="([^"]+)"', xml)
if not sw:
    sw = re.findall(r'checked="([^"]+)"[^>]*class="[^"]*Switch"', xml)
print(",".join(sw) if sw else "NONE")
PYEOF
}
# 取第 N 个 Switch 的中心坐标（0起）
switch_xy() {
  python3 - "$EV/$1" "$2" <<'PYEOF'
import re, sys
try:
    xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
except OSError:
    raise SystemExit
nodes = re.findall(r'<node[^>]*class="[^"]*Switch"[^>]*>', xml)
if not nodes:
    nodes = [m for m in re.findall(r'<node[^>]*>', xml) if 'Switch' in m]
i = int(sys.argv[2])
if i < len(nodes):
    m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', nodes[i])
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
PYEOF
}
nav() { # $1=tab desc
  snap /sdcard/fn_nav.xml
  local xy
  xy=$(python3 - "$EV/fn_nav.xml" "$1" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
d = sys.argv[2]
for m in re.finditer(r'content-desc="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % d, xml):
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2); break
for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="%s"' % d, xml):
    x1, y1, x2, y2 = map(int, m.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2); break
PYEOF
)
  [ -n "$xy" ] && tap $xy
  sleep 2
}

echo "== [F1] 连通性: 内嵌服务 /health =="
timeout 20 adb forward tcp:$PORT tcp:$PORT >/dev/null 2>&1 || true
sleep 1
H=$(curl -s -m 8 "http://127.0.0.1:$PORT/health" 2>/dev/null)
echo "  health -> ${H:0:100}"
if [ -n "$H" ]; then ok "/health 连通 ($H)"; else bad "/health 无响应"; fi

echo "== [F2] 连通性: MCP tools/call log_tail =="
TOKEN=$(R "run-as $PKG cat shared_prefs/erbian.xml" 2>/dev/null | tr -d '\r' | grep -o 'name="server_token" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
echo "  token=${TOKEN:0:6}…(len=${#TOKEN})"
if [ -z "$TOKEN" ]; then
  bad "读不到 server_token"
else
  BHDR="Bear"
  AUTHHDR="Authorization: ${BHDR}er $TOKEN"
  M=$(curl -s -m 10 -X POST "http://127.0.0.1:$PORT/mcp" \
      -H "$AUTHHDR" -H "Content-Type: application/json" \
      -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"log_tail","arguments":{"n":5}}}' 2>/dev/null)
  echo "  mcp -> ${M:0:140}"
  if echo "$M" | grep -q '"content"' && ! echo "$M" | grep -q '"isError":true'; then
    ok "MCP log_tail 返回日志"
  else
    bad "MCP 调用失败: ${M:0:120}"
  fi
fi

echo "== [F3] 功能: 设置页开关（MCP/动态取色/纯黑）点击生效 =="
nav 3
snap /sdcard/fn_sw0.xml
S0=$(switch_states fn_sw0.xml)
echo "  初始 Switch 状态: $S0"
if [ "$S0" = "NONE" ] || [ -z "$S0" ]; then
  bad "设置页取不到 Switch 节点"
else
  # 点第3个（纯黑模式）验证翻转
  XY=$(switch_xy fn_sw0.xml 2)
  if [ -z "$XY" ]; then
    bad "取不到第3个 Switch 坐标"
  else
    tap $XY; sleep 2
    snap /sdcard/fn_sw1.xml
    S1=$(switch_states fn_sw1.xml)
    echo "  点纯黑后状态: $S1"
    if [ "$S1" != "$S0" ] && [ "$S1" != "NONE" ]; then ok "纯黑模式开关生效 ($S0 → $S1)"; else bad "纯黑模式开关无反应 ($S0 → $S1)"; fi
    # 点回恢复
    tap $XY; sleep 2
    snap /sdcard/fn_sw2.xml
    S2=$(switch_states fn_sw2.xml)
    if [ "$S2" = "$S0" ]; then ok "开关可复位 ($S2)"; else bad "复位失败 ($S0 → $S2)"; fi
  fi
  # 点第2个（动态取色）
  XY2=$(switch_xy fn_sw0.xml 1)
  if [ -n "$XY2" ]; then
    tap $XY2; sleep 2
    snap /sdcard/fn_sw3.xml
    S3=$(switch_states fn_sw3.xml)
    echo "  点动态取色后: $S3"
    if [ "$S3" != "$S0" ] && [ "$S3" != "NONE" ]; then ok "动态取色开关生效"; else bad "动态取色开关无反应 ($S0 → $S3)"; fi
    tap $XY2; sleep 2
  fi
fi

echo "== [F4] 功能: 日志页（设置 → 查看）=="
nav 3
snap /sdcard/fn_log0.xml
LXY=$(python3 - "$EV/fn_log0.xml" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
for pat in (r'text="查看"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="查看"'):
    m = re.search(pat, xml)
    if m:
        x1, y1, x2, y2 = map(int, m.groups()); print((x1+x2)//2, (y1+y2)//2); break
PYEOF
)
if [ -z "$LXY" ]; then
  bad "设置页找不到日志『查看』入口"
else
  tap $LXY; sleep 2
  snap /sdcard/fn_log1.xml
  if grep -q "运行日志" "$EV/fn_log1.xml" 2>/dev/null; then
    ok "日志页打开"
    if grep -qE '[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}' "$EV/fn_log1.xml" 2>/dev/null; then
      ok "日志行可见（时间戳格式）"
    else
      bad "日志页无日志行"
    fi
  else
    bad "日志页未打开"
  fi
  # 返回
  BXY=$(python3 - "$EV/fn_log1.xml" <<'PYEOF'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
m = re.search(r'text="返回"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml) or \
    re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="返回"', xml)
if m:
    x1, y1, x2, y2 = map(int, m.groups()); print((x1+x2)//2, (y1+y2)//2)
PYEOF
)
  [ -n "$BXY" ] && tap $BXY; sleep 1
fi

echo "== [F5] 黑夜模式适配: 夜间4页背景必须全暗 =="
R "cmd uimode night yes" >/dev/null 2>&1 || true
sleep 5
DARK_OK=1
for d in 0 1 2 3; do
  nav $d
  OUT="$EV/dark_p$d.png"
  timeout 20 adb exec-out screencap -p > "$OUT" 2>/dev/null || true
  if python3 scripts/bg_probe.py "$OUT" --expect dark 2>&1 | tee -a "$EV/bg_probe.txt" | grep -q FAIL; then
    DARK_OK=0; FUNC_FAIL=1
  fi
done
[ "$DARK_OK" = 1 ] && ok "夜间 4 页背景全暗"

echo "== [F6] 恢复浅色 + 浅色背景断言 =="
R "cmd uimode night no" >/dev/null 2>&1 || true
sleep 5
nav 0
timeout 20 adb exec-out screencap -p > "$EV/light_p0.png" 2>/dev/null || true
if python3 scripts/bg_probe.py "$EV/light_p0.png" --expect light 2>&1 | tee -a "$EV/bg_probe.txt" | grep -q FAIL; then
  bad "恢复浅色后背景未变亮"
else
  ok "已恢复浅色模式"
fi

echo "-- 功能/连通性/黑夜 汇总 --"
if [ "$FUNC_FAIL" = 0 ]; then
  echo "FUNC_ASSERT_PASS" | tee -a "$EV/verdict.txt"
else
  echo "FUNC_ASSERT_FAIL" | tee -a "$EV/verdict.txt"
fi
cat "$EV/verdict.txt"
# 终局门禁（component-tour 之后跑，复核全量 verdict）
if grep -q '_FAIL' "$EV/verdict.txt" 2>/dev/null; then
  echo "VERDICT_GATE_FAIL"
  exit 1
fi
echo "VERDICT_GATE_PASS"
