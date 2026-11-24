"""Particle Muter's branding style: its colours, type and stripe, made as Culprit's are (Culprit's tools/branding/
style.py): the same dark slate ground, the same type (Russo One for titles, Rajdhani Bold for words), and the user's
stripe across the corner. Culprit's colours are its cost scale; Particle Muter's are the cool colours of the game's
magic particles (end rods' pale light, soul fire's cyan, enchanting glyphs' and portals' blues and violets), pale to
deep, as a scale of six stops. Both fonts are under the SIL Open Font License (their licences beside them in fonts/).
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
FONTS = HERE / "fonts"
TITLE_FONT = FONTS / "RussoOne-Regular.ttf"
TAG_FONT = FONTS / "Rajdhani-Bold.ttf"

# The mod's scale: pale ice to deep violet (end rod, soul fire, enchanting glyphs, portal).
GLOW = [(0xE8, 0xFF, 0xF7), (0x9E, 0xF4, 0xE6), (0x52, 0xDA, 0xF2), (0x42, 0xA4, 0xFF), (0x6C, 0x6C, 0xFF), (0x9C, 0x4C, 0xEE)]
WHITE = (242, 244, 248)
SLATE = (16, 18, 22)             # Culprit's panel (the gaps in the stripe)
NIGHT = (11, 12, 17)


def glow(t: float, stops=GLOW) -> tuple:
    """The scale's colour at t (0 to 1)."""
    x = min(max(t, 0.0), 1.0) * (len(stops) - 1)
    i = min(int(x), len(stops) - 2)
    f = x - i
    a, b = stops[i], stops[i + 1]
    return tuple(round(a[k] * (1 - f) + b[k] * f) for k in range(3))


def glow_array(t: np.ndarray, stops=GLOW) -> np.ndarray:
    """The scale over an array of t (0 to 1): an array of RGB."""
    t = np.clip(t, 0.0, 1.0)
    rgb = np.zeros(t.shape + (3,))
    for i in range(3):
        rgb[..., i] = np.interp(t * (len(stops) - 1), np.arange(len(stops)), np.array([c[i] for c in stops], float))
    return rgb


def stripe(width: int, height: int, steps: int = 8, gap: float = 0.0, slant: float = 1.0, supersample: int = 4,
           stops=GLOW) -> Image.Image:
    """The user's stripe, exactly as Culprit's (its heat_stripe): `steps` blocks along the scale, each cut on a slant with
    a thin gap between them; the stripe's own ends cut the same way."""
    s = supersample
    w, h = width * s, height * s
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lean = h * slant
    g = gap * s
    step = (w - lean) / steps
    for i in range(steps):
        x0 = i * step + (g / 2 if i else 0)
        x1 = (i + 1) * step - (g / 2 if i < steps - 1 else 0)
        col = glow(i / max(steps - 1, 1), stops)
        d.polygon([(x0 + lean, 0), (x1 + lean, 0), (x1, h), (x0, h)], fill=col + (255,))
    return img.resize((width, height), Image.LANCZOS)


def shadow_of(layer: Image.Image, radius: float, opacity: float, offset=(0, 0)) -> Image.Image:
    """A soft dark shadow of a layer's opaque parts."""
    alpha = layer.getchannel("A").point(lambda a: int(a * opacity))
    sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    sh.putalpha(alpha)
    sh = sh.filter(ImageFilter.GaussianBlur(radius))
    out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    return out


def title_font(cap: float) -> ImageFont.FreeTypeFont:
    """Russo One at the size whose capitals are `cap` pixels tall (its H is 0.70 of the size)."""
    return ImageFont.truetype(str(TITLE_FONT), round(cap / 0.70))


def tag_font(cap: float) -> ImageFont.FreeTypeFont:
    """Rajdhani Bold at the size whose capitals are `cap` pixels tall (its A is 0.65 of the size)."""
    return ImageFont.truetype(str(TAG_FONT), round(cap / 0.65))
