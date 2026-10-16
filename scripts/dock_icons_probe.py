#!/usr/bin/env python3
"""dock 图标可见性门禁：四个槽的图标区都必须有像素。

背景：DockItem 把 graphicsLayer{alpha=pillAlpha} 挂在含 Icon 的 Box 上，
未选中 tab 整层透明 → 只有当前页图标可见（2026-10-16 真机+CI 实测：
选中槽图标区 1465 暗像素，其余三槽 0）。

2026-10-16 晚：引入 edge-to-edge 后 dock 底部内边距变化 → 位置从 y1638 上移到
y1700 附近，写死坐标全 0 假红。改为**自动定位 dock 容器色带**（底部中性灰带），
图标带取容器带上半部，不再依赖固定坐标。

用法: dock_icons_probe.py <png>
"""
import sys

SLOTS = [(150, 285, '频道'), (365, 500, '日程'), (580, 715, '画像'), (795, 930, '设置')]

def main():
    png = sys.argv[1]
    import os
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from layout_probe import decode
    w, h, bpp, stride, rows = decode(png)

    # ---- 自动定位 dock 容器带 ----
    # dock 是浅色 M3 surfaceContainerHigh≈(230,224,233) 的中性灰药丸；页面背景 (240,240,252)/(255,251,254)
    # 明显更亮且偏蓝 → 用「严格中性 + 亮度 200-238」把它排除，再取**最靠底**的连续段（dock 贴底）。
    def grey_n(y, step=4):
        line = rows[y]
        n = 0
        for x in range(120, 960, step):
            i = x * bpp
            r, g, b = line[i], line[i + 1], line[i + 2]
            if abs(r - b) <= 8 and 200 <= (r + g + b) // 3 <= 238:
                n += 1
        return n

    ys = [y for y in range(1250, h - 30) if grey_n(y) >= 120]
    if not ys:
        print("dock_icons: 找不到 dock 容器带（页面可能不在频道页/非浅色）gate=FAIL")
        sys.exit(1)
    segs, st, prev = [], ys[0], ys[0]
    for y in ys[1:]:
        if y - prev > 8:
            segs.append((st, prev)); st = y
        prev = y
    segs.append((st, prev))
    # dock 紧贴底部 → 取 bot 最大的段（而非最长，防把内容区长灰带算进来）
    top, bot = max(segs, key=lambda s: s[1])
    # 高度应合理（dock 约 150-200px）；过长说明还是抓错了 → 截到底部 200px 内
    if bot - top > 210:
        top = bot - 170
    band_h = bot - top
    icon_top = top + max(8, band_h // 8)
    icon_bot = top + max(40, band_h * 3 // 5)

    fails, parts = [], []
    for x0, x1, name in SLOTS:
        n = 0
        for y in range(icon_top, icon_bot):
            line = rows[y]
            for x in range(x0, x1):
                i = x * bpp
                if (line[i] + line[i + 1] + line[i + 2]) / 3 < 150:
                    n += 1
        parts.append(f"{name}={n}")
        if n < 30:
            fails.append(f"{name}({n}px)")
    ok = not fails
    print(f"dock_icons band y{top}-{bot} icon_band y{icon_top}-{icon_bot}: " + " ".join(parts) + f" gate={'PASS' if ok else 'FAIL'}")
    if fails:
        print("  图标缺失槽位: " + ", ".join(fails))
    sys.exit(0 if ok else 1)

if __name__ == '__main__':
    main()
