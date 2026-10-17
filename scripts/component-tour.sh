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
  # 坑(2026-10-16): bounds_desc 已改为直接输出中心点 "cx cy"(2个数)，旧版这里按 4 坐标
  # 格式取 n[2] → IndexError → cx/cy 空 → input tap 空操作 → 9帧7张同画还全绿。
  local b
  b=$(bounds_desc "$1" "$2")
  if [ -z "$b" ]; then echo "  tap_sel miss: $2"; return 1; fi
  case "$b" in
    *[!0-9\ ]*) echo "  tap_sel BAD b=[$b] $2"; return 1 ;;
  esac
  R "input tap $b"
  echo "  tap $2 -> $b"
}

pull_dump() { # $1=设备路径 → 本地 $EV/$(basename)（状态断言要用本地文件）
  if [ -x "$HOME/rish/rish_t" ]; then
    R "cat $1" > "$EV/$(basename "$1")" 2>/dev/null || true
  else
    adb pull "$1" "$EV/$(basename "$1")" >/dev/null 2>&1 || true
  fi
}

# 键盘检测必须走 dumpsys（2026-10-16 实测: uiautomator dump 不含 IME 窗口，
# 旧的 dump 里 grep inputmethod 恒为假 → BACK 从未执行 → 键盘连压 T03~T05 三步、
# dock 点击全落键盘上，页面断言只靠层级 diff 还假通过）
close_ime() {
  local i s
  for i in 1 2 3; do
    s=$(R "dumpsys input_method" | tr -d '\r')
    if echo "$s" | grep -q "mInputShown=true"; then
      echo "  close_ime: 键盘在前台, BACK 收起"
      R "input keyevent 4"
      sleep 1
      continue
    fi
    echo "$s" | grep -q "mInputShown" && return 0
    return 0  # 字段缺失（新 ROM 改名）时无法判断，交给页面级断言兜底
  done
}

dump_dev() { # $1=设备路径；wc -c 取真字节，重试3次，永不置败（set -e 已撤）
  local i out n=0
  for i in 1 2 3; do
    out=$(R "uiautomator dump $1" 2>&1 | tr -d '\r')
    n=$(R "wc -c $1" 2>/dev/null | tr -d '\r' | tr -dc '0-9')
    if [ "${n:-0}" -gt 1000 ] 2>/dev/null; then
      echo "  dump $1 bytes=$n (${out:0:40})"
      pull_dump "$1"
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
      pmp=$(timeout 20 adb shell pm path $PKG 2>&1 | tr -d '\r' | head -2)
      acts=$(timeout 25 adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' | grep -iE "erbian|topResumed" | head -6)
      lcz=$(timeout 20 adb logcat -d -t 300 2>/dev/null | tr -d '\r' | grep -iE "erbian|ActivityTaskManager.*START" | grep -viE "chatty" | tail -12)
      { echo "pm-path: $pmp"; echo "activities: $acts"; echo "logcat: $lcz"; } > "$EV/launch_probe.txt"
      echo "  probe archived"
    fi
    timeout 20 adb shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
    timeout 20 adb shell wm dismiss-keyguard 2>/dev/null || true
    timeout 20 adb shell input keyevent 82 2>/dev/null || true
    sleep 2
  done
  echo "  launch WARN: app not foreground after retries"
  return 1
}

# 巡检自装（实测: instrumentation 收尾会卸掉主包 — pm path 空/START -92/Invalid packageName）
APK=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$APK" ]; then
  timeout 180 adb install -r "$APK" > "$EV/tour_install.txt" 2>&1 || true
else
  echo "APK missing: $APK" > "$EV/tour_install.txt"
fi
echo "tour-install: $(tail -1 "$EV/tour_install.txt" 2>/dev/null)"
pp=$(timeout 20 adb shell pm path $PKG 2>/dev/null | tr -d '\r' | head -1)
echo "pm-path-after-install: $pp"
# 预授权日历：instrumentation 收尾卸主包 → 巡检重装=全新包 → 权限重置 →
# SchedulePage 首次组合弹系统对话框占满 dump，T05~T07 连环假/红（2026-10-16 实测）
timeout 20 adb shell pm grant $PKG android.permission.READ_CALENDAR > "$EV/grant.txt" 2>&1 || true
timeout 20 adb shell pm grant $PKG android.permission.WRITE_CALENDAR >> "$EV/grant.txt" 2>&1 || true
echo "grant: $(tr -d '\r' < "$EV/grant.txt" | grep -v '^$' | tail -2 | tr '\n' ';')"

# ---------- 状态断言（2026-10-16 加） ----------
# 背景: 上一轮 CI 9 帧里 7 张是同一画面(dump md5 全同)却全绿 —— 点击空操作 + 零断言。
# 原则: 每步必须证明"页面/状态真的变了"，任何一条失败 → TOUR_ASSERT_FAIL + exit 1。
TOUR_FAIL=0

dump_has() { # $1=dump basename(在$EV) $2=needle $3=label —— 层级里必须出现标记
  local f="$EV/$1"
  if [ ! -s "$f" ]; then echo "  ASSERT FAIL $3 (missing $1)"; TOUR_FAIL=1; return 1; fi
  if python3 - "$f" "$2" <<'PYEOF'
import sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
sys.exit(0 if sys.argv[2] in xml else 1)
PYEOF
  then echo "  ASSERT OK   $3"; else echo "  ASSERT FAIL $3 (marker not in $1: $2)"; TOUR_FAIL=1; return 1; fi
}

dump_lacks() { # 与 dump_has 相反：层级里不该出现（如折叠态不该露出证据正文）
  local f="$EV/$1"
  if [ ! -s "$f" ]; then echo "  ASSERT FAIL $3 (missing $1)"; TOUR_FAIL=1; return 1; fi
  if python3 - "$f" "$2" <<'PYEOF'
import sys
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
sys.exit(1 if sys.argv[2] in xml else 0)
PYEOF
  then echo "  ASSERT OK   $3"; else echo "  ASSERT FAIL $3 (不该出现却出现: $2)"; TOUR_FAIL=1; return 1; fi
}

dump_diff() { # $1=dumpA $2=dumpB $3=label —— 两状态层级必须不同（同 md5 = 没切页）
  local a b
  a=$(md5sum "$EV/$1" 2>/dev/null | cut -d' ' -f1)
  b=$(md5sum "$EV/$2" 2>/dev/null | cut -d' ' -f1)
  if [ -n "$a" ] && [ -n "$b" ] && [ "$a" != "$b" ]; then echo "  ASSERT OK   $3"; else echo "  ASSERT FAIL $3 ($1 与 $2 层级相同)"; TOUR_FAIL=1; fi
}

png_diff() { # $1=pngA $2=pngB $3=label —— 帧字节级必须不同（状态栏时钟是唯一合法差异源）
  local a b
  a=$(md5sum "$EV/$1" 2>/dev/null | cut -d' ' -f1)
  b=$(md5sum "$EV/$2" 2>/dev/null | cut -d' ' -f1)
  if [ -n "$a" ] && [ -n "$b" ] && [ "$a" != "$b" ]; then echo "  ASSERT OK   $3"; else echo "  ASSERT FAIL $3 ($1 与 $2 字节相同)"; TOUR_FAIL=1; fi
}

echo "== tour: T01 channel base =="
launch
dump_dev /sdcard/t01.xml
shot "$EV/T01-channel.png" || true
# dock 图标可见性门禁：四槽图标区都得有像素（未选中图标被 layer alpha 隐身的回归，2026-10-16）
python3 scripts/dock_icons_probe.py "$EV/T01-channel.png" || TOUR_FAIL=1

echo "== tour: T02 input typing (keyboard open) =="
tap_sel /sdcard/t01.xml 'text=说点什么' || true
sleep 1
R "input text component-tour-ok"
sleep 1
dump_dev /sdcard/t02.xml
shot "$EV/T02-input-typing.png" || true
dump_has t02.xml 'component-tour-ok' 'T02 键入文本已进输入框'
dump_diff t01.xml t02.xml 'T02 与 T01 层级不同(键盘/输入态)'

echo "== tour: T03 sent bubble =="
close_ime   # dumpsys 判定收键盘；旧版 grep dump 判 IME 恒假，键盘连压三步
# 键盘收起后布局会整体回移，必须重取 dump 再找发送键
dump_dev /sdcard/t02c.xml
b=$(bounds_desc /sdcard/t02c.xml 'desc=发送')
[ -z "$b" ] && b=$(bounds_desc /sdcard/t02.xml 'desc=发送')
if [ -n "$b" ]; then
  R "input tap $b"   # b 已是 "cx cy" 中心点（bounds_desc 直接输出）
  echo "  send tap -> $b"
else
  echo "  ASSERT FAIL T03 找不到发送键"; TOUR_FAIL=1
fi
sleep 3
dump_dev /sdcard/t03.xml
shot "$EV/T03-bubble.png" || true
dump_has t03.xml '已送达' 'T03 气泡已发送(已送达回执)'
dump_diff t01.xml t03.xml 'T03 与 T01 层级不同'
png_diff T01-channel.png T03-bubble.png 'T03 帧≠T01帧'

echo "== tour: T04 long-press bubble menu =="
b=$(bounds_desc /sdcard/t03.xml 'text=component-tour-ok')
if [ -n "$b" ]; then
  R "input swipe $b $b 1200"
  echo "  longpress -> $b"
else
  echo "  ASSERT FAIL T04 找不到气泡(无法长按)"; TOUR_FAIL=1
fi
sleep 3   # 等选择手柄/菜单动画稳定
# 2026-10-17 实测：长按选中后系统/Compose 菜单浮层会让 uiautomator dump 挂起（bytes=0，
# 伴随模拟器 GPU ColorBuffer 错误）→ T04 不再依赖 dump，改用截图证据 + 帧变化；
# 菜单内容（复制/全选/记录到 Agent）由本地目检 T04 截图把关。
shot "$EV/T04-longpress-menu.png" || true
png_diff T03-bubble.png T04-longpress-menu.png 'T04 长按后帧变化(选择态/菜单)'
# 长按选中态清理：实测（2026-10-17）选中态会①抑制 Compose semantics（dock desc 从 dump 消失）
# ②吞掉后续 tap ③BACK 按多了直接退 App（BACK×2 已退桌面、误开过 Google Calendar）。
# 结论：清理不可靠 → T04 之后直接重启 App 拿干净状态（几秒代价，换取 T05-T09 稳定）。
R "input keyevent 4"; sleep 1
R "am force-stop com.lantianhcgp.erbian" || true
sleep 1
R "am start -n com.lantianhcgp.erbian/.ui.MainActivity" || true
sleep 5

echo "== tour: T05 schedule =="
close_ime   # 菜单关掉后键盘可能残留，dock 点击前必须确保无 IME
# App 已重启（T04 后），dock 坐标沿用 t01（固定布局）
tap_sel /sdcard/t01.xml 'desc=1' || true
sleep 2
dump_dev /sdcard/t05.xml
shot "$EV/T05-schedule.png" || true
dump_has t05.xml '耳边待办' 'T05 已切到日程页(页面独有标记)'
dump_diff t01.xml t05.xml 'T05 与 T01 层级不同'
png_diff T01-channel.png T05-schedule.png 'T05 帧≠T01帧'

echo "== tour: T06 profile with seeded card + evidence expand =="
# 种样本卡（3条证据测折叠）。契约=服务器写回的 {"cards":[...]} 包裹格式（App 现也容忍裸数组）
cat > /tmp/seed_profile.json <<'CARD'
{"cards":[{"category":"身份","title":"高三学生 · 英语课代表","content":"每周6天课，节奏紧凑；承担英语听写组织与课代表职责，学习规划以词汇积累为主线。","confidence":0.92,"evidence":["每周六天课，日程排满","负责组织英语听写与词汇学习","自述任高三英语课代表"]}]}
CARD
adb push /tmp/seed_profile.json /data/local/tmp/seed_profile.json >/dev/null 2>&1 || true
R "run-as $PKG sh -c 'cp /data/local/tmp/seed_profile.json files/profile.json'"
echo "  seed-card: $(R "run-as $PKG sh -c 'wc -c < files/profile.json'" | tr -dc '0-9') bytes"
sleep 1
R "am force-stop $PKG"; sleep 1; R "am start -n $PKG/.ui.MainActivity > /dev/null"; sleep 3
dump_dev /sdcard/t06pre.xml
tap_sel /sdcard/t06pre.xml 'desc=2' || true
sleep 2
dump_dev /sdcard/t06.xml
shot "$EV/T06-profile-card.png" || true
dump_has t06.xml '高三学生' 'T06 样本卡已渲染'
dump_lacks t06.xml '每周六天课' 'T06 证据默认折叠(不露正文)'
# 卡片形状门禁：左右边缘必须竖直（超椭圆装配错=上窄下宽梯形，2026-10-16 真机实测）
python3 scripts/shape_probe.py "$EV/T06-profile-card.png" || TOUR_FAIL=1
tap_sel /sdcard/t06.xml 'text=点按展开' || tap_sel /sdcard/t06.xml 'text=共3条证据，点按展开' || true
sleep 1
dump_dev /sdcard/t06b.xml
shot "$EV/T06b-profile-expanded.png" || true
dump_has t06b.xml '每周六天课' 'T06b 证据已展开'
dump_diff t06.xml t06b.xml 'T06b 与 T06 层级不同'
png_diff T06-profile-card.png T06b-profile-expanded.png 'T06b 帧≠T06帧'

echo "== tour: T07 settings =="
tap_sel /sdcard/t06b.xml 'desc=3' || true
sleep 2
dump_dev /sdcard/t07.xml
shot "$EV/T07-settings.png" || true
dump_diff t06b.xml t07.xml 'T07 已从画像切走'
png_diff T06b-profile-expanded.png T07-settings.png 'T07 帧≠T06b帧'

echo "== tour: T08 wake panel =="
R "am force-stop $PKG"; sleep 1
R "am start -a android.intent.action.ASSIST -n $PKG/.ui.MainActivity > /dev/null"
sleep 4
dump_dev /sdcard/t08.xml
shot "$EV/T08-wake-panel.png" || true
dump_has t08.xml '发送' 'T08 唤醒面板出现'

echo "== tour: T09 wake panel typing =="
tap_sel /sdcard/t08.xml 'text=说点什么' || true
sleep 1
R "input text panel-typing"
sleep 1
dump_dev /sdcard/t09.xml
shot "$EV/T09-panel-typing.png" || true
dump_has t09.xml 'panel-typing' 'T09 面板键入文本已进输入框'
dump_diff t08.xml t09.xml 'T09 与 T08 层级不同'
png_diff T08-wake-panel.png T09-panel-typing.png 'T09 帧≠T08帧'

echo "== tour: backstop — 全部 T 帧两两不得字节相同 =="
BTFAIL=0
for a in "$EV"/T*.png; do
  for c in "$EV"/T*.png; do
    [ "$a" \< "$c" ] || continue
    if [ -f "$a" ] && [ -f "$c" ] && cmp -s "$a" "$c"; then
      echo "  ASSERT FAIL 帧重复: $(basename "$a") == $(basename "$c")"; BTFAIL=1; TOUR_FAIL=1
    fi
  done
done
[ "$BTFAIL" = 0 ] && echo "  ASSERT OK   9帧字节互不相同"

echo "== tour: layout gate (dock 状态帧; T02/T08/T09=键盘或浮层态无 dock，豁免) =="
for f in T01-channel T03-bubble T04-longpress-menu T05-schedule T06-profile-card T06b-profile-expanded T07-settings; do
  r=$(python3 scripts/layout_probe.py "$EV/$f.png" 2>&1) || TOUR_FAIL=1
  echo "layout_probe $f.png: $r"
done

echo "TOUR_DONE"
ls -la "$EV"/T*.png | awk '{print $5, $9}'

# ---------- 收口：断言汇总 + 全 verdict 终局门禁 ----------
if [ "$TOUR_FAIL" = 0 ]; then
  echo "TOUR_ASSERT_PASS" | tee -a "$EV/verdict.txt"
else
  echo "TOUR_ASSERT_FAIL" | tee -a "$EV/verdict.txt"
fi
echo "-- verdict 全文 --"; cat "$EV/verdict.txt" 2>/dev/null
# 此前所有门禁都 || true 报告制、FAIL 也绿(2026-10-16)。终局门禁：任何 *_FAIL → CI 红。
if grep -q '_FAIL' "$EV/verdict.txt" 2>/dev/null; then
  echo "VERDICT_GATE_FAIL"
  exit 1
fi
echo "VERDICT_GATE_PASS"
