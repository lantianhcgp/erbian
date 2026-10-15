#!/data/data/com.termux/files/usr/bin/bash
# 真机面板自动发送: panel-send.sh "文字"（设备端 grep，避免大 XML 过 rish 被截断）
set -e
TEXT="${1:?text}"
RISH="$HOME/rish/rish_t"

run() { echo "$1" | timeout 40 "$RISH" 2>/dev/null; }

bounds() { # $1=设备端xml路径 $2=匹配串(设备端grep -o 前缀) -> "cx cy"
  run "cat $1 | grep -o '$2[^/]*bounds=\"\[[0-9,]*\]\[[0-9,]*\]\" | head -1" | python3 -c "
import re, sys
s = sys.stdin.read()
m = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)
if m:
    x1,y1,x2,y2 = map(int, m.groups())
    print((x1+x2)//2, (y1+y2)//2)
"
}

run "am start -a android.intent.action.ASSIST -n com.lantianhcgp.erbian/.ui.MainActivity > /dev/null"
sleep 5
IN=""
for i in 1 2 3; do
  run "uiautomator dump /sdcard/ps1.xml > /dev/null 2>&1" || true
  IN=$(bounds /sdcard/ps1.xml 'text="说点什么"')
  [ -n "$IN" ] && break
  sleep 2
done
[ -n "$IN" ] || { echo "NO_INPUT_FIELD"; exit 3; }
echo "input at $IN"
run "input tap $IN"
sleep 1
ESC=$(printf '%s' "$TEXT" | sed 's/ /\\\\ /g')
run "input text $ESC"
sleep 1
run "uiautomator dump /sdcard/ps2.xml > /dev/null 2>&1"
SEND=$(bounds /sdcard/ps2.xml 'content-desc="发送"')
[ -n "$SEND" ] || { echo "NO_SEND_BTN"; exit 4; }
echo "send at $SEND"
run "input tap $SEND"
sleep 3
run "uiautomator dump /sdcard/ps3.xml > /dev/null 2>&1"
run "cat /sdcard/ps3.xml | grep -c '已送达\|说点什么'" > /dev/null
run "cat /sdcard/ps3.xml" | grep -o "$TEXT" | head -1 && echo "BUBBLE_SEEN" || echo "BUBBLE_CHECK_SKIPPED"
