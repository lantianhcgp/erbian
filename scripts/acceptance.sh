#!/usr/bin/env bash
# 模拟器自动验收脚本（CI 内执行，android-emulator-runner 提供 adb 与已启动模拟器）
set -e
cd "$(dirname "$0")/.."
EV=build/acceptance
mkdir -p "$EV"
ADB="adb"

PKG=com.lantianhcgp.erbian
COMP=$PKG/$PKG.voice.AssistInteractionService

echo "== [1] install =="
$ADB install -r app/build/outputs/apk/debug/app-debug.apk | tee "$EV/install.txt"

echo "== [2] launch app =="
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 5
$ADB exec-out screencap -p > "$EV/01_home.png"

echo "== [3] configure wake chain (same 3 settings as production) =="
$ADB shell settings put secure assistant "$COMP" | tee "$EV/wake_settings.txt"
$ADB shell settings put secure voice_interaction_service "$COMP" | tee -a "$EV/wake_settings.txt"
$ADB shell settings put global power_button_long_press 5 | tee -a "$EV/wake_settings.txt"
$ADB shell settings get secure assistant | tee -a "$EV/wake_settings.txt"

echo "== [4] long-press power key (behavior=5 -> our assist service) =="
$ADB shell input keyevent --longpress KEYCODE_POWER
sleep 4
$ADB shell "dumpsys window | grep -iE 'erbian|Assist' " > "$EV/assist_window.txt" || true
$ADB exec-out screencap -p > "$EV/02_wake.png"
# 记录 assistant 会话相关日志
$ADB logcat -d -t 300 > "$EV/wake_logcat.txt" || true
grep -c "$PKG" "$EV/wake_logcat.txt" > "$EV/wake_logcat_hits.txt" || echo "0" > "$EV/wake_logcat_hits.txt"

echo "== [5] instrumentation smoke tests =="
gradle :app:connectedDebugAndroidTest --no-daemon > "$EV/instrumentation.txt" 2>&1 || true
tail -40 "$EV/instrumentation.txt"
grep -q "BUILD SUCCESSFUL" "$EV/instrumentation.txt" && echo "INSTRUMENTATION_PASS" | tee "$EV/verdict.txt" || echo "INSTRUMENTATION_FAIL" | tee "$EV/verdict.txt"

echo "== [6] final screenshots =="
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null || true
sleep 3
$ADB exec-out screencap -p > "$EV/03_final.png"

echo "ACCEPTANCE DONE"
