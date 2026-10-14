#!/usr/bin/env bash
# uiautomator 级 UI 验收（不依赖 instrumentation 时序）: ui-check.sh
# 输出: UI_CHECK_PASS / UI_CHECK_FAIL + build/acceptance/ui_check_detail.txt
EV=build/acceptance
mkdir -p "$EV"
ADB="adb"
PKG=com.lantianhcgp.erbian
D=$EV/ui_check_detail.txt
: > "$D"

SIZE=$($ADB shell wm size | grep -oE '[0-9]+x[0-9]+' | head -1)
W=${SIZE%x*}; H=${SIZE#*x}
echo "screen=${W}x${H}" | tee -a "$D"

dump() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  $ADB shell cat /sdcard/ui.xml 2>/dev/null || true
}

has() { # has <dump> <text>
  case "$1" in *"$2"*) return 0;; *) return 1;; esac
}

$ADB shell am force-stop $PKG
sleep 1
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 4

FAIL=0

# 1 频道页
DUMP=$(dump)
echo "-- channel page --" >> "$D"
for m in "频道" "说点什么"; do
  if has "$DUMP" "$m"; then echo "OK: $m" >> "$D"; else echo "MISS: $m" >> "$D"; FAIL=1; fi
done

# 2 发送出泡
$ADB shell input tap $((W*47/100)) $((H*76/100))
sleep 1
$ADB shell input text "ceshi_jilu"
sleep 1
$ADB shell input keyevent 66   # ENTER 发送（IME 没有发送键时）
sleep 2
DUMP=$(dump)
echo "-- after send --" >> "$D"
if has "$DUMP" "ceshi_jilu"; then echo "OK: send bubble" >> "$D"; else echo "MISS: send bubble" >> "$D"; FAIL=1; fi

# 3 导航三页（底部 dock 四等分，x=W*(2i+1)/8）
for item in "1:耳边待办" "2:画像" "3:唤醒状态"; do
  idx=${item%%:*}; marker=${item#*:}
  $ADB shell input tap $((W*(2*idx+1)/8)) $((H - 140))
  sleep 2
  DUMP=$(dump)
  echo "-- nav $idx --" >> "$D"
  if has "$DUMP" "$marker"; then echo "OK: $marker" >> "$D"; else echo "MISS: $marker" >> "$D"; FAIL=1; fi
done

# 4 回频道页
$ADB shell input tap $((W*1/8)) $((H - 140))
sleep 2

if [ "$FAIL" = 0 ]; then echo "UI_CHECK_PASS" | tee "$EV/verdict.txt"; else echo "UI_CHECK_FAIL" | tee "$EV/verdict.txt"; fi
cat "$D"
