"""Particle Muter's logo, made as Culprit's was (Culprit's tools/branding/logos_flat.py): the clean look of utility mods
(flat shapes with smooth edges, few colours, no outlines), one white symbol on the dark slate ground with a soft
shadow under it, and the user's stripe, the dashed tape across the bottom-right corner, cut on the slant in eight
steps with thin gaps, in Particle Muter's own colours (style.GLOW: pale ice to violet, the game's magic particles).

The spark is the sparkle of "effects" icons: a superellipse |x|^p + |y|^p = r^p with p 0.6, four points tapering to
sharp tips, the up and down ones a little longer.

Examples (the first round):
- lens:         a white ring round a dark lens, as Culprit's glass; in it the spark lit in the colours, crossed by the
                white bar of the "no" sign;
- lens, line:   the same, the spark drawn as a line in the colours (as Culprit's chosen box);
- lens, two:    the same with a big spark and a small one, as sparkles are drawn;
- lens, glow:   the lens itself in the colours (as Culprit's "hot"), the spark dark on it;
- spark:        a white spark and a small one, crossed out by a slash that cuts its own gap, as the "off" icons are;
- spark, glow:  the same, the sparks lit in the colours;
- eye:          the eye of "hide" icons, its iris the spark, crossed out;
- light:        the lens on a light ground, in ink.

Each is drawn at four times the size and smoothed down to 1024 px, then 400.

Usage: <Ground Burst's tools/.venv>\\Scripts\\python.exe tools\\branding\\logos.py [--out <folder>] [--choose <name>]
Writes logo_<name>.png (400) and logo_<name>_1024.png into the folder (default build/branding/logos) and a sheet of
them at 400, 128 and 48 px beside it (build/branding/logos_sheet.png); --choose <name> also writes that one as
docs/branding/logo.png and its symbol alone on transparency as docs/branding/icon.png.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFont

import style as S

N = 1024                                  # every logo is made at 1024 and scaled down
SS = 4                                    # drawn at four times that, then smoothed down
M = N * SS

WHITE = S.WHITE
LENS = (28, 32, 44)                       # the lens's inside on the dark (Culprit's)
INK = (34, 38, 50)                        # the symbol on the light ground (Culprit's)
PALE = (232, 235, 241)                    # the lens on the light ground
TAPE_AT = 0.77                            # the tape's middle, on the diagonal: clear of the symbol
C = (0.47, 0.455)                         # the middle of the round symbols (up and left, away from the tape)
R, T = 0.31, 0.068                        # the ring's outer radius and its thickness
BAR = 0.062                               # the width of the "no" sign's bar
SLASH = 0.058                             # the width of an "off" slash
GAP = 0.024                               # the gap a bar or slash cuts round itself
PINCH, TALL = 0.6, 1.12                   # the spark's shape: its sides drawn in, its up and down points longer
LOGO = S.ROOT / "docs" / "branding" / "logo.png"
ICON = S.ROOT / "docs" / "branding" / "icon.png"


def px(v: float) -> float:
    return v * M


def blank() -> Image.Image:
    return Image.new("L", (M, M), 0)


def clear() -> Image.Image:
    return Image.new("RGBA", (M, M), (0, 0, 0, 0))


def disc(dr: ImageDraw.ImageDraw, cx: float, cy: float, r: float, fill=255):
    dr.ellipse((px(cx - r), px(cy - r), px(cx + r), px(cy + r)), fill=fill)


def disc_mask(cx: float, cy: float, r: float) -> Image.Image:
    m = blank()
    disc(ImageDraw.Draw(m), cx, cy, r)
    return m


def solid(colour, mask: Image.Image) -> Image.Image:
    layer = Image.new("RGBA", (M, M), colour + (255,))
    layer.putalpha(mask)
    return layer


def cut(layer: Image.Image, mask: Image.Image) -> Image.Image:
    """The layer with the mask's shape taken out of it."""
    layer.putalpha(ImageChops.subtract(layer.getchannel("A"), mask))
    return layer


def keep(layer: Image.Image, mask: Image.Image) -> Image.Image:
    """The layer kept only inside the mask."""
    layer.putalpha(ImageChops.multiply(layer.getchannel("A"), mask))
    return layer


def line_mask(a, b, width: float) -> Image.Image:
    """A straight stroke from a to b with round ends."""
    m = blank()
    dr = ImageDraw.Draw(m)
    dr.line((px(a[0]), px(a[1]), px(b[0]), px(b[1])), fill=255, width=round(px(width)))
    disc(dr, *a, width / 2)
    disc(dr, *b, width / 2)
    return m


def on_diagonal(c, d0: float, d1: float):
    """The two points d0 before and d1 after c along the slash's diagonal (top left to bottom right)."""
    k = math.sqrt(0.5)
    return (c[0] - d0 * k, c[1] - d0 * k), (c[0] + d1 * k, c[1] + d1 * k)


def slashed(layer: Image.Image, a, b, width: float = SLASH, gap: float = GAP, colour=WHITE) -> Image.Image:
    """The "off" icons' slash: a stroke from a to b with round ends over the layer, which is cut away round it by `gap`
    so the slash stands clear of what it crosses."""
    cut(layer, line_mask(a, b, width + 2 * gap))
    layer.alpha_composite(solid(colour, line_mask(a, b, width)))
    return layer


# ---- the spark ---------------------------------------------------------------------------------------------------

def spark_points(cx: float, cy: float, r: float, pinch: float = PINCH, tall: float = TALL, steps: int = 2880):
    """The spark's outline: a superellipse |x|^p + |y|^p = r^p with p under 1, its up and down points `tall` times."""
    pts = []
    for k in range(steps):
        a = 2 * math.pi * k / steps
        c, s = math.cos(a), math.sin(a)
        rr = (abs(c) ** pinch + abs(s) ** pinch) ** (-1 / pinch)
        pts.append((px(cx + r * rr * c), px(cy + r * rr * s * tall)))
    return pts


def spark_mask(cx: float, cy: float, r: float) -> Image.Image:
    """A four-pointed spark, r from its middle to its side points."""
    m = blank()
    ImageDraw.Draw(m).polygon(spark_points(cx, cy, r), fill=255)
    return m


def spark_line_mask(cx: float, cy: float, r: float, width: float) -> Image.Image:
    """The spark as a line `width` wide inside its edge: every point of it within `width` of the outside, measured
    exactly (so the line keeps its width round the curves and closes up at the points)."""
    from scipy import ndimage
    inside = np.asarray(spark_mask(cx, cy, r)) > 127
    x0, x1 = max(0, int(px(cx - r * 1.1))), min(M, int(px(cx + r * 1.1)) + 1)
    y0, y1 = max(0, int(px(cy - r * TALL * 1.1))), min(M, int(px(cy + r * TALL * 1.1)) + 1)
    box = inside[y0:y1, x0:x1]
    depth = ndimage.distance_transform_edt(box)
    line = np.zeros_like(inside)
    line[y0:y1, x0:x1] = box & (depth <= px(width))
    return Image.fromarray(np.uint8(line) * 255, "L")


def glow_fill(mask: Image.Image, mode: str, cx: float, cy: float, r: float, t0: float = 0.0, t1: float = 1.0) -> Image.Image:
    """The mod's scale across a shape: "radial", from its middle out to radius r (a spark lit from inside); "diagonal",
    from the bottom left to the top right (as Culprit's box); t0 and t1 the part of the scale used."""
    n = M // 4
    xs, ys = np.meshgrid((np.arange(n) + 0.5) / n, (np.arange(n) + 0.5) / n)
    if mode == "radial":
        t = np.hypot(xs - cx, ys - cy) / r
    else:
        t = ((xs - (cx - r)) + ((cy + r) - ys)) / (4 * r)
    t = t0 + (t1 - t0) * np.clip(t, 0, 1)
    img = Image.fromarray(np.uint8(S.glow_array(t) + 0.5), "RGB").resize((M, M), Image.BICUBIC).convert("RGBA")
    img.putalpha(mask)
    return img


def lit_spark(cx: float, cy: float, r: float) -> Image.Image:
    """A spark lit in the colours from its middle: cyan there (so a white bar over it still stands out) to violet at
    its points."""
    return glow_fill(spark_mask(cx, cy, r), "radial", cx, cy, r * TALL, 0.22, 1.0)


# ---- the ground, the tape, the logo ---------------------------------------------------------------------------------

def ground(top=(27, 30, 40), bottom=(14, 16, 22)) -> Image.Image:
    """Culprit's ground: plain slate, a little lighter at the top, a little fine noise so it does not band."""
    t = np.linspace(0, 1, N)[:, None, None]
    a = np.array(top, float) * (1 - t) + np.array(bottom, float) * t
    a = np.broadcast_to(a, (N, N, 3)) + np.random.default_rng(5).normal(0, 0.6, (N, N, 1))
    return Image.fromarray(np.uint8(np.clip(a, 0, 255) + 0.5), "RGB").convert("RGBA")


def light_ground() -> Image.Image:
    return ground((246, 247, 250), (222, 225, 232))


def corner_tape(img: Image.Image, at: float = TAPE_AT, steps: int = 8, stops=S.GLOW) -> Image.Image:
    """The user's stripe as Culprit's logo has it (its corner_tape): the scale's tape across the bottom-right corner, its
    middle at (at, at), from the bottom edge to the right edge, pale to deep, both ends running off the edges whole."""
    img = img.copy()
    seen = (2 * N - 2 * N * at) * math.sqrt(2)
    h = round(N * 0.05)
    w = round(seen + N * 0.07 + 2.4 * h)
    tape = S.stripe(w, h, steps=steps, gap=h * 0.32, slant=1.0, stops=stops).rotate(45, resample=Image.BICUBIC, expand=True)
    c = N * at
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    layer.alpha_composite(tape, (round(c - tape.width / 2), round(c - tape.height / 2)))
    img.alpha_composite(S.shadow_of(layer, 8, 0.6, (0, 5)))
    img.alpha_composite(layer)
    return img


def compose(symbol: Image.Image, base: Image.Image = None, shadow: float = 0.45) -> Image.Image:
    """The logo: the ground, the tape, the symbol over them with its soft shadow (Culprit's compose)."""
    img = corner_tape(base if base is not None else ground())
    sym = symbol.resize((N, N), Image.LANCZOS)
    img.alpha_composite(S.shadow_of(sym, 14, shadow, (0, 8)))
    img.alpha_composite(sym)
    return img


# ---- the symbols --------------------------------------------------------------------------------------------------

def ring_mask() -> Image.Image:
    return ImageChops.subtract(disc_mask(*C, R), disc_mask(*C, R - T))


def lens(spark: str = "glow", lens_fill: str = "dark", ink=WHITE, lens_colour=LENS) -> Image.Image:
    """The ring and its lens, the spark inside, the "no" bar across from the lens's top left to its bottom right (its
    ends hidden under the ring). The spark is "glow" (lit in the colours), "line" (drawn as a line in the colours),
    "two" (a big spark and a small one), "dark" (for the lit lens) or "white"; only a white spark has the bar cut a gap
    round itself in it (any other keeps its whole outline under the white bar)."""
    inside = disc_mask(*C, R - T + 0.002)
    layer = clear()
    if lens_fill == "dark":
        layer.alpha_composite(solid(lens_colour, inside))
    else:
        layer.alpha_composite(glow_fill(inside, "diagonal", *C, R - T, 0.22, 1.0))
    a, b = on_diagonal(C, R - T + 0.02, R - T + 0.02)
    if spark == "glow":
        content = lit_spark(*C, 0.185)
    elif spark == "line":
        content = glow_fill(spark_line_mask(*C, 0.19, 0.03), "diagonal", *C, 0.19, 0.0, 1.0)
    elif spark == "two":
        content = clear()
        content.alpha_composite(lit_spark(C[0] - 0.035, C[1] + 0.03, 0.15))
        content.alpha_composite(lit_spark(C[0] + 0.118, C[1] - 0.122, 0.06))
    elif spark == "dark":
        content = solid(LENS, spark_mask(*C, 0.185))         # (dark on the lit lens: whole under the white bar)
    else:
        content = cut(solid(WHITE, spark_mask(*C, 0.185)), line_mask(a, b, BAR + 2 * GAP))
    layer.alpha_composite(content)
    layer.alpha_composite(keep(solid(ink, line_mask(a, b, BAR)), inside))
    layer.alpha_composite(solid(ink, ring_mask()))
    return layer


def spark(fill: str = "white") -> Image.Image:
    """A big spark and a small one at its top right (the sparkle that means "effects" everywhere), crossed out by the
    "off" icons' slash."""
    big, small = (0.445, 0.47, 0.285), (0.75, 0.205, 0.085)
    layer = clear()
    for cx, cy, r in (big, small):
        layer.alpha_composite(solid(WHITE, spark_mask(cx, cy, r)) if fill == "white" else lit_spark(cx, cy, r))
    a, b = on_diagonal(big[:2], 0.40, 0.30)
    return slashed(layer, a, b)


def almond_mask(cx: float, cy: float, a: float, b: float, inset: float = 0.0) -> Image.Image:
    """An eye's almond: two arcs meeting at points (cx +- a, cy), b from the middle at their highest; `inset` moves both
    arcs in (a line's width, for the eye drawn as a line)."""
    rho = (a * a + b * b) / (2 * b)
    upper = disc_mask(cx, cy + (rho - b), rho - inset)          # the upper arc's circle lies below the middle
    lower = disc_mask(cx, cy - (rho - b), rho - inset)
    return ImageChops.multiply(upper, lower)


def eye() -> Image.Image:
    """The eye of "hide" icons, its iris the spark, crossed out by the "off" icons' slash."""
    a, b, w = 0.345, 0.215, 0.058
    outer, inner = almond_mask(*C, a, b), almond_mask(*C, a, b, w)
    layer = clear()
    layer.alpha_composite(solid(LENS, inner))
    layer.alpha_composite(lit_spark(*C, 0.14))
    layer.alpha_composite(solid(WHITE, ImageChops.subtract(outer, inner)))
    p, q = on_diagonal(C, 0.42, 0.34)
    return slashed(layer, p, q)


# ---- the second round: "lens, two" (the user: "definitely the best of the bunch, however something just doesn't look
# right about the stop sign"), its "no" sign changed round ------------------------------------------------------------

TWO = ((-0.035, 0.03, 0.15), (0.118, -0.122, 0.06))      # the two sparks: from the sign's middle, and their sizes
RED = (238, 86, 92)                                       # the in-game button's red slash, as a clean coral red


def drop_crumbs(layer: Image.Image, min_area: float = 0.0012) -> Image.Image:
    """Clears the crumbs a cut leaves (the tip of a spark's point left alone past a bar's gap, which reads as a stray
    speck): every separate piece smaller than `min_area` of the logo's area."""
    from scipy import ndimage
    alpha = np.asarray(layer.getchannel("A"))
    pieces, count = ndimage.label(alpha > 24)
    if count < 2:
        return layer
    sizes = ndimage.sum_labels(np.ones_like(alpha), pieces, index=np.arange(1, count + 1))
    small = np.zeros(count + 1, bool)
    small[1:] = sizes < min_area * M * M
    keep_mask = ~small[pieces]
    layer.putalpha(Image.fromarray(np.uint8(alpha * keep_mask), "L"))
    return layer


def paint(spec, mask: Image.Image, c, r) -> Image.Image:
    """A colour, or "glow": the mod's scale across the sign from its bottom left (pale) to its top right (violet)."""
    return glow_fill(mask, "diagonal", c[0], c[1], r, 0.0, 1.0) if spec == "glow" else solid(spec, mask)


def sign(sparks=TWO, ring_t: float = T, bar_w: float = BAR, bar: str = "inside", direction: str = "\\", gap: bool = False,
         ring_paint=WHITE, bar_paint=WHITE, spark_paint="glow", c=C, r: float = R, gap_w: float = GAP) -> Image.Image:
    """The lens and its sparks under a "no" sign of which every part can change:
    - ring_t, bar_w: the ring's and the bar's widths;
    - bar: "inside" (its ends under the ring, as the sign is drawn), "floating" (round ends short of the ring), "out"
      (the "off" icons' slash, through the ring and out past it, the ring cut round it), "cut" (no bar to see: only the
      gap it would leave in the sparks), or "none";
    - direction: "\\" (top left to bottom right, as the sign is drawn) or "/" (along the tape; the sparks mirrored);
    - gap: whether the bar cuts a gap round itself in the sparks, gap_w wide on each side;
    - ring_paint, bar_paint, spark_paint: a colour, "glow" (the mod's scale) or, for the sparks, "white";
    - c, r: the sign's middle and size (the sparks scaled with its lens)."""
    inner = r - ring_t
    scale = inner / (R - T)
    flip = -1 if direction == "/" else 1
    k = math.sqrt(0.5)

    def along(d0: float, d1: float):
        """Points d0 before and d1 after the middle along the bar's diagonal."""
        return (c[0] - flip * d0 * k, c[1] - d0 * k), (c[0] + flip * d1 * k, c[1] + d1 * k)

    inside = disc_mask(*c, inner + 0.002)
    layer = clear()
    layer.alpha_composite(solid(LENS, inside))
    content = clear()
    for dx, dy, sr in sparks:
        sx, sy, sr = c[0] + flip * dx * scale, c[1] + dy * scale, sr * scale
        if spark_paint == "white":
            content.alpha_composite(solid(WHITE, spark_mask(sx, sy, sr)))
        else:
            content.alpha_composite(lit_spark(sx, sy, sr))
    ring = ImageChops.subtract(disc_mask(*c, r), disc_mask(*c, inner))
    if bar == "inside":
        a, b = along(inner + 0.02, inner + 0.02)
    elif bar == "floating":
        a, b = along(inner - 0.05, inner - 0.05)
    elif bar == "out":
        a, b = along(r + 0.05, r + 0.04)
    else:
        a, b = along(inner, inner)
    if gap or bar in ("out", "cut"):
        drop_crumbs(cut(content, line_mask(a, b, bar_w + 2 * gap_w)))
    layer.alpha_composite(content)
    if bar == "out":
        cut_ring = Image.fromarray(np.uint8(np.asarray(ring) * (np.asarray(line_mask(a, b, bar_w + 2 * gap_w)) < 128)), "L")
        layer.alpha_composite(paint(ring_paint, cut_ring, c, r))
        layer.alpha_composite(paint(bar_paint, line_mask(a, b, bar_w), c, r))
        return layer
    if bar in ("inside", "floating"):
        bar_mask = line_mask(a, b, bar_w)
        if bar == "inside":
            bar_mask = ImageChops.multiply(bar_mask, inside)
        layer.alpha_composite(paint(bar_paint, bar_mask, c, r))
    layer.alpha_composite(paint(ring_paint, ring, c, r))
    return layer


MORE = {
    "lens, two (first)": lambda: lens("two"),
    "thin": lambda: sign(ring_t=0.046, bar_w=0.042, sparks=((-0.04, 0.035, 0.165), (0.125, -0.13, 0.066))),
    "centred": lambda: sign(sparks=((0.0, 0.0, 0.16), (0.12, -0.12, 0.055))),
    "apart": lambda: sign(sparks=((-0.095, 0.095, 0.095), (0.1, -0.1, 0.055))),
    "gap": lambda: sign(gap=True),
    "floating bar": lambda: sign(bar="floating"),
    "slash out": lambda: sign(bar="out", c=(0.455, 0.44), r=0.29),
    "mirrored": lambda: sign(direction="/"),
    "glow sign": lambda: sign(ring_paint="glow", bar_paint="glow", spark_paint="white"),
    "red bar": lambda: sign(bar_paint=RED),
    "thin, centred": lambda: sign(ring_t=0.046, bar_w=0.042, sparks=((0.0, 0.0, 0.17), (0.128, -0.128, 0.058))),
    "no bar": lambda: sign(bar="none"),
}


def more_examples() -> dict:
    return {name: compose(make()) for name, make in MORE.items()}


# ---- the third round: the user, "Maybe try a centre gapped one?": "centred" with the bar cutting its gap, by the gap's
# width and the sign's weight; "centred" itself to compare -------------------------------------------------------------

CENTRED = ((0.0, 0.0, 0.16), (0.12, -0.12, 0.055))
THIN_CENTRED = ((0.0, 0.0, 0.17), (0.128, -0.128, 0.058))
THIN = {"ring_t": 0.046, "bar_w": 0.042}

GAPPED = {
    "centred (round two)": lambda: sign(sparks=CENTRED),
    "centred, gap": lambda: sign(sparks=CENTRED, gap=True),
    "centred, fine gap": lambda: sign(sparks=CENTRED, gap=True, gap_w=0.013),
    "centred, wide gap": lambda: sign(sparks=CENTRED, gap=True, gap_w=0.036),
    "thin, centred, gap": lambda: sign(sparks=THIN_CENTRED, gap=True, **THIN),
    "thin, centred, fine gap": lambda: sign(sparks=THIN_CENTRED, gap=True, gap_w=0.013, **THIN),
}


def gapped_examples() -> dict:
    return {name: compose(make()) for name, make in GAPPED.items()}


# ---- the fourth round: the user, "Maybe try one inbetween sizes for centered fine gap, and thin centred fine gap?":
# the sign's weight in steps from the one to the other (the ring and bar thinner, the sparks larger as they go), all
# with the fine gap --------------------------------------------------------------------------------------------------

FINE_GAP = 0.013


def weighted(t: float) -> Image.Image:
    """The centred sign with the fine gap, t of the way from "centred, fine gap" (0) to "thin, centred, fine gap" (1)."""
    def mix(a: float, b: float) -> float:
        return a + (b - a) * t
    sparks = tuple(tuple(mix(p, q) for p, q in zip(s0, s1)) for s0, s1 in zip(CENTRED, THIN_CENTRED))
    return sign(sparks=sparks, gap=True, gap_w=FINE_GAP, ring_t=mix(T, THIN["ring_t"]), bar_w=mix(BAR, THIN["bar_w"]))


BETWEEN = {
    "centred, fine gap": lambda: weighted(0.0),
    "between, 1 (a quarter)": lambda: weighted(0.25),
    "between, 2 (halfway)": lambda: weighted(0.5),
    "between, 3 (three quarters)": lambda: weighted(0.75),
    "thin, centred, fine gap": lambda: weighted(1.0),
}


def between_examples() -> dict:
    return {name: compose(make()) for name, make in BETWEEN.items()}


SETS = {"first": ("logos", lambda: examples(), lambda: SYMBOLS),
        "more": ("logos_more", lambda: more_examples(), lambda: MORE),
        "gapped": ("logos_gapped", lambda: gapped_examples(), lambda: GAPPED),
        "between": ("logos_between", lambda: between_examples(), lambda: BETWEEN)}


SYMBOLS = {
    "lens": lambda: lens("glow"),
    "lens, line": lambda: lens("line"),
    "lens, two": lambda: lens("two"),
    "lens, glow": lambda: lens("dark", "glow"),
    "spark": lambda: spark("white"),
    "spark, glow": lambda: spark("glow"),
    "eye": eye,
    "light": lambda: lens("glow", ink=INK, lens_colour=PALE),
}


def examples() -> dict:
    out = {}
    for name, make in SYMBOLS.items():
        light = name == "light"
        out[name] = compose(make(), light_ground() if light else None, 0.18 if light else 0.45)
    return out


def sheet(logos: dict, path: Path):
    """Each logo at 400, 128 and 48 px, two to a row, named (Culprit's sheet)."""
    pad, label_h = 24, 40
    cell_w = 400 + 128 + 48 + pad * 4
    cols = 2
    rows = (len(logos) + cols - 1) // cols
    img = Image.new("RGB", (cols * cell_w + pad, rows * (400 + label_h + pad) + pad), (43, 43, 46))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(str(S.TAG_FONT), 30)
    for i, (name, logo) in enumerate(logos.items()):
        x = pad + (i % cols) * cell_w
        y = pad + (i // cols) * (400 + label_h + pad)
        d.text((x, y), name, font=f, fill=(235, 235, 235))
        y += label_h
        for size, dx in ((400, 0), (128, 400 + pad), (48, 400 + pad * 2 + 128)):
            small = logo.resize((size, size), Image.LANCZOS)
            img.paste(small, (x + dx, y), small)
    img.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=None)
    ap.add_argument("--set", default="first", choices=tuple(SETS))
    ap.add_argument("--choose", default=None)
    args = ap.parse_args()
    folder, make_logos, make_symbols = SETS[args.set]
    out = Path(args.out or S.ROOT / "build" / "branding" / folder)
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("logo_*.png"):
        old.unlink()                                        # (examples from an earlier run)
    logos = make_logos()
    symbols = make_symbols()
    for name, logo in logos.items():
        file = name.replace(", ", "_").replace(" ", "_").replace("(", "").replace(")", "")
        logo.save(out / f"logo_{file}_1024.png", optimize=True)
        logo.resize((400, 400), Image.LANCZOS).save(out / f"logo_{file}.png", optimize=True)
    sheet(logos, out.parent / f"{out.name}_sheet.png")
    if args.choose:
        LOGO.parent.mkdir(parents=True, exist_ok=True)
        logos[args.choose].resize((400, 400), Image.LANCZOS).convert("RGB").save(LOGO, optimize=True)
        sym = symbols[args.choose]().resize((N, N), Image.LANCZOS)
        cut_out = sym.crop(sym.getbbox())
        side = max(cut_out.size) + 48
        icon = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        icon.alpha_composite(cut_out, ((side - cut_out.width) // 2, (side - cut_out.height) // 2))
        icon.resize((512, 512), Image.LANCZOS).save(ICON, optimize=True)
        print("chose", args.choose, "as", LOGO, "and its symbol as", ICON)
    print("wrote", ", ".join(logos), "to", out)


if __name__ == "__main__":
    main()
