#!/data/data/com.termux/files/usr/bin/bash
# 截图库：唤醒+满亮 → 文件式 screencap → 拉回 → 亮度断言，暗帧自动重拍（最多3次）
# 用法: source 后 shot <输出png绝对路径>；写 <输出png>.check 记 mean RGB
ADB="${ADB:-adb}"

shot_brightness() { # $1=png -> 打印前60行均值RGB（python zlib 解码）
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
    joined = b''.join(raw[i*stride+1:(i+1)*stride] for i in range(min(60,h)))
    if not joined: print('EMPTY'); raise SystemExit
    # 均值（RGB）
    n=len(joined)
    print(f'{sum(joined)/n:.0f}')
except Exception:
    print('ERR')
PYEOF
}

shot() { # $1=输出路径（绝对）
  local OUT="$1" b try
  for try in 1 2 3; do
    $ADB shell settings put system screen_brightness_mode 0 >/dev/null 2>&1 || true
    $ADB shell settings put system screen_brightness 255 >/dev/null 2>&1 || true
    $ADB shell settings put global stay_on_while_plugged_in 3 >/dev/null 2>&1 || true
    $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    $ADB shell wm dismiss-keyguard >/dev/null 2>&1 || true
    sleep 1
    $ADB shell screencap -p /sdcard/_shot.png >/dev/null 2>&1 || true
    $ADB pull /sdcard/_shot.png "$OUT" >/dev/null 2>&1 || true
    b=$(shot_brightness "$OUT" 2>/dev/null)
    echo "try=$try mean=$b -> $OUT" >> "${OUT}.check"
    # 亮度>25 视为真画面；否则睡屏重醒来一次完整渲染周期再拍
    case "$b" in
      ''|INVALID|EMPTY|ERR) sleep 2 ;;
      *) [ "$b" -gt 25 ] 2>/dev/null && return 0 ;;
    esac
    $ADB shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true
    sleep 1
  done
  # screencap 三连黑 → screenrecord 管线兜底（MediaCodec 路径，与 SF 截屏不同源）
  $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  $ADB shell wm dismiss-keyguard >/dev/null 2>&1 || true
  $ADB shell settings put system screen_brightness 255 >/dev/null 2>&1 || true
  sleep 1
  $ADB shell "screenrecord --time-limit 2 --bit-rate 4000000 /sdcard/_shot.mp4" >/dev/null 2>&1 || true
  $ADB pull /sdcard/_shot.mp4 "$OUT.mp4" >/dev/null 2>&1 || true
  if [ -s "$OUT.mp4" ]; then
    ffmpeg -y -loglevel error -i "$OUT.mp4" -vframes 1 "$OUT" >/dev/null 2>&1 || true
    b=$(shot_brightness "$OUT" 2>/dev/null)
    echo "screenrecord mean=$b -> $OUT" >> "${OUT}.check"
    case "$b" in ''|INVALID|EMPTY|ERR) ;; *) return 0 ;; esac
  fi
  return 1
}
