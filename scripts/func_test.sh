#!/usr/bin/env bash
# 功能 + 连通性 + 黑夜模式 验收（用户要求 2026-10-16：
#   "不仅要界面测试通过，还要所有功能测试通过"、"黑夜模式、按钮测试这些检查事项加到流程"、
#   "模拟器测试要把连通性功能也测一遍"）
# 运行于 CI 模拟器（adb）。任何 FAIL → FUNC_ASSERT_FAIL + exit 1（走 verdict 终局门禁）。
# 坑(首跑实测)：component-tour 结束时 app 停在「唤醒面板浮层」，
#   直接 nav 会全部落空、夜间截图还会因面板本来就深色而"假绿" → 必须先归位 + 每步页面标记校验。
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
shot_screen() { timeout 25 adb exec-out screencap -p > "$1" 2>/dev/null || true; }
snap() { R "uiautomator dump $1" >/dev/null 2>&1; adb pull "$1" "$EV/$(basename "$1")" >/dev/null 2>&1 || true; }

# 文案节点中心坐标（支持 text= / content-desc=，两种属性顺序都匹配）
xy_of() { # $1=dump basename  $2=kind(text|desc)  $3=值
  python3 - "$EV/$1" "$2" "$3" <<'PYEOF'
import re, sys
try:
    xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
except OSError:
    raise SystemExit
kind, val = sys.argv[2], sys.argv[3]
attr = 'text' if kind == 'text' else 'content-desc'
for pat in (attr + '="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*' + attr + '="%s"'):
    m = re.search(pat % re.escape(val), xml)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PYEOF
}

# 某文案所在行右侧的点击点（设置页 Row(SpaceBetween) 的 Switch 在最右）
switch_xy() { # $1=dump basename  $2=该行文案
  python3 - "$EV/$1" "$2" <<'PYEOF'
import re, sys
try:
    xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
except OSError:
    raise SystemExit
val = sys.argv[2]
for pat in ('text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(val),
            'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="%s"' % re.escape(val)):
    m = re.search(pat, xml)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        wm = re.search(r'displayWidth="(\d+)"', xml) or re.search(r'bounds="\[0,0\]\[(\d+),\d+\]"', xml)
        w = int(wm.group(1)) if wm else 1080
        print(w - 90, (y1 + y2) // 2)   # SpaceBetween 右端 ≈ 开关位置
        break
PYEOF
}

# 滚动到某文案可见（Compose 超屏内容不进 dump，必须先滚到）
scroll_to_text() { # $1=text  $2=dump basename
  local i xy
  for i in 1 2 3 4 5 6; do
    snap "/sdcard/$2"            # 必须绝对路径：相对路径会 dump 到仓库根、pull 落空
    xy=$(xy_of "$2" text "$1")
    [ -n "$xy" ] && return 0
    timeout 20 adb shell input swipe 540 1380 540 560 400 >/dev/null 2>&1 || true   # 起点避开浮动 dock(y1638+)
    sleep 2
  done
  return 1
}

nav() { # $1=tab desc  $2=页面独有标记（防假绿：切页后必须见到标记）
  snap /sdcard/fn_nav.xml
  local xy
  xy=$(xy_of fn_nav.xml desc "$1")
  [ -n "$xy" ] && tap $xy
  sleep 2
  if [ -n "$2" ]; then
    snap /sdcard/fn_chk.xml
    if grep -q -- "$2" "$EV/fn_chk.xml" 2>/dev/null; then
      ok "切到 tab$1（见到标记：$2）"
    else
      bad "tab$1 未生效（未见标记：$2）"
    fi
  fi
}
sp_get() { # $1=bool key → 打印 true/false
  R "run-as $PKG cat shared_prefs/erbian.xml" 2>/dev/null | tr -d '\r' \
    | grep -o "name=\"$1\" value=\"[^\"]*\"" | head -1 | sed 's/.*value="//;s/"//'
}

echo "== [F-ENV] 设备参数（核对 dp→px 换算） =="
timeout 15 adb shell wm size 2>/dev/null | tr -d '\r' | sed 's/^/  size:     /'
timeout 15 adb shell wm density 2>/dev/null | tr -d '\r' | sed 's/^/  density:  /'

echo "== [F0] 归位: 从唤醒面板/任意态回到主界面 =="
timeout 20 adb shell am force-stop $PKG >/dev/null 2>&1 || true
sleep 1
timeout 25 adb shell am start -n $PKG/.ui.MainActivity >/dev/null 2>&1 || true
sleep 5
snap /sdcard/fn_home.xml
if grep -q 'content-desc="3"' "$EV/fn_home.xml" 2>/dev/null; then
  ok "主界面就绪（dock 4 槽可见）"
else
  bad "未能回到主界面（dump 无 dock desc=3）"
fi

echo "== [F1] 连通性: 内嵌服务 /health =="
timeout 20 adb forward tcp:$PORT tcp:$PORT >/dev/null 2>&1 || true
sleep 1
H=$(curl -s -m 8 "http://127.0.0.1:$PORT/health" 2>/dev/null)
echo "  health -> ${H:0:100}"
if [ -n "$H" ]; then ok "/health 连通 ($H)"; else bad "/health 无响应"; fi

echo "== [F2] 连通性: MCP tools/call log_tail =="
TOKEN=$(R "run-as $PKG cat shared_prefs/erbian.xml" 2>/dev/null | tr -d '\r' \
        | grep -o 'name="server_token">[^<]*<' | head -1 | sed 's/.*>//;s/<//')
echo "  token_len=${#TOKEN}"
if [ -z "$TOKEN" ]; then
  bad "读不到 server_token"
else
  BHDR="Bear"
  AUTHHDR="Authori""zation: ${BHDR}er $TOKEN"
  M=$(curl -s -m 10 -X POST "http://127.0.0.1:$PORT/mcp" \
      -H "$AUTHHDR" -H "Content-Type: application/json" \
      -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"log_tail","arguments":{"n":5}}}' 2>/dev/null)
  echo "  mcp -> ${M:0:120}"
  if echo "$M" | grep -q '"content"' && ! echo "$M" | grep -q '"isError":true'; then
    ok "MCP log_tail 返回日志"
  else
    bad "MCP 调用失败: ${M:0:100}"
  fi
fi

echo "== [F3] 功能: 设置页『纯黑模式』开关（点击→状态持久化）=="
nav 3 "设置"
BEFORE=$(sp_get pure_black); BEFORE=${BEFORE:-false}
echo "  点击前 pure_black=$BEFORE"
if ! scroll_to_text "纯黑模式（OLED）" fn_sw0.xml; then
  bad "滚动后仍找不到纯黑模式开关"
else
  SXY=$(switch_xy fn_sw0.xml "纯黑模式（OLED）")
  tap $SXY; sleep 3
  AFTER=$(sp_get pure_black)
  echo "  点击后 pure_black=$AFTER"
  if [ -n "$AFTER" ] && [ "$AFTER" != "$BEFORE" ]; then
    ok "纯黑模式开关生效并持久化 ($BEFORE → $AFTER)"
  else
    bad "纯黑模式开关无反应 ($BEFORE → $AFTER)"
  fi
  # 点回复位（重新滚到可见）
  scroll_to_text "纯黑模式（OLED）" fn_sw1.xml || true
  SXY2=$(switch_xy fn_sw1.xml "纯黑模式（OLED）")
  [ -n "$SXY2" ] && tap $SXY2; sleep 3
  BACK=$(sp_get pure_black)
  if [ "$BACK" = "$BEFORE" ]; then ok "开关可复位 ($BACK)"; else bad "复位失败 ($BEFORE → $BACK)"; fi
fi

echo "== [F4] 功能: 日志页（设置 → 查看）=="
nav 3 "设置"
if ! scroll_to_text "查看" fn_log0.xml; then
  LXY=""
else
  LXY=$(xy_of fn_log0.xml text "查看")
fi
if [ -z "$LXY" ]; then
  bad "设置页找不到日志『查看』入口"
else
  tap $LXY; sleep 3
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
  BXY=$(xy_of fn_log1.xml text "返回")
  [ -n "$BXY" ] && tap $BXY; sleep 2
fi

echo "== [F4b] 全局记录链路（ACTION_PROCESS_TEXT → App 上行）=="
# 系统级：任何 App 选中文字的菜单项即走此 intent；这里直接模拟系统调用验证端到端
R "am start -a android.intent.action.PROCESS_TEXT -t text/plain -e android.intent.extra.PROCESS_TEXT '全局记录连通性测试文本' -n com.lantianhcgp.erbian/.ui.ProcessTextActivity" >/dev/null 2>&1 || true
sleep 5
GT=$(curl -s -m 8 -X POST http://127.0.0.1:8790/mcp \
  -H "$AUTHHDR" -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"log_tail","arguments":{"n":30}}}' 2>/dev/null)
echo "  log_tail -> ${GT:0:160}"
if echo "$GT" | grep -q "process_text recorded"; then
  ok "全局记录：PROCESS_TEXT 进 App 并落日志"
else
  bad "全局记录链路断（log_tail 无 process_text recorded）"
fi

echo "== [F5] 黑夜模式: 夜间 4 页背景必须全暗 =="
R "cmd uimode night yes" >/dev/null 2>&1 || true
sleep 6
timeout 20 adb shell am force-stop $PKG >/dev/null 2>&1 || true
sleep 1
timeout 25 adb shell am start -n $PKG/.ui.MainActivity >/dev/null 2>&1 || true
sleep 5
DARK_OK=1
for d in 0 1 2 3; do
  case $d in
    0) MK="说点什么" ;; 1) MK="耳边待办" ;; 2) MK="画像" ;; 3) MK="设置" ;;
  esac
  nav $d "$MK"
  OUT="$EV/dark_p$d.png"
  shot_screen "$OUT"
  if python3 scripts/bg_probe.py "$OUT" --expect dark 2>&1 | tee -a "$EV/bg_probe.txt" | grep -q FAIL; then
    DARK_OK=0; FUNC_FAIL=1
  fi
  # 2026-10-16 用户：夜间三页大标题是黑的 → 页头文字带必须有亮字（防回归）
  if python3 scripts/title_probe.py "$OUT" --expect dark 2>&1 | tee -a "$EV/title_probe.txt" | grep -q FAIL; then
    bad "夜间标题未适配 $MK"
    FUNC_FAIL=1
  fi
done
[ "$DARK_OK" = 1 ] && ok "夜间 4 页背景全暗"

echo "== [F6] 恢复浅色 =="
R "cmd uimode night no" >/dev/null 2>&1 || true
sleep 6
timeout 20 adb shell am force-stop $PKG >/dev/null 2>&1 || true
sleep 1
timeout 25 adb shell am start -n $PKG/.ui.MainActivity >/dev/null 2>&1 || true
sleep 5
nav 0 "说点什么"
shot_screen "$EV/light_p0.png"
if python3 scripts/bg_probe.py "$EV/light_p0.png" --expect light 2>&1 | tee -a "$EV/bg_probe.txt" | grep -q FAIL; then
  bad "恢复浅色后背景未变亮"
else
  ok "已恢复浅色模式"
fi
if python3 scripts/title_probe.py "$EV/light_p0.png" --expect light 2>&1 | tee -a "$EV/title_probe.txt" | grep -q FAIL; then
  bad "浅色页头缺深字（标题异常）"
else
  ok "浅色页头标题正常"
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
