#!/usr/bin/env bash
# 模拟器自动验收脚本（CI 内执行，android-emulator-runner 提供 adb 与已启动模拟器）
set -e
cd "$(dirname "$0")/.."
EV=build/acceptance
mkdir -p "$EV"
ADB="adb"

PKG=com.lantianhcgp.erbian
COMP=$PKG/$PKG.voice.AssistInteractionService

echo "== [0] pin screen on =="
$ADB shell svc power stayon usb
$ADB shell settings put global stay_on_while_plugged_in 3
$ADB shell input keyevent KEYCODE_WAKEUP
$ADB shell wm dismiss-keyguard || true

echo "== [1] install =="
$ADB install -r app/build/outputs/apk/debug/app-debug.apk | tee "$EV/install.txt"

echo "== [2] instrumentation smoke tests (screen interactive) =="
gradle :app:connectedDebugAndroidTest --no-daemon > "$EV/instrumentation.txt" 2>&1 || true
tail -25 "$EV/instrumentation.txt"
grep -q "BUILD SUCCESSFUL" "$EV/instrumentation.txt" && echo "INSTRUMENTATION_PASS" | tee "$EV/verdict.txt" || echo "INSTRUMENTATION_FAIL" | tee "$EV/verdict.txt"
echo "== [2b] crash evidence =="
$ADB logcat -d 2>/dev/null | grep -B3 -A40 "FATAL EXCEPTION" | head -120 > "$EV/crash_evidence.txt" || true
wc -l "$EV/crash_evidence.txt" || true

echo "== [3] launch app + baseline screenshot =="
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 3
$ADB exec-out screencap -p > "$EV/01_home.png"

echo "== [4] configure wake chain (same 3 settings as production) =="
$ADB shell settings put secure assistant "$COMP" | tee "$EV/wake_settings.txt"
$ADB shell settings put secure voice_interaction_service "$COMP" | tee -a "$EV/wake_settings.txt"
$ADB shell settings put global power_button_long_press 5 | tee -a "$EV/wake_settings.txt"
$ADB shell settings get secure assistant | tee -a "$EV/wake_settings.txt"

echo "== [5] long-press power key (behavior=5 -> our assist service) =="
$ADB logcat -c || true
$ADB shell dumpsys voice_interaction > "$EV/vie_dump_before.txt" 2>&1 || true
$ADB shell input keyevent --longpress KEYCODE_POWER
sleep 4
$ADB shell "dumpsys window | grep -iE 'erbian|VoiceInter|Assist'" > "$EV/assist_window.txt" || true
$ADB exec-out screencap -p > "$EV/02_wake.png"
$ADB shell dumpsys voice_interaction > "$EV/vie_dump_after.txt" 2>&1 || true
$ADB logcat -d -t 500 > "$EV/wake_logcat.txt" || true
grep -E "session|onNewSession|panel attached|powerLongPress" "$EV/wake_logcat.txt" > "$EV/session_evidence.txt" || true
cat "$EV/session_evidence.txt" || true
$ADB shell input keyevent KEYCODE_BACK || true

echo "== [6] final screenshot =="
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null || true
sleep 2
$ADB exec-out screencap -p > "$EV/03_final.png"

echo "ACCEPTANCE DONE, verdict=$(cat $EV/verdict.txt)"
