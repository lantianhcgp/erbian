#!/usr/bin/env python3
"""组件形状门禁：卡片左右边缘必须竖直。

背景：SuperellipseShape 装配顺序错时，所有组件被渲染成"上窄下宽梯形"
（2026-10-16 真机+CI 帧实测：卡片顶宽975→底宽1005，左右各漂±15px）。
本探针取卡片色区块的顶部/底部左右边缘，漂移>3px 即 FAIL。

用法: shape_probe.py <png> [--tol 3]
卡片色按 CI 亮色主题 (241,240,249)±5 自动识别；找不到卡片区即 FAIL（不许假绿）。
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layout_probe import decode

CARD = (241, 240, 249)

def is_card(rows, w, bpp, x, y, tol=5):
    i = x * bpp
    line = rows[y]
    return (abs(line[i] - CARD[0]) <= tol and
            abs(line[i + 1] - CARD[1]) <= tol and
            abs(line[i + 2] - CARD[2]) <= tol)

def main():
    png = sys.argv[1]
    tol = int(sys.argv[sys.argv.index('--tol') + 1]) if '--tol' in sys.argv else 3
    w, h, bpp, stride, rows = decode(png)

    # 中列找最长卡片色竖直区块 = 卡片上下边界
    cx = w // 2
    best = (0, 0, 0)
    cur = None
    for y in range(h):
        if is_card(rows, w, bpp, cx, y):
            if cur is None:
                cur = y
        else:
            if cur is not None and y - cur > best[2]:
                best = (cur, y - 1, y - cur)
            cur = None
    if cur is not None and h - cur > best[2]:
        best = (cur, h - 1, h - cur)
    top, bot, ch = best
    if ch < 100:
        print(f"shape_probe: 找不到卡片区(top={top} bot={bot} h={ch}) gate=FAIL")
        sys.exit(1)

    def edge(y, xs, xe, step):
        for x in range(xs, xe, step):
            if is_card(rows, w, bpp, x, y):
                return x
        return None

    ys = [top + 20, (top + bot) // 2, bot - 20]
    lt = edge(ys[0], 0, w // 2, 1)          # 顶：左边缘
    lb = edge(ys[2], 0, w // 2, 1)          # 底：左边缘
    rt = edge(ys[0], w - 1, w // 2, -1)
    rb = edge(ys[2], w - 1, w // 2, -1)
    if lt is None or lb is None or rt is None or rb is None:
        print(f"shape_probe: 边缘取点失败 lt={lt} lb={lb} rt={rt} rb={rb} gate=FAIL")
        sys.exit(1)
    dl, dr = abs(lb - lt), abs(rb - rt)
    ok = dl <= tol and dr <= tol
    print(f"shape_probe card_y{top}-{bot}: left {lt}->{lb} (Δ{dl}) right {rt}->{rb} (Δ{dr}) gate={'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)

if __name__ == '__main__':
    main()
