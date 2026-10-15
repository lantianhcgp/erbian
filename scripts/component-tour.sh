#!/data/data/com.termux/files/usr/bin/bash
# 组件级巡检（用户要求 2026-10-15）：以组件为单位逐状态截图，供 agent 放大逐一目检
# 状态: T01频道 T02输入中 T03已发送+气泡 T04长按菜单 T05日程 T06画像(样本卡+展开) T07设置 T08唤醒面板 T09面板输入中
cd "$(dirname "$0")/.."
EV=build/acceptance
ADB="adb"
BD_TMP=/tmp/bd.xml
PKG=com.lantianhcgp.erbian
mkdir -p "$EV"
source scripts/shot.sh

# 设备命令双模：手机上用 rish，CI runner 上用 adb（r​​ish 不存在曾致全链静默空跑）
R() {
  if [ -x "$HOME/rish/rish_t" ]; then
    echo "$1" | timeout 40 "$HOME/rish/rish_t" 2>/dev/null || true
  else
    # 坑链终局: sh -c 包裹被拆参；不加引号管道在本地跑；加引号(参数模式)adb 转义引号致
    # "Error type 3"(组件名带引号)。正解=stdin 模式，与 ui-check 一直全中的 run() 同款：
    echo "$1" | timeout 40 adb shell 2>/dev/null || true
  fi
}

bounds_desc() { # $1=dump-file-on-device $2=selector like: desc=1 / text=说点什么
  # 配方照抄 ui-check 一直全中的路径: python 正则 + 本地文件（grep 路线在 1.19MB 单行 XML 上屡屡空手，弃）
  local sel="$2"
  if [ -x "$HOME/rish/rish_t" ]; then
    R "cat $1" > "$BD_TMP" 2>/dev/null || true
  else
    adb pull "$1" "$BD_TMP" >/dev/null 2>&1 || true
  fi
  echo "    bd dbg $(wc -c < "$BD_TMP" 2>/dev/null)B file for $1" >&2
  # 证据随包：把设备 dump 也拉进 artifact（agent 本地亲验用）
  cp "$BD_TMP" "$EV/$(basename $1)" 2>/dev/null || true
  python3 - "$sel" "$BD_TMP" <<'PYEOF'
import re, sys, html
sel, path = sys.argv[1], sys.argv[2]
key, _, val = sel.partition('=')
attr = {'desc': 'content-desc', 'text': 'text'}.get(key)
if not attr:
    sys.exit(0)
try:
    xml = open(path, encoding='utf-8', errors='ignore').read()
except OSError:
    sys.exit(0)
pat = re.escape(attr) + r'="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
for mm in re.finditer(pat, xml):
    if html.unescape(mm.group(1)) == val:
        x1, y1, x2, y2 = map(int, mm.groups()[1:])
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PYEOF
}

tap_sel() { # $1=dump $2=selector
  local b cx cy
  b=$(bounds_desc "$1" "$2")
  [ -z "$b" ] && { echo "  tap_sel miss: $2"; return 1; }
  cx=$(( (${b:1:4}%%,*} + ${b%%,*} ... ) ))
}
# 上面写法太脆，改用 python 解析
tap_sel() {
  local b cx cy
  b=$(bounds_desc "$1" "$2")
  [ -z "$b" ] && { echo "  tap_sel miss: $2"; return 1; }
  cx=$(python3 -c "import sys,re; n=list(map(int,re.findall(r'\d+',sys.argv[1]))); print((n[0]+n[2])//2)" "$b")
  cy=$(python3 -c "import sys,re; n=list(map(int,re.findall(r'\d+',sys.argv[1]))); print((n[1]+n[3])//2)" "$b")
  R "input tap $cx $cy"
  echo "  tap $2 -> $cx $cy"
}

dump_dev() { # $1=设备路径；wc -c 取真字节，重试3次，永不置败（set -e 已撤）
  local i out n=0
  for i in 1 2 3; do
    out=$(R "uiautomator dump $1" 2>&1 | tr -d '\r')
    n=$(R "wc -c $1" 2>/dev/null | tr -d '\r' | tr -dc '0-9')
    if [ "${n:-0}" -gt 1000 ] 2>/dev/null; then
      echo "  dump $1 bytes=$n (${out:0:40})"
      return 0
    fi
    sleep 2
  done
  echo "  dump FAIL $1 bytes=${n:-0} out=${out:0:60}"
  return 0
}

launch() { # 确保 App 真在前台。start/force-stop 用与 ui-check 逐字节相同的多参数直呼
  # （R 的 echo/stdin/引号三种形态都踩过 adb 的坑: 拆参/本地管道/转义引号→Error type 3）
  timeout 20 adb shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
  sleep 1
  timeout 20 adb shell wm dismiss-keyguard 2>/dev/null || true
  timeout 20 adb shell am force-stop $PKG 2>/dev/null || true
  sleep 1
  local i pkg out
  for i in 1 2 3 4 5 6; do
    out=$(timeout 30 adb shell am start -n $PKG/.ui.MainActivity 2>&1 | tr -d '\r' | grep -E 'Status|Error|Warning|Starting' | head -2)
    sleep 3
    pkg=$(timeout 20 adb shell dumpsys window | tr -d '\r' | grep -m1 mCurrentFocus | grep -o 'com\.[a-zA-Z.]*' | head -1)
    echo "  launch try$i focus=$pkg | am=$out"
    case "$pkg" in *lantianhcgp.erbian*) return 0 ;; esac
    if [ $i -eq 1 ]; then
      kg=$(timeout 20 adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -iE "mShowingLockscreen|isKeyguardShowing|KeyguardController|mKeyguard" | head -4)
      dko=$(timeout 20 adb shell wm dismiss-keyguard 2>&1 | tr -d '\r' | head -1)
      echo "  kg-state: $kg | dismiss-out=$dko"
      timeout 25 adb logcat -d -b crash -t 60 2>/dev/null | tr -d '\r' | grep -B2 -A18 "erbian" | head -40 > "$EV/crash_at_launch.txt" || true
      echo "  crash-archived lines=$(wc -l < "$EV/crash_at_launch.txt" 2>/dev/null || echo 0)"
    fi
    timeout 20 adb shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
    timeout 20 adb shell wm dismiss-keyguard 2>/dev/null || true
    timeout 20 adb shell input keyevent 82 2>/dev/null || true
    sleep 2
  done
  echo "  launch WARN: app not foreground after retries"
  return 1
}

echo "== tour: T01 channel base =="
launch
dump_dev /sdcard/t01.xml
shot "$EV/T01-channel.png" || true

echo "== tour: T02 input typing (keyboard open) =="
tap_sel /sdcard/t01.xml 'text=说点什么' || true
sleep 1
R "input text component-tour-ok"
sleep 1
dump_dev /sdcard/t02.xml
shot "$EV/T02-input-typing.png" || true

echo "== tour: T03 sent bubble =="
R "input keyevent 4"   # 收键盘
sleep 1
b=$(bounds_desc /sdcard/t02.xml 'content-desc=发送')
if [ -n "$b" ]; then
  cx=$(python3 -c "import sys,re; n=list(map(int,re.findall(r'\d+',sys.argv[1]))); print((n[0]+n[2])//2)" "$b")
  cy=$(python3 -c "import sys,re; n=list(map(int,re.findall(r'\d+',sys.argv[1]))); print((n[1]+n[3])//2)" "$b")
  R "input tap $cx $cy"
  echo "  send tap -> $cx $cy"
fi
sleep 3
dump_dev /sdcard/t03.xml
shot "$EV/T03-bubble.png" || true

echo "== tour: T04 long-press bubble menu =="
b=$(bounds_desc /sdcard/t03.xml 'text=component-tour-ok')
if [ -n "$b" ]; then
  cx=$(python3 -c "import sys,re; n=list(map(int,re.findall(r'\d+',sys.argv[1]))); print((n[0]+n[2])//2)" "$b")
  cy=$(python3 -c "import sys,re; n=list(map(int,re.findall(r'\d+',sys.argv[1]))); print((n[1]+n[3])//2)" "$b")
  R "input swipe $cx $cy $cx $cy 900"
  echo "  longpress -> $cx $cy"
fi
sleep 2
dump_dev /sdcard/t04.xml
shot "$EV/T04-longpress-menu.png" || true
R "input keyevent 4"
sleep 1

echo "== tour: T05 schedule =="
tap_sel /sdcard/t01.xml 'desc=1' || true
sleep 2
dump_dev /sdcard/t05.xml
shot "$EV/T05-schedule.png" || true

echo "== tour: T06 profile with seeded card + evidence expand =="
# 种样本卡（3条证据测折叠）
R "run-as $PKG sh -c 'cat > files/profile.json'" <<'CARD'
[{"category":"习惯","title":"英语听写需自带耳机","content":"听写前确认耳机，忘带会影响整节课节奏。","confidence":0.86,"evidence":["周三听写前确认耳机","周一提过忘带耳机","课代表职责提醒"]}]
CARD
sleep 1
R "am force-stop $PKG"; sleep 1; R "am start -n $PKG/.ui.MainActivity > /dev/null"; sleep 3
tap_sel /sdcard/t05.xml 'desc=2' || true
sleep 2
dump_dev /sdcard/t06.xml
shot "$EV/T06-profile-card.png" || true
tap_sel /sdcard/t06.xml 'text=点按展开' || tap_sel /sdcard/t06.xml 'text=共3条证据，点按展开' || true
sleep 1
dump_dev /sdcard/t06b.xml
shot "$EV/T06b-profile-expanded.png" || true

echo "== tour: T07 settings =="
tap_sel /sdcard/t06b.xml 'desc=3' || true
sleep 2
dump_dev /sdcard/t07.xml
shot "$EV/T07-settings.png" || true

echo "== tour: T08 wake panel =="
R "am force-stop $PKG"; sleep 1
R "am start -a android.intent.action.ASSIST -n $PKG/.ui.MainActivity > /dev/null"
sleep 4
dump_dev /sdcard/t08.xml
shot "$EV/T08-wake-panel.png" || true

echo "== tour: T09 wake panel typing =="
tap_sel /sdcard/t08.xml 'text=说点什么' || true
sleep 1
R "input text panel-typing"
sleep 1
dump_dev /sdcard/t09.xml
shot "$EV/T09-panel-typing.png" || true

echo "TOUR_DONE"
ls -la "$EV"/T*.png | awk '{print $5, $9}'
