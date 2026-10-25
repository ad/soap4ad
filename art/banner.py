#!/usr/bin/env python3
"""Draws the TV banner in the icon's style: banner.py <res_dir> [text] [name]. Needs Pillow and macOS Arial Bold."""
import sys
from PIL import Image, ImageDraw, ImageFont

TOP, BOTTOM = (250, 92, 126), (108, 130, 250)
FONT = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"


def banner(w, h, text):
    s = 4  # drawn 4x larger, then downsampled for smooth edges
    W, H = w * s, h * s
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        c = tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
        for x in range(W):
            px[x, y] = c

    u = H * 0.0058  # one glyph unit; the glyph is 100 x 92 units
    font = ImageFont.truetype(FONT, round(H * 0.25))
    d = ImageDraw.Draw(img)
    left, top, right, bottom = d.textbbox((0, 0), text, font=font)
    gap = W * 0.05
    k = min(1, W * 0.87 / (100 * u + gap + right - left))  # names longer than "soap4u" shrink to its width
    if k < 1:
        u, gap = u * k, gap * k
        font = ImageFont.truetype(FONT, round(H * 0.25 * k))
        left, top, right, bottom = d.textbbox((0, 0), text, font=font)
    gx, gy = (W - (100 * u + gap + right - left)) / 2, (H - 92 * u) / 2
    P = lambda x, y: (gx + x * u, gy + y * u)
    mask = Image.new("L", (W, H), 0)
    m = ImageDraw.Draw(mask)
    for end in (32, 68):
        m.line([P(50, 22), P(end, 2)], fill=255, width=round(6 * u))
        m.ellipse([P(end - 3, -1), P(end + 3, 5)], fill=255)
    m.rounded_rectangle([P(2, 22), P(98, 92)], radius=12 * u, fill=255)
    m.rounded_rectangle([P(12, 34), P(66, 80)], radius=8 * u, fill=0)
    for y in (42, 50, 58):
        m.rounded_rectangle([P(74, y - 2), P(88, y + 2)], radius=2 * u, fill=0)
    m.ellipse([P(75, 66), P(87, 78)], fill=0)
    img.paste((255, 255, 255), mask=mask)

    d.text((gx + 100 * u + gap - left, (H - (bottom - top)) / 2 - top), text, font=font, fill=(255, 255, 255))
    return img.resize((w, h), Image.LANCZOS)


out = sys.argv[1]
text = sys.argv[2] if len(sys.argv) > 2 else "Soap TV"
name = sys.argv[3] if len(sys.argv) > 3 else "banner"
banner(320, 180, text).save(f"{out}/drawable-xhdpi/{name}.png")
banner(640, 360, text).save(f"{out}/drawable-xxxhdpi/{name}.png")
