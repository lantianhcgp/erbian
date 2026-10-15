#!/data/data/com.termux/files/usr/bin/bash
# 截图库（2026-10-15 acc4 对照法）：三阶梯采集，每级全帧亮度断言，过亮即收
#   tier1 exec-out（acc4 已证能出实拍的路径）
#   tier2 文件式 shell screencap + pull
#   tier3 screenrecord + ffmpeg 抽帧
ADB="${ADB:-adb}"

shot_brightness() { # $1=png -> 全帧均值RGB
  python3 - "$1" <<'PYEOF'
import struct, zlib, sys
try:
    d=open(sys.argv[1],'rb').read()
    if d[:8]!=b'\x89PNG\r\n\x1a\n' or len(d)<100:
        print('INVALID'); raise SystemExit
    w,h=struct.unpack('>II', d[16:24])
    pos, idat = 8, b''
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        if typ == b'IDAT': idat += d[pos+8:pos+8+ln]
        pos += 12 + ln
    raw = zlib.decompress(idat)
    bpp = 4 if (d[25]&4) else 3
    stride = w*bpp+1
    joined = b''.join(raw[i*stride+1:(i+1)*stride] for i in range(h))  # 全帧
    if not joined: print('EMPTY'); raise SystemExit
    print(f'{sum(joined)//len(joined)}')
except Exception:
    print('ERR')
PYEOF
}

_bright_enough() { # $1=mean -> 0=yes
  case "$1" in ''|INVALID|EMPTY|ERR) return 1 ;; *) [ "$1" -gt 25 ] 2>/dev/null ;; esac
}

_wake() {
  $ADB shell settings put system screen_brightness_mode 0 >/dev/null 2>&1 || true
  $ADB shell settings put system screen_brightness 255 >/dev/null 2>&1 || true
  $ADB shell settings put global stay_on_while_plugged_in 3 >/dev/null 2>&1 || true
  $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  $ADB shell wm dismiss-keyguard >/dev/null 2>&1 || true
  sleep 1
}

shot() { # $1=输出png绝对路径
  local OUT="$1" b
  _wake
  # tier1 exec-out
  timeout 20 $ADB exec-out screencap -p > "$OUT" 2>/dev/null || true
  b=$(shot_brightness "$OUT" 2>/dev/null); echo "exec-out mean=$b -> $OUT" >> "${OUT}.check"
  _bright_enough "$b" && return 0
  # tier2 文件式
  $ADB shell screencap -p /sdcard/_shot.png >/dev/null 2>&1 || true
  $ADB pull /sdcard/_shot.png "$OUT" >/dev/null 2>&1 || true
  b=$(shot_brightness "$OUT" 2>/dev/null); echo "file mean=$b -> $OUT" >> "${OUT}.check"
  _bright_enough "$b" && return 0
  # tier3 录屏抽帧
  timeout 25 $ADB shell "screenrecord --time-limit 2 --bit-rate 6000000 /sdcard/_shot.mp4" >/dev/null 2>&1 || true
  $ADB pull /sdcard/_shot.mp4 "$OUT.mp4" >/dev/null 2>&1 || true
  if [ -s "$OUT.mp4" ]; then
    ffmpeg -y -loglevel error -i "$OUT.mp4" -vframes 1 "$OUT" >/dev/null 2>&1 || true
    b=$(shot_brightness "$OUT" 2>/dev/null); echo "screenrecord mean=$b -> $OUT" >> "${OUT}.check"
    _bright_enough "$b" && return 0
  fi
  # 末招：睡醒一整轮再重复 tier1
  $ADB shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true
  sleep 2
  _wake
  timeout 20 $ADB exec-out screencap -p > "$OUT" 2>/dev/null || true
  b=$(shot_brightness "$OUT" 2>/dev/null); echo "retried-exec-out mean=$b -> $OUT" >> "${OUT}.check"
  _bright_enough "$b"
}
