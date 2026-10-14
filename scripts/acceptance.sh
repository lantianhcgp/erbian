#!/usr/bin/env bash
# 模拟器自动验收脚本（CI 内执行，android-emulator-runner 提供 adb 与已启动模拟器）
# 顺序原则：截图/唤醒取证在前（干净状态），测试工具（uiautomator/instrumentation）垫底防副作用。
set -e
cd "$(dirname "$0")/.."
EV=build/acceptance
mkdir -p "$EV"
ADB="adb"

PKG=com.lantianhcgp.erbian
COMP=$PKG/$PKG.voice.AssistInteractionService

echo "== [0] pin screen on =="
bash scripts/wake-screen.sh
$ADB shell dumpsys power | grep -E "mWakefulness=|mScreenOn" | head -3 > "$EV/screen_state.txt" || true
cat "$EV/screen_state.txt" || true

echo "== [1] install =="
$ADB install -r app/build/outputs/apk/debug/app-debug.apk | tee "$EV/install.txt"

echo "== [2] launch app + baseline screenshot =="
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 4
$ADB exec-out screencap -p > "$EV/01_home.png"

echo "== [3] configure wake chain (same 3 settings as production) =="
$ADB shell settings put secure assistant "$COMP" | tee "$EV/wake_settings.txt"
$ADB shell settings put secure voice_interaction_service "$COMP" | tee -a "$EV/wake_settings.txt"
$ADB shell settings put global power_button_long_press 5 | tee -a "$EV/wake_settings.txt"
$ADB shell settings get secure assistant | tee -a "$EV/wake_settings.txt"
echo "== [3b] roles (verb fallback) =="
{
  $ADB shell cmd role set-role-holders android.app.role.ASSISTANT "$PKG" 2>&1
  $ADB shell cmd role add-role-holders android.app.role.ASSISTANT "$PKG" 2>&1
  $ADB shell cmd role get-role-holders android.app.role.ASSISTANT 2>&1
  $ADB shell cmd role get-role-holders android.app.role.VOICE_INTERACTION 2>&1
} | tee "$EV/role_out.txt"
sleep 2
$ADB shell dumpsys activity services "$PKG" > "$EV/services_after_role.txt" 2>&1 || true

echo "== [4] long-press power key (behavior=5 -> our assist service) =="
bash scripts/wake-screen.sh
$ADB logcat -c || true
$ADB shell input keyevent --longpress KEYCODE_POWER
sleep 4
$ADB shell "dumpsys window | grep -iE 'erbian|VoiceInter|Assist'" > "$EV/assist_window.txt" || true
$ADB shell dumpsys activity services "$PKG" > "$EV/services_after_wake.txt" 2>&1 || true
$ADB exec-out screencap -p > "$EV/02_wake.png"
$ADB logcat -d -t 500 > "$EV/wake_logcat.txt" || true
grep -E "session|onNewSession|panel attached|powerLongPress" "$EV/wake_logcat.txt" > "$EV/session_evidence.txt" || true
cat "$EV/session_evidence.txt" || true
$ADB shell input keyevent KEYCODE_BACK || true

echo "== [5] direct ASSIST intent probe =="
bash scripts/wake-screen.sh
$ADB logcat -c || true
$ADB shell am start -a android.intent.action.ASSIST >/dev/null 2>&1 || true
sleep 3
$ADB exec-out screencap -p > "$EV/04_assist.png"
$ADB logcat -d -t 300 > "$EV/assist_intent_logcat.txt" 2>&1 || true

echo "== [6] final screenshot =="
bash scripts/wake-screen.sh
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null || true
sleep 2
$ADB exec-out screencap -p > "$EV/03_final.png"

echo "== [7] crash evidence =="
$ADB logcat -d 2>/dev/null | grep -B3 -A40 "FATAL EXCEPTION" | head -120 > "$EV/crash_evidence.txt" || true
wc -l "$EV/crash_evidence.txt" || true

echo "== [8] uiautomator UI check (gating, last - may affect display) =="
bash scripts/ui-check.sh || true

echo "== [9] instrumentation (best-effort, very last) =="
gradle :app:connectedDebugAndroidTest --no-daemon > "$EV/instrumentation.txt" 2>&1 || true
grep -E "FAILED|BUILD" "$EV/instrumentation.txt" | tail -4 || true

echo "ACCEPTANCE DONE, verdict=$(cat $EV/verdict.txt 2>/dev/null || echo N/A)"
