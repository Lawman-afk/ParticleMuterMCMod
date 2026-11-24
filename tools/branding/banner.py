"""The mod page's banner, made as Culprit's is (its tools/branding/banner.py, laid out as Ground Burst's): a picture of
a normally generated world (the showcase run's cherry grove) with the title, the stripe in the mod's colours and the
tagline over it.

The picture is the showcase run's (`gradlew runShowcase -PshotSize=1920x1080`: a fresh default world, the village nearest
its spawn as the world made it, scanned for ten seconds and photographed; devworld/Showcase). Two layouts:
- `scene`, 1800 x 600: the picture across the whole banner, the title, stripe and tagline centred over the land
  darkened under them;
- `wide`, 1600 x 400: the title, stripe and tagline on the left over the picture blurred into the dark, the village
  standing clear on the right.

Usage: <Ground Burst's tools/.venv>\\Scripts\\python.exe tools\\branding\\banner.py [--source <png>] [--top <row>]
       [--focus <x>] [--out <folder>]
--top: the picture's first row in the scene banner (it shows 640 rows of the 1920 x 1080 picture); --focus: the
picture's column the wide banner keeps in view. Writes banner_scene and banner_wide (PNG and JPEG) into the folder
(default build/branding), and the scene banner as docs/branding/banner.jpg.
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

import style as S

SOURCE = S.ROOT / "run" / "screenshots" / "showcase_grove_s.png"
BANNER = S.ROOT / "docs" / "branding" / "banner.jpg"


def type_block(W: int, H: int, cap: float, top: float, x_mid: float | None, left: float | None, title_style: str) -> Image.Image:
    """The title, the stripe and the tagline as one layer: centred on x_mid, or from `left`."""
    scale = cap / 84
    text = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    tw = S.title_width(cap)
    x0 = x_mid - tw / 2 if x_mid is not None else left
    S.draw_title(text, x0, top, cap, style=title_style)
    sw, sh = round(S.STRIPE_W * 1.25 * scale), round(S.STRIPE_H * scale)
    stripe = S.stripe(sw, sh, steps=6, gap=sh * 0.45, slant=1.0)
    stripe_top = round(top + cap + 30 * scale)
    text.alpha_composite(stripe, (round(x_mid - sw / 2) if x_mid is not None else round(left), stripe_top))
    tag_cap = 31 * scale
    tag_top = stripe_top + sh + 24 * scale
    tx = x_mid - S.tagline_width(tag_cap) / 2 if x_mid is not None else left
    S.draw_tagline(text, tx, tag_top, tag_cap)
    return text


def block_height(cap: float) -> float:
    scale = cap / 84
    return cap + 30 * scale + S.STRIPE_H * scale + 24 * scale + 31 * scale


def scene_banner(src: Image.Image, top_row: int, cap: float = 88, bottom: float = 40, title_style: str = "glow") -> Image.Image:
    W, H = 1800, 600
    k = W / 1920
    rows = round(H / k)
    pic = src.crop((0, top_row, 1920, top_row + rows)).resize((W, H), Image.LANCZOS)
    pic = ImageEnhance.Color(ImageEnhance.Contrast(pic).enhance(1.05)).enhance(1.08).convert("RGBA")
    block = block_height(cap)
    top = H - bottom - block
    xs, ys = S.grid(W, H)
    # The land darkens toward the bottom, most under the type, so the title reads over the village and its boxes.
    fall = S.smoothstep(top - 150, H, ys) * 0.80
    scrim = np.exp(-(((xs - W / 2) / 720) ** 2 + ((ys - (top + block / 2)) / 120) ** 4)) * 0.88     # (the labels behind the type fade)
    pic = S.darken(pic, S.NIGHT, S.mask(np.maximum(fall, scrim)))
    edge = S.smoothstep(0.62, 1.15, np.hypot((xs - W / 2) / (W / 2), (ys - H * 0.40) / (H * 0.9))) * 0.55
    pic = S.darken(pic, S.NIGHT, S.mask(edge))
    text = type_block(W, H, cap, top, W / 2, None, title_style)
    pic.alpha_composite(S.shadow_of(text, 7, 0.6, (0, 3)))
    pic.alpha_composite(text)
    return pic.convert("RGB")


def wide_banner(src: Image.Image, focus: int, cap: float = 74, left: float = 90, at: float = 1250, fade=(900, 1180),
                rows=(220, 820), title_style: str = "glow") -> Image.Image:
    W, H = 1600, 400
    k = H / (rows[1] - rows[0])
    pic = src.crop((0, rows[0], 1920, rows[1])).resize((round(1920 * k), H), Image.LANCZOS).convert("RGBA")
    px = round(at - focus * k)
    cover = src.resize((W, round(1080 * W / 1920)), Image.LANCZOS)
    cy = round((cover.height - H) * 0.5)
    back = cover.crop((0, cy, W, cy + H)).filter(ImageFilter.GaussianBlur(46)).convert("RGBA")
    back = S.tint(back, S.NIGHT, 0.82)
    xs, ys = S.grid(W, H)
    sharp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if px >= 0:
        sharp.alpha_composite(pic, (px, 0))
    else:
        sharp.alpha_composite(pic.crop((-px, 0, min(pic.width, W - px), H)))
    a = S.smoothstep(fade[0], fade[1], xs) * (0.55 + 0.45 * S.smoothstep(0, 60, ys) * S.smoothstep(H, H - 70, ys))
    sharp = S.darken(sharp, S.NIGHT, S.mask(S.smoothstep(fade[1] + 160, fade[0], xs) * 0.55))
    sharp.putalpha(S.mask(a * (np.asarray(sharp.getchannel("A")) / 255.0)))
    back.alpha_composite(sharp)
    top = (H - block_height(cap)) / 2
    text = type_block(W, H, cap, top, None, left, title_style)
    back.alpha_composite(S.shadow_of(text, 6, 0.4, (0, 2)))
    back.alpha_composite(text)
    return back.convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=str(SOURCE))
    ap.add_argument("--top", type=int, default=230)
    ap.add_argument("--focus", type=int, default=960)
    ap.add_argument("--title", default="glow", choices=("glow", "white"))
    ap.add_argument("--out", default=str(S.ROOT / "build" / "branding"))
    ap.add_argument("--no-docs", action="store_true", help="leave docs/branding/banner.jpg as it is")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    src = Image.open(args.source).convert("RGB")
    if src.size != (1920, 1080):
        src = src.resize((1920, 1080), Image.LANCZOS)
    scene = scene_banner(src, args.top, title_style=args.title)
    wide = wide_banner(src, args.focus, title_style=args.title)
    for name, img in (("banner_scene", scene), ("banner_wide", wide)):
        img.save(out / f"{name}.png", optimize=True)
        img.save(out / f"{name}.jpg", quality=92, subsampling=0)
        print(name, img.size, out / f"{name}.png")
    if not args.no_docs:
        BANNER.parent.mkdir(parents=True, exist_ok=True)
        scene.save(BANNER, quality=92, subsampling=0)
        print("banner", BANNER)


if __name__ == "__main__":
    main()
