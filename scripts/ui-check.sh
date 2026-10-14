#!/usr/bin/env bash
# uiautomator 级 UI 验收: ui-check.sh
# 导航点击通过 contentDescription("0".."3") 节点坐标（不猜像素）。
EV=build/acceptance
mkdir -p "$EV"
ADB="adb"
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

# 找 contentDescription=index 的节点中心坐标
bounds_of_desc() { # $1=dump $2=desc -> "cx cy"
  python3 - "$2" <<'PYEOF'
import re, sys, html
desc = sys.argv[1]
try:
    xml = open('/tmp/ui_live.xml', encoding='utf-8', errors='ignore').read()
except Exception:
    xml = ''
m = None
for mm in re.finditer(r'content-desc="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if html.unescape(mm.group(1)) == desc:
        m = mm
if not m:
    for mm in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="([^"]*)"', xml):
    # second pattern unlikely; keep simple
        pass
if m:
    x1,y1,x2,y2 = map(int, m.groups()[1:5])
    print((x1+x2)//2, (y1+y2)//2)
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

# 2 发送出泡（点输入框 y 用 input 节点、ENTER 发送）
printf '%s' "$DUMP" > /tmp/ui_live.xml
INP=$(python3 - <<'PYEOF'
import re, html
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
  timeout 15 $ADB shell input keyevent 66
  sleep 2
fi
DUMP=$(dump)
echo "-- after send --" >> "$D"
if has "$DUMP" "ceshi_jilu"; then echo "OK: send bubble" >> "$D"; else echo "MISS: send bubble" >> "$D"; FAIL=1; fi

# 3 导航三页
for item in "1:耳边待办" "2:刷新" "3:唤醒状态"; do
  idx=${item%%:*}; marker=${item#*:}
  tap_page "$idx" || true
  sleep 2
  DUMP=$(dump)
  printf '%s' "$DUMP" > "$EV/nav$idx.xml"
  echo "-- nav $idx --" >> "$D"
  if has "$DUMP" "$marker"; then echo "OK: $marker" >> "$D"; else
    echo "MISS: $marker" >> "$D"; FAIL=1
    echo "-- texts on screen after tap $idx:" >> "$D"
    printf '%s' "$DUMP" | grep -o 'text="[^"]*"' | sort -u | head -15 >> "$D"
  fi
done

tap_page "0" || true
sleep 2

if [ "$FAIL" = 0 ]; then echo "UI_CHECK_PASS" | tee "$EV/verdict.txt"; else echo "UI_CHECK_FAIL" | tee "$EV/verdict.txt"; fi
cat "$D"
