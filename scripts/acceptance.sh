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
$ADB shell pm grant $PKG android.permission.READ_CALENDAR 2>/dev/null || true
$ADB shell pm grant $PKG android.permission.WRITE_CALENDAR 2>/dev/null || true

echo "== [2] launch app + baseline screenshot =="
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null
sleep 4
timeout 20 $ADB exec-out screencap -p > "$EV/01_home.png"

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
timeout 15 $ADB logcat -c || true
timeout 20 $ADB shell dumpsys -l 2>/dev/null | grep -i voice > "$EV/voice_services_list.txt" || true
timeout 20 $ADB shell input keyevent --longpress KEYCODE_POWER
sleep 4
$ADB shell "dumpsys window | grep -iE 'erbian|VoiceInter|Assist'" > "$EV/assist_window.txt" || true
$ADB shell dumpsys activity services "$PKG" > "$EV/services_after_wake.txt" 2>&1 || true
timeout 20 $ADB exec-out screencap -p > "$EV/02_wake.png"
sleep 1
timeout 20 $ADB exec-out screencap -p > "$EV/02_wake.png"   # 双帧取后者（软件渲染首帧常黑）
timeout 30 $ADB logcat -d -t 500 > "$EV/wake_logcat.txt" || true
grep -E "session|onNewSession|panel attached|powerLongPress" "$EV/wake_logcat.txt" > "$EV/session_evidence.txt" || true
# UI 侧会话面板探测：长按后屏幕上是否出现「发送」按钮/输入框
timeout 40 $ADB shell uiautomator dump /sdcard/ui_wake.xml > "$EV/wake_dump_out.txt" 2>&1 || true
timeout 20 $ADB shell cat /sdcard/ui_wake.xml 2>/dev/null | grep -o '说点什么\|发送\|耳边' | sort -u > "$EV/session_ui_evidence.txt" || true
echo "-- session_ui_evidence:" >> "$EV/session_evidence.txt"
cat "$EV/session_ui_evidence.txt" >> "$EV/session_evidence.txt" 2>/dev/null || true
timeout 25 $ADB shell run-as $PKG sh -c 'tail -20 files/logs/app-*.log' > "$EV/session_applog.txt" 2>>"$EV/session_applog.txt" || true
echo "--- applog tail ---" >> "$EV/session_applog.txt"
cat "$EV/session_applog.txt" || true
echo "== [4c] cmd voice_interaction probe =="
timeout 20 $ADB shell cmd voice_interaction > "$EV/cmd_vie_out.txt" 2>&1 || true
head -20 "$EV/cmd_vie_out.txt" || true

echo "== [5] direct ASSIST intent probe =="
bash scripts/wake-screen.sh
timeout 15 $ADB logcat -c || true
$ADB shell am start -a android.intent.action.ASSIST >/dev/null 2>&1 || true
sleep 3
timeout 20 $ADB exec-out screencap -p > "$EV/04_assist.png"
timeout 30 $ADB logcat -d -t 300 > "$EV/assist_intent_logcat.txt" 2>&1 || true

echo "== [6] final screenshot =="
bash scripts/wake-screen.sh
$ADB shell am start -n $PKG/.ui.MainActivity >/dev/null || true
sleep 2
timeout 20 $ADB exec-out screencap -p > "$EV/03_final.png"

echo "== [7] crash evidence =="
$ADB logcat -d 2>/dev/null | grep -B3 -A40 "FATAL EXCEPTION" | head -120 > "$EV/crash_evidence.txt" || true
wc -l "$EV/crash_evidence.txt" || true

echo "== [8] uiautomator UI check (gating, last - may affect display) =="
bash scripts/ui-check.sh || true

echo "== [9] instrumentation (best-effort, very last) =="
gradle :app:connectedDebugAndroidTest --no-daemon > "$EV/instrumentation.txt" 2>&1 || true
grep -E "FAILED|BUILD" "$EV/instrumentation.txt" | tail -4 || true

echo "ACCEPTANCE DONE, verdict=$(cat $EV/verdict.txt 2>/dev/null || echo N/A)"
