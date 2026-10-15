#!/data/data/com.termux/files/usr/bin/bash
# 截图库 v2（2026-10-15 教训：PNG 扫描线是 filter 差分编码，直接对原始字节求均值≈0——
# 曾把全部亮帧误判成黑帧。必须 defilter 后按真实像素统计。）
ADB="${ADB:-adb}"

shot_brightness() { # $1=png -> "mean bright%"（真实像素，抽样行/隔列）
  python3 - "$1" <<'PYEOF'
import struct, zlib, sys
try:
    d=open(sys.argv[1],'rb').read()
    if d[:8]!=b'\x89PNG\r\n\x1a\n' or len(d)<100:
        print('INVALID 0'); raise SystemExit
    w,h=struct.unpack('>II', d[16:24]); ct=d[25]
    pos, idat = 8, b''
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        if typ == b'IDAT': idat += d[pos+8:pos+8+ln]
        pos += 12 + ln
    raw = zlib.decompress(idat)
    bpp = 4 if (ct&4) else 3
    stride = w*bpp+1
    prev = bytearray(w*bpp)
    total=bright=sumv=0
    for y in range(h):
        line = raw[y*stride:(y+1)*stride]
        f = line[0]; cur = bytearray(line[1:])
        if f==1:
            for i in range(len(cur)): cur[i] = (cur[i] + (cur[i-bpp] if i>=bpp else 0)) & 0xFF
        elif f==2:
            for i in range(len(cur)): cur[i] = (cur[i] + prev[i]) & 0xFF
        elif f==3:
            for i in range(len(cur)): cur[i] = (cur[i] + (((cur[i-bpp] if i>=bpp else 0) + prev[i])>>1)) & 0xFF
        elif f==4:
            for i in range(len(cur)):
                a = cur[i-bpp] if i>=bpp else 0; b = prev[i]; c = prev[i-bpp] if i>=bpp else 0
                p = a+b-c; pa,pb,pc = abs(p-a),abs(p-b),abs(p-c)
                pr = a if (pa<=pb and pa<=pc) else (b if pb<=pc else c)
                cur[i] = (cur[i]+pr)&0xFF
        if y % 3 == 0:
            for x in range(0, w, 2):
                px = cur[x*bpp:(x+1)*bpp]
                v = px[0] if px[0]>=px[1] else px[1]
                if px[2]>v: v = px[2]
                sumv += v; total += 1
                if v>40: bright += 1
        prev = cur
    if not total: print('EMPTY 0'); raise SystemExit
    pct = 100*bright//total
    print(f'{sumv//total} {pct}')
except Exception as e:
    print(f'ERR 0')
PYEOF
}

_bright_enough() { # "$mean $pct" -> 0=合格（均值>25 且亮像素>15%）
  local m p
  m=${1%% *}; p=${1##* }
  case "$m" in ''|INVALID|EMPTY|ERR) return 1 ;; esac
  [ "$m" -gt 25 ] 2>/dev/null && [ "$p" -gt 15 ] 2>/dev/null
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
  timeout 20 $ADB exec-out screencap -p > "$OUT" 2>/dev/null || true
  b=$(shot_brightness "$OUT" 2>/dev/null); echo "exec-out: $b -> $OUT" >> "${OUT}.check"
  _bright_enough "$b" && return 0
  $ADB shell screencap -p /sdcard/_shot.png >/dev/null 2>&1 || true
  $ADB pull /sdcard/_shot.png "$OUT" >/dev/null 2>&1 || true
  b=$(shot_brightness "$OUT" 2>/dev/null); echo "file: $b -> $OUT" >> "${OUT}.check"
  _bright_enough "$b" && return 0
  timeout 25 $ADB shell "screenrecord --time-limit 2 --bit-rate 6000000 /sdcard/_shot.mp4" >/dev/null 2>&1 || true
  $ADB pull /sdcard/_shot.mp4 "$OUT.mp4" >/dev/null 2>&1 || true
  if [ -s "$OUT.mp4" ]; then
    ffmpeg -y -loglevel error -i "$OUT.mp4" -vframes 1 "$OUT" >/dev/null 2>&1 || true
    b=$(shot_brightness "$OUT" 2>/dev/null); echo "screenrecord: $b -> $OUT" >> "${OUT}.check"
    _bright_enough "$b" && return 0
  fi
  $ADB shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true
  sleep 2
  _wake
  timeout 20 $ADB exec-out screencap -p > "$OUT" 2>/dev/null || true
  b=$(shot_brightness "$OUT" 2>/dev/null); echo "retried: $b -> $OUT" >> "${OUT}.check"
  _bright_enough "$b"
}
