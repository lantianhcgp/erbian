#!/usr/bin/env python3
"""夜间标题亮度探针（2026-10-16 用户反馈：夜间日程/画像/设置三页大标题仍是黑的）。

在页头文字带（浅色基线 y106-181）内取最亮像素：
- 夜间 expect=dark  → 最亮 >= MIN_DARK 才算标题已适配（黑字会被判 FAIL）
- 浅色 expect=light → 页头必须有足够多的暗像素（深字浅底）

用法:
  title_probe.py <png> --expect dark
  title_probe.py <png> --expect light
"""
import sys
from PIL import Image

# 浅色三页+频道的页头文字带（CI 实测定位）
BANDS = [(96, 190)]          # 覆盖四页页头 y106-181 + 余量
MIN_DARK = 140               # 夜间标题最亮像素下限（正常白色标题 197-227）
MIN_LIGHT_DARKPIX = 12       # 浅色页头区暗像素数下限

def main() -> int:
    path = sys.argv[1]
    expect = sys.argv[sys.argv.index("--expect") + 1] if "--expect" in sys.argv else "dark"
    im = Image.open(path).convert("RGB")
    px = im.load()
    W, H = im.size

    y0, y1 = BANDS[0]
    y1 = min(y1, H - 1)

    brightest = 0
    loc = None
    darkpix = 0
    for y in range(y0, y1 + 1):
        for x in range(30, min(760, W - 1), 2):   # 标题在左半区（右侧是按钮）
            r, g, b = px[x, y]
            l = (r * 299 + g * 587 + b * 114) // 1000
            if l > brightest:
                brightest = l
                loc = (x, y, r, g, b)
            if l < 120:
                darkpix += 1

    name = path.rsplit("/", 1)[-1]
    if expect == "dark":
        ok = brightest >= MIN_DARK
        print(f"title_probe {name}: max_lum={brightest} at {loc} expect=dark need >={MIN_DARK} gate={'PASS' if ok else 'FAIL'}")
        return 0 if ok else 1
    else:
        ok = darkpix >= MIN_LIGHT_DARKPIX
        print(f"title_probe {name}: dark_pixels={darkpix} expect=light need >={MIN_LIGHT_DARKPIX} gate={'PASS' if ok else 'FAIL'}")
        return 0 if ok else 1

if __name__ == "__main__":
    sys.exit(main())
