#!/usr/bin/env python3
"""布局像素门禁：量浮动 dock 药丸的纵向占带。
用法: layout_probe.py <png> [--min-h 120] [--max-h 220]
输出: dock_band=y0-y1 height=NNpx pill_bottom_margin=NNpx 以及 PASS/FAIL。
背景=白(≥244)，药丸=灰面(≤240)；自底向上找连续灰带。异常巨型(如全屏高)必红。
"""
import struct, zlib, sys

def decode(path):
    d=open(path,'rb').read()
    w,h=struct.unpack('>II', d[16:24]); ct=d[25]
    pos, idat = 8, b''
    while pos < len(d):
        ln = struct.unpack('>I', d[pos:pos+4])[0]; t=d[pos+4:pos+8]
        if t==b'IDAT': idat += d[pos+8:pos+8+ln]
        pos += 12+ln
    raw = zlib.decompress(idat)
    bpp = 4 if ct==6 else 3
    stride = w*bpp
    rows=[]; prev=bytearray(stride); p=0
    for y in range(h):
        f=raw[p]; p+=1
        line=bytearray(raw[p:p+stride]); p+=stride
        if f==1:
            for i in range(bpp,stride): line[i]=(line[i]+line[i-bpp])&255
        elif f==2:
            for i in range(stride): line[i]=(line[i]+prev[i])&255
        elif f==3:
            for i in range(stride):
                a=line[i-bpp] if i>=bpp else 0
                line[i]=(line[i]+((a+prev[i])>>1))&255
        elif f==4:
            for i in range(stride):
                a=line[i-bpp] if i>=bpp else 0
                c=prev[i-bpp] if i>=bpp else 0
                b_=prev[i]
                pp=a+b_-c
                pa,pb,pc=abs(pp-a),abs(pp-b_),abs(pp-c)
                pr=a if (pa<=pb and pa<=pc) else (b_ if pb<=pc else c)
                line[i]=(line[i]+pr)&255
        rows.append(line); prev=line
    return w,h,bpp,stride,rows

def rowmean(rows,w,bpp,y):
    line=rows[y]; s=0; n=0
    for x in range(0,w,8):
        i=x*bpp; s+=(line[i]+line[i+1]+line[i+2])/3; n+=1
    return s/n

def main():
    png=sys.argv[1]
    minh=int(sys.argv[sys.argv.index('--min-h')+1]) if '--min-h' in sys.argv else 120
    maxh=int(sys.argv[sys.argv.index('--max-h')+1]) if '--max-h' in sys.argv else 240
    w,h,bpp,stride,rows=decode(png)
    # 最长连续灰带（药丸=灰200..243；手势条细线/文字不会成为最长带）
    best=(0,0,0); cur=(0,0)
    for y in range(h):
        m=rowmean(rows,w,bpp,y)
        if 200<=m<=243:
            if cur[0]==0: cur=(y,y)
            else: cur=(cur[0],y)
        else:
            if cur!=(0,0) and cur[1]-cur[0]+1>best[2]:
                best=(cur[0],cur[1],cur[1]-cur[0]+1)
            cur=(0,0)
    if cur!=(0,0) and cur[1]-cur[0]+1>best[2]:
        best=(cur[0],cur[1],cur[1]-cur[0]+1)
    bs,be,height=best
    bottom_margin=h-1-be
    ok = minh<=height<=maxh and be > h*0.7
    print(f"dock_band=y{bs}-{be} height={height}px bottom_margin={bottom_margin}px gate={'PASS' if ok else 'FAIL'}")
    sys.exit(0 if ok else 1)

if __name__=='__main__':
    main()
