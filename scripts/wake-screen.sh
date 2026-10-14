#!/data/data/com.termux/files/usr/bin/bash
# 唤醒+解锁（幂等；CI 模拟器屏幕会超时灭屏，任何阶段前调用）
ADB="adb"
$ADB shell settings put system screen_off_timeout 600000
$ADB shell svc power stayon usb
$ADB shell input keyevent KEYCODE_WAKEUP
$ADB shell wm dismiss-keyguard
$ADB shell input keyevent KEYCODE_WAKEUP
