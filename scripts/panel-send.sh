#!/data/data/com.termux/files/usr/bin/bash
# 真机面板自动发送: panel-send.sh "文字"
set -e
TEXT="${1:?text}"
RISH="$HOME/rish/rish_t"
[ -n "$TEXT" ] || exit 2

run() { echo "$1" | timeout 40 "$RISH" 2>/dev/null; }

run "am start -a android.intent.action.ASSIST -n com.lantianhcgp.erbian/.ui.MainActivity > /dev/null"
sleep 3
run "uiautomator dump /sdcard/ps1.xml > /dev/null 2>&1"
XML=$(run "cat /sdcard/ps1.xml")

center() { # $1=xml文件(设备端) $2=attr匹配正则
  echo "$XML" | python3 -c "
import re, sys
xml = sys.stdin.read()
pat = sys.argv[1]
m = re.search(pat + r'[^/]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
if m:
    x1,y1,x2,y2 = map(int, m.groups())
    print((x1+x2)//2, (y1+y2)//2)
" "$2"
}

IN=$(echo "$XML" | python3 -c "
import re, sys
xml = sys.stdin.read()
m = re.search(r'text=\"说点什么\"[^/]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
if m:
    x1,y1,x2,y2 = map(int, m.groups())
    print((x1+x2)//2, (y1+y2)//2)
")
[ -n "$IN" ] || { echo "NO_INPUT_FIELD"; exit 3; }
echo "input at $IN"
run "input tap $IN"
sleep 1
run "input text $TEXT"
sleep 1
run "uiautomator dump /sdcard/ps2.xml > /dev/null 2>&1"
XML2=$(run "cat /sdcard/ps2.xml")
SEND=$(echo "$XML2" | python3 -c "
import re, sys
xml = sys.stdin.read()
m = re.search(r'content-desc=\"发送\"[^/]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml)
if m:
    x1,y1,x2,y2 = map(int, m.groups())
    print((x1+x2)//2, (y1+y2)//2)
")
[ -n "$SEND" ] || { echo "NO_SEND_BTN"; exit 4; }
echo "send at $SEND"
run "input tap $SEND"
sleep 3
run "uiautomator dump /sdcard/ps3.xml > /dev/null 2>&1"
run "cat /sdcard/ps3.xml" | grep -o "$TEXT" | head -1 && echo "BUBBLE_SEEN" || echo "BUBBLE_NOT_FOUND"
