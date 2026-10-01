#!/usr/bin/env python3
"""Renderiza una hoja de contacto con los fotogramas clave de cada ejercicio.

Replica la cinemática de StickFigure.kt para revisar las poses de
app/src/main/assets/exercises.json sin necesidad de un emulador.

Uso: python3 tools/preview_poses.py [salida.png]   (requiere Pillow)
"""
import json, math, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "app/src/main/assets/exercises.json")

TORSO, NECK, HEAD_R = 30, 10, 6
UPPER_ARM, FOREARM, THIGH, SHIN = 14, 13, 20, 20
SHOULDER_HALF, HIP_HALF = 7, 4
DEFAULTS = dict(torso=180, armF=0, foreF=0, armB=0, foreB=0,
                thighF=0, shinF=0, thighB=0, shinB=0, lift=0)


def d(theta, mirror=False):
    r = math.radians(theta)
    return (-math.sin(r) if mirror else math.sin(r), math.cos(r))


def add(p, v, k):
    return (p[0] + v[0] * k, p[1] + v[1] * k)


def skeleton(frame, view, anchor):
    f = dict(DEFAULTS, **frame)
    f.setdefault("head", f["torso"])
    j = {}
    front = view == "front"
    hip = (0.0, 0.0)
    j["hip"] = hip
    neck = add(hip, d(f["torso"]), TORSO)
    j["shoulder"] = neck
    j["head"] = add(neck, d(f["head"]), NECK)
    for side, mirror in (("F", False), ("B", front)):
        sh = add(neck, (SHOULDER_HALF if side == "F" else -SHOULDER_HALF, 0), 1) if front else neck
        hp = add(hip, (HIP_HALF if side == "F" else -HIP_HALF, 0), 1) if front else hip
        j["shoulder" + side] = sh
        j["hip" + side] = hp
        j["elbow" + side] = add(sh, d(f["arm" + side], mirror), UPPER_ARM)
        j["hand" + side] = add(j["elbow" + side], d(f["fore" + side], mirror), FOREARM)
        j["knee" + side] = add(hp, d(f["thigh" + side], mirror), THIGH)
        j["foot" + side] = add(j["knee" + side], d(f["shin" + side], mirror), SHIN)
    max_y = max(max(p[1] for p in j.values()), j["head"][1] + HEAD_R)
    ax = j[anchor][0]
    return {k: (p[0] - ax, p[1] - max_y - f["lift"]) for k, p in j.items()}


def bounds(sks):
    xs, ys = [], []
    for s in sks:
        for p in s.values():
            xs.append(p[0]); ys.append(p[1])
        xs += [s["head"][0] - HEAD_R, s["head"][0] + HEAD_R]
        ys.append(s["head"][1] - HEAD_R)
    return min(xs), min(ys), max(xs), max(ys + [0])


def draw_frame(img, box, sk, anim, fit):
    x0, y0, w, h = box
    minx, miny, maxx, maxy = fit
    scale = min((w - 20) / (maxx - minx + 16), (h - 20) / (maxy - miny + 12))
    cx = x0 + w / 2 - (minx + maxx) / 2 * scale
    gy = y0 + h - 14
    T = lambda p: (cx + p[0] * scale, gy + p[1] * scale)
    g = ImageDraw.Draw(img)
    g.line([(x0 + 4, gy), (x0 + w - 4, gy)], fill=(150, 150, 150), width=2)
    if anim.get("mat"):
        g.rectangle([T((minx - 4, 0))[0], gy - 3, T((maxx + 4, 0))[0], gy], fill=(120, 180, 200))
    lw = max(2, int(3.2 * scale))
    back, front = (150, 160, 190), (40, 60, 120)

    def limb(side, col):
        for a, b in (("shoulder" + side, "elbow" + side), ("elbow" + side, "hand" + side),
                     ("hip" + side, "knee" + side), ("knee" + side, "foot" + side)):
            g.line([T(sk[a]), T(sk[b])], fill=col, width=lw)

    side_col = back if anim["view"] == "side" else front
    limb("B", side_col)
    g.line([T(sk["hip"]), T(sk["shoulder"])], fill=front, width=lw + 2)
    if anim["view"] == "front":
        g.line([T(sk["shoulderB"]), T(sk["shoulderF"])], fill=front, width=lw)
        g.line([T(sk["hipB"]), T(sk["hipF"])], fill=front, width=lw)
    hx, hy = T(sk["head"])
    r = HEAD_R * scale
    g.ellipse([hx - r, hy - r, hx + r, hy + r], fill=front)
    limb("F", front)
    prop = anim["prop"]
    accent = (230, 110, 40)
    hands = ["handF", "handB"]
    if prop == "dumbbells":
        for hnd in hands:
            x, y = T(sk[hnd])
            g.rectangle([x - 4 * scale, y - 1.5 * scale, x + 4 * scale, y + 1.5 * scale], fill=accent)
    if prop == "kettlebell":
        x, y = T(sk["handF"])
        g.ellipse([x - 4.5 * scale, y + 1 * scale, x + 4.5 * scale, y + 10 * scale], fill=accent)
    if prop == "band_feet":
        for s in "FB":
            g.line([T(sk["foot" + s]), T(sk["hand" + s])], fill=(220, 60, 90), width=max(1, int(scale)))
    if prop == "band_knees":
        x, y = T(sk["kneeF"])
        g.ellipse([x - 3 * scale, y - 3 * scale, x + 3 * scale, y + 3 * scale], outline=(220, 60, 90), width=lw // 2 + 1)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "poses_preview.png"
    data = json.load(open(DATA, encoding="utf-8"))
    exs = data["exercises"]
    cw, ch, cols = 170, 170, 4
    img = Image.new("RGB", (cw * cols, ch * len(exs) // 2 + ch), "white")
    g = ImageDraw.Draw(img)
    for i, e in enumerate(exs):
        a = e["animation"]
        sks = [skeleton(fr, a["view"], a["anchor"]) for fr in a["frames"]]
        fit = bounds(sks)
        for k, sk in enumerate(sks[:2]):
            idx = i * 2 + k
            x0, y0 = (idx % cols) * cw, (idx // cols) * ch
            draw_frame(img, (x0, y0 + 14, cw, ch - 14), sk, a, fit)
            g.text((x0 + 4, y0 + 2), f"{e['id']} [{k}]", fill="black")
    img.save(out)
    print("->", out)


if __name__ == "__main__":
    main()
