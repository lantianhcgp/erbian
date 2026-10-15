#!/usr/bin/env bash
# uiautomator 级 UI 验收: ui-check.sh
# 导航点击通过 contentDescription("0".."3") 节点坐标（不猜像素）。
EV=build/acceptance
mkdir -p "$EV"
ADB="adb"
. "$(cd "$(dirname "$0")" && pwd)/shot.sh"
PKG=com.lantianhcgp.erbian
D=$EV/ui_check_detail.txt
: > "$D"

bash "$(dirname "$0")/wake-screen.sh"

SIZE=$($ADB shell wm size | grep -oE '[0-9]+x[0-9]+' | head -1)
W=${SIZE%x*}; H=${SIZE#*x}
echo "screen=${W}x${H}" | tee -a "$D"

dump() {
  timeout 40 $ADB shell uiautomator dump /sdcard/ui.xml > "$EV/ui_dump_out.txt" 2>&1 || true
  local X
  X=$($ADB shell cat /sdcard/ui.xml 2>/dev/null || true)
  [ -z "$X" ] && { echo "-- dump empty: $(cat "$EV/ui_dump_out.txt")" >> "$D"; }
  printf '%s' "$X"
}

has() { case "$1" in *"$2"*) return 0;; *) return 1;; esac; }

# 找 content-desc=index 的节点中心坐标（$1=desc）
bounds_of_desc() {
  python3 - "$1" <<'PYEOF'
import re, sys, html
desc = sys.argv[1]
xml = open('/tmp/ui_live.xml', encoding='utf-8', errors='ignore').read()
for mm in re.finditer(r'content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if html.unescape(mm.group(1)) == desc:
        x1, y1, x2, y2 = map(int, mm.groups()[1:5])
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PYEOF
}

tap_page() { # $1=index -> dump to /tmp, find bounds, tap
  local DUMP DUMPFILE
  DUMP=$(dump)
  printf '%s' "$DUMP" > /tmp/ui_live.xml
  local C
  C=$(bounds_of_desc "$1")
  if [ -z "$C" ]; then
    echo "  tap_page $1: node not found" >> "$D"
    return 1
  fi
  echo "  tap_page $1 -> $C" >> "$D"
  python3 - "$1" >> "$D" 2>/dev/null <<'PYEOF2'
import re, html, sys
xml=open('/tmp/ui_live.xml',encoding='utf-8',errors='ignore').read()
want=sys.argv[1]
for mm in re.finditer(r'content-desc="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    d=html.unescape(mm.group(1))
    if d in list("0123"):
        x1,y1,x2,y2=map(int,mm.groups()[1:5])
        mark=" <== target" if d==want else ""
        print(f"  desc={d} center=({(x1+x2)//2},{(y1+y2)//2}){mark}")
PYEOF2
  timeout 15 $ADB shell input tap $C
}

FAIL=0

timeout 20 $ADB shell am force-stop $PKG
sleep 1
timeout 20 $ADB shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 4

# 1 频道页
DUMP=$(dump)
echo "-- channel page --" >> "$D"
for m in "频道" "说点什么"; do
  if has "$DUMP" "$m"; then echo "OK: $m" >> "$D"; else echo "MISS: $m" >> "$D"; FAIL=1; fi
done

# 2 发送出泡：点输入框 → 输入 → 重新dump取「发送」键坐标 → 点击 → 验证清空+出泡
printf '%s' "$DUMP" > /tmp/ui_live.xml
INP=$(python3 - <<'PYEOF'
import re
xml=open('/tmp/ui_live.xml',encoding='utf-8',errors='ignore').read()
for mm in re.finditer(r'text="说点什么"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    x1,y1,x2,y2=map(int,mm.groups()); print((x1+x2)//2,(y1+y2)//2); break
PYEOF
)
if [ -n "$INP" ]; then
  timeout 15 $ADB shell input tap $INP
  sleep 1
  timeout 15 $ADB shell input text "ceshi_jilu"
  sleep 1
  # 键盘态重新取发送键坐标（ENTER 不是发送——曾是多轮假绿根源）
  DUMP=$(dump)
  printf '%s' "$DUMP" > /tmp/ui_live.xml
  SEND=$(python3 - <<'PYEOF'
import re
xml=open('/tmp/ui_live.xml',encoding='utf-8',errors='ignore').read()
for mm in re.finditer(r'content-desc="发送"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    x1,y1,x2,y2=map(int,mm.groups()); print((x1+x2)//2,(y1+y2)//2); break
PYEOF
)
  echo "  send btn at $SEND" >> "$D"
  if [ -n "$SEND" ]; then timeout 15 $ADB shell input tap $SEND; fi
  sleep 3
  timeout 15 $ADB shell input keyevent 4   # 收键盘（主页面 BACK 先关 IME）
  sleep 1
fi
DUMP=$(dump)
echo "-- after send --" >> "$D"
# 判定：输入框已清空(重新显示占位) 且 页面上存在刚输入的文本 = 真发送成功
if has "$DUMP" "说点什么" && has "$DUMP" "ceshi_jilu"; then echo "OK: send bubble" >> "$D"; else echo "MISS: send bubble" >> "$D"; FAIL=1; fi

# 3 导航三页
for item in "1:耳边待办" "2:刷新" "3:唤醒状态"; do
  idx=${item%%:*}; marker=${item#*:}
  tap_page "$idx" || true
  sleep 2
  DUMP=$(dump)
  printf '%s' "$DUMP" > "$EV/nav$idx.xml"
  # 每页视觉证据（文件式截图+亮度断言+自动重拍，见 shot.sh）
  shot "$EV/page$idx.png" || true
  echo "-- nav $idx --" >> "$D"
  if has "$DUMP" "$marker"; then echo "OK: $marker" >> "$D"; else
    echo "MISS: $marker" >> "$D"; FAIL=1
    echo "-- texts on screen after tap $idx:" >> "$D"
    printf '%s' "$DUMP" | grep -o 'text="[^"]*"' | sort -u | head -15 >> "$D"
  fi
done

tap_page "0" || true
sleep 2

# 布局像素门禁：dock 药丸尺寸越界即红（1712px 全屏巨物曾逃过目检 —— 2026-10-16）
LAYOUT_OK=1
for f in "$EV"/page*.png; do
  [ -f "$f" ] || continue
  r=$(python3 scripts/layout_probe.py "$f" 2>&1) || LAYOUT_OK=0
  echo "layout_probe $(basename "$f"): $r" | tee -a "$EV/layout_probe.txt"
done
if [ "$LAYOUT_OK" = 1 ]; then echo "LAYOUT_GATE_PASS" | tee -a "$EV/verdict.txt"; else echo "LAYOUT_GATE_FAIL" | tee -a "$EV/verdict.txt"; fi

if [ "$FAIL" = 0 ]; then echo "UI_CHECK_PASS" | tee -a "$EV/verdict.txt"; else echo "UI_CHECK_FAIL" | tee -a "$EV/verdict.txt"; fi
# 上行报文取证（发送后 uplink 约2s内打出）
sleep 6
if grep -q "webhooks/erbian-record" "$EV/uplink_received.txt" 2>/dev/null; then
  echo "UPLINK_PASS" | tee -a "$EV/verdict.txt"
else
  echo "UPLINK_FAIL" | tee -a "$EV/verdict.txt"
fi
cat "$EV/uplink_received.txt" >> "$D" 2>/dev/null || true
cat "$D"
