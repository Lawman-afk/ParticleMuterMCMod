"""The mod page's screenshots, from the showcase run's pictures of a normally generated world (devworld/Showcase):
before and after pictures split down the middle (the left half as the game draws it, the right half with the
particle muted, the same spot and moment of the day), and the inventory and lists as they are, all into docs/screenshots
as JPEG.

Usage: <Ground Burst's tools/.venv>\\Scripts\\python.exe tools\\branding\\shots.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

import style as S

SHOTS = S.ROOT / "run" / "screenshots"
OUT = S.ROOT / "docs" / "screenshots"
# The split pictures: (out name, before, after, left label, right label).
SPLITS = [("compare_petals", "grove_s", "grove_s_muted", "Cherry petals on", "Cherry petals muted"),
          ("compare_ash", "deltas_n", "deltas_n_muted", "Basalt delta ash on", "Basalt delta ash muted")]
# The pictures as they are: (out name, source).
AS_THEY_ARE = [("list_recent_ash", "deltas_recent"), ("list_recent_petals", "grove_recent"), ("inventory_button", "grove_inventory"),
               ("list_muted", "grove_muted_list")]


def label(img: Image.Image, text: str, x: float, right: bool):
    f = S.tag_font(34)
    w = f.getlength(text)
    pad = 18
    x0 = x - w - 2 * pad if right else x
    y0 = 40
    d = ImageDraw.Draw(img, "RGBA")
    d.rounded_rectangle((x0, y0, x0 + w + 2 * pad, y0 + 34 / 0.65 * 0.95 + 2 * pad - 12), radius=10, fill=S.NIGHT + (200,))
    d.text((x0 + pad, y0 + pad - 4), text, font=f, fill=S.WHITE + (255,))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, before, after, left, right in SPLITS:
        a = Image.open(SHOTS / f"showcase_{before}.png").convert("RGB")
        b = Image.open(SHOTS / f"showcase_{after}.png").convert("RGB")
        w, h = a.size
        out = a.copy()
        out.paste(b.crop((w // 2, 0, w, h)), (w // 2, 0))
        d = ImageDraw.Draw(out)
        d.rectangle((w // 2 - 2, 0, w // 2 + 1, h), fill=S.WHITE)
        label(out, left, 40, False)
        label(out, right, w - 40, True)
        out.save(OUT / f"{name}.jpg", quality=90, subsampling=0)
        print(name)
    for name, source in AS_THEY_ARE:
        Image.open(SHOTS / f"showcase_{source}.png").convert("RGB").save(OUT / f"{name}.jpg", quality=90, subsampling=0)
        print(name)


if __name__ == "__main__":
    main()
