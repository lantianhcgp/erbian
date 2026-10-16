#!/usr/bin/env python3
"""dock 图标可见性门禁：四个槽的图标区都必须有像素。

背景：DockItem 把 graphicsLayer{alpha=pillAlpha} 挂在含 Icon 的 Box 上，
未选中 tab 整层透明 → 只有当前页图标可见（2026-10-16 真机+CI 实测：
选中槽图标区 1465 暗像素，其余三槽 0）。

坐标系：CI 固定 1080x1920；dock 带 y1638-1839（layout_probe 实测），
图标区/文字区按经验带划分；槽中心 215/431/648/864（ui-check 实测 bounds）。
用法: dock_icons_probe.py <png>
"""
import sys

def main():
    png = sys.argv[1]
    import os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from layout_probe import decode
    w, h, bpp, stride, rows = decode(png)

    ICON_Y = (1655, 1720)   # 图标带
    SLOTS = [(150, 285, '频道'), (365, 500, '日程'), (580, 715, '画像'), (795, 930, '设置')]
    fails = []
    parts = []
    for x0, x1, name in SLOTS:
        n = 0
        for y in range(ICON_Y[0], ICON_Y[1]):
            line = rows[y]
            for x in range(x0, x1):
                i = x * bpp
                if (line[i] + line[i + 1] + line[i + 2]) / 3 < 150:
                    n += 1
        parts.append(f"{name}={n}")
        if n < 30:
            fails.append(f"{name}({n}px)")
    ok = not fails
    print(f"dock_icons icon_band y{ICON_Y[0]}-{ICON_Y[1]}: " + " ".join(parts) + f" gate={'PASS' if ok else 'FAIL'}")
    if fails:
        print("  图标缺失槽位: " + ", ".join(fails))
    sys.exit(0 if ok else 1)

if __name__ == '__main__':
    main()
