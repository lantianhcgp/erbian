#!/usr/bin/env python3
"""页面背景亮度探针（黑夜模式适配验收）。

用亮度直方图的峰值 bin 代表背景色（页面主体 y150-1600），不受文字/卡片多少干扰。
- 深色模式：峰值亮度应 < DARK_MAX（默认 64）
- 浅色模式：峰值亮度应 >= LIGHT_MIN（默认 184）

用法:
  bg_probe.py <png> --expect dark   [--dark-max 64]
  bg_probe.py <png> --expect light  [--light-min 184]
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout_probe import decode

def main():
    png = sys.argv[1]
    if "--expect" not in sys.argv:
        print("bg_probe: 缺 --expect dark|light"); sys.exit(2)
    expect = sys.argv[sys.argv.index("--expect") + 1]
    dark_max = int(sys.argv[sys.argv.index("--dark-max") + 1]) if "--dark-max" in sys.argv else 64
    light_min = int(sys.argv[sys.argv.index("--light-min") + 1]) if "--light-min" in sys.argv else 184

    w, h, bpp, stride, rows = decode(png)
    y0, y1 = 150, min(1600, h - 300)
    hist = [0] * 16
    n = 0
    for y in range(y0, y1, 4):
        line = rows[y]
        for x in range(0, w, 6):
            i = x * bpp
            lum = (line[i] + line[i + 1] + line[i + 2]) // 3
            hist[min(15, lum // 16)] += 1
            n += 1
    peak = max(range(16), key=lambda k: hist[k])
    bg_lum = peak * 16 + 8          # 峰值 bin 中心
    ratio = hist[peak] / n * 100

    if expect == "dark":
        ok = bg_lum < dark_max
        need = f"<{dark_max}"
    else:
        ok = bg_lum >= light_min
        need = f">={light_min}"
    print(f"bg_probe {os.path.basename(png)}: bg_lum={bg_lum} (峰值占 {ratio:.0f}%) expect={expect} need {need} gate={'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()
