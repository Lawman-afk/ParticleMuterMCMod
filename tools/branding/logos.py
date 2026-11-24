"""Particle Muter's logo examples, in the clean look of utility mods (flat shapes, smooth edges, few colours, as the
user asked for Culprit's), each with the user's stripe across the bottom-right corner.

Examples:
- spark:   a white four-pointed spark and two small ones, crossed by a red slash, on slate;
- ring:    the white spark inside a red "no" ring, on slate;
- button:  the inventory button itself, drawn smooth: a light rounded square, the orange spark, the red slash;
- dots:    a puff of white particles of three sizes fading out, the slash across them, on deep blue.

Each is drawn four times over and smoothed down. Writes logo_<name>.png (400 px) into the folder given (default
build/branding) and a sheet of them all at 400, 128 and 48 px beside them.

Usage: <a Python with Pillow> tools/branding/logos.py [--out <folder>]
"""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

N = 400
SS = 4
M = N * SS

SLATE = (40, 46, 60)
DEEP = (24, 34, 66)
WHITE = (244, 246, 250)
RED = (214, 52, 52)
ORANGE = (240, 160, 40)
GOLD = (255, 222, 110)
LIGHT = (205, 208, 214)
STRIPE = [(240, 160, 40), (250, 250, 250)]          # the stripe: the spark's orange and white


def p(v: float) -> float:
    return v * M


def spark(d: ImageDraw.ImageDraw, cx: float, cy: float, r: float, waist: float, fill) -> None:
    """A four-pointed star: points r from the middle, its sides drawn in to waist."""
    pts = []
    for k in range(8):
        a = math.pi / 4 * k - math.pi / 2
        rr = r if k % 2 == 0 else r * waist
        pts.append((p(cx + rr * math.cos(a)), p(cy + rr * math.sin(a))))
    d.polygon(pts, fill=fill)


def slash(d: ImageDraw.ImageDraw, x0: float, y0: float, x1: float, y1: float, w: float, fill) -> None:
    d.line((p(x0), p(y0), p(x1), p(y1)), fill=fill, width=int(p(w)))
    for x, y in ((x0, y0), (x1, y1)):
        d.ellipse((p(x - w / 2), p(y - w / 2), p(x + w / 2), p(y + w / 2)), fill=fill)


def stripe_band(img: Image.Image) -> None:
    band = Image.new("RGB", img.size, STRIPE[0])
    d = ImageDraw.Draw(band)
    step = 0.05
    for i in range(-30, 30):
        a = i * step
        if i % 2:
            d.polygon([(p(a), 0), (p(a + step), 0), (p(a + step - 1.2), p(1.2)), (p(a - 1.2), p(1.2))], fill=STRIPE[1])
    mask = Image.new("L", img.size, 0)
    md = ImageDraw.Draw(mask)
    lo, hi = 1.56, 1.74                                      # the band between x + y = lo and x + y = hi
    md.polygon([(p(lo - 1.0), p(1.0)), (p(1.0), p(lo - 1.0)), (p(1.0), p(hi - 1.0)), (p(hi - 1.0), p(1.0))], fill=255)
    img.paste(band, (0, 0), mask)


def base(colour) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGB", (M, M), colour)
    return img, ImageDraw.Draw(img)


def ex_spark() -> Image.Image:
    img, d = base(SLATE)
    spark(d, 0.46, 0.42, 0.27, 0.30, WHITE)
    spark(d, 0.22, 0.70, 0.085, 0.34, WHITE)
    spark(d, 0.72, 0.18, 0.06, 0.34, WHITE)
    slash(d, 0.20, 0.17, 0.72, 0.69, 0.075, RED)
    stripe_band(img)
    return img


def ex_ring() -> Image.Image:
    img, d = base(SLATE)
    spark(d, 0.45, 0.43, 0.22, 0.30, WHITE)
    cx, cy, r, w = 0.45, 0.43, 0.32, 0.07
    d.ellipse((p(cx - r), p(cy - r), p(cx + r), p(cy + r)), outline=RED, width=int(p(w)))
    k = (r - w / 2) / math.sqrt(2)
    slash(d, cx - k, cy - k, cx + k, cy + k, w, RED)
    stripe_band(img)
    return img


def ex_button() -> Image.Image:
    img, d = base(SLATE)
    x0, y0, x1, y1 = 0.12, 0.10, 0.76, 0.74
    d.rounded_rectangle((p(x0), p(y0), p(x1), p(y1)), radius=int(p(0.08)), fill=LIGHT)
    spark(d, 0.50, 0.34, 0.17, 0.32, ORANGE)
    spark(d, 0.50, 0.34, 0.06, 0.40, GOLD)
    spark(d, 0.29, 0.58, 0.075, 0.34, ORANGE)
    slash(d, 0.22, 0.20, 0.66, 0.64, 0.06, RED)
    stripe_band(img)
    return img


def ex_dots() -> Image.Image:
    img, d = base(DEEP)
    dots = [(0.30, 0.52, 0.085, 255), (0.48, 0.36, 0.07, 255), (0.58, 0.58, 0.05, 230), (0.70, 0.30, 0.045, 200),
            (0.40, 0.20, 0.035, 170), (0.22, 0.32, 0.04, 200), (0.78, 0.50, 0.03, 140), (0.52, 0.12, 0.025, 120)]
    for x, y, r, a in dots:
        c = tuple(int(DEEP[i] + (WHITE[i] - DEEP[i]) * a / 255) for i in range(3))
        d.rounded_rectangle((p(x - r), p(y - r), p(x + r), p(y + r)), radius=int(p(r * 0.35)), fill=c)
    slash(d, 0.18, 0.14, 0.72, 0.68, 0.07, RED)
    stripe_band(img)
    return img


EXAMPLES = {"spark": ex_spark, "ring": ex_ring, "button": ex_button, "dots": ex_dots}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(Path(__file__).resolve().parents[2] / "build" / "branding"))
    out = Path(ap.parse_args().out)
    out.mkdir(parents=True, exist_ok=True)
    made = {}
    for name, fn in EXAMPLES.items():
        img = fn().resize((N, N), Image.LANCZOS)
        img.save(out / f"logo_{name}.png")
        made[name] = img
    # The sheet: each at 400, 128 and 48, with its name.
    pad, row = 24, N + 40
    sheet = Image.new("RGB", (pad + (N + 128 + 48 + 3 * pad), pad + row * len(made)), (30, 30, 34))
    sd = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(made.items()):
        y = pad + i * row
        sheet.paste(img, (pad, y))
        sheet.paste(img.resize((128, 128), Image.LANCZOS), (2 * pad + N, y))
        sheet.paste(img.resize((48, 48), Image.LANCZOS), (3 * pad + N + 128, y))
        sd.text((2 * pad + N, y + 140), name, fill=(230, 230, 230))
    sheet.save(out / "logos_sheet.png")
    print("wrote", out)


if __name__ == "__main__":
    main()
