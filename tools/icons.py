"""Draws the mod's GUI sheet, textures/gui/icons.png (64x16): the 11x11 button in four states.

    u 0  muted (the slash), as on the inventory button      u 11  the same, under the mouse
    u 22 not muted (no slash), as on a list row               u 33  the same, under the mouse

A small bevelled button in vanilla's manner, as the sound mod's is (light grey, white top and left edges, dark bottom
and right), with a spark of particles on it, crossed by a red slash when muted. Pure Python (zlib), no other library.

    python tools/icons.py
"""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/particlemuter/textures/gui/icons.png"

COLOURS = {
    "A": (255, 255, 255, 255),   # light edge
    "B": (138, 138, 138, 255),   # the corner between the light and dark edges
    "E": (52, 52, 52, 255),      # dark edge
    "D": (189, 189, 189, 255),   # face
    "H": (136, 146, 201, 255),   # face under the mouse
    "C": (174, 0, 0, 255),       # slash
    "Y": (232, 162, 28, 255),    # spark arms
    "W": (255, 228, 107, 255),   # spark centre
    "O": (196, 98, 16, 255),     # small sparks
}

# A four-pointed spark at the top right and two small ones at the bottom left.
SPARKS = {(6, 1): "Y", (6, 2): "Y", (6, 4): "Y", (6, 5): "Y", (4, 3): "Y", (5, 3): "Y", (7, 3): "Y", (8, 3): "Y", (6, 3): "W",
          (3, 6): "O", (2, 7): "O", (3, 7): "W", (4, 7): "O", (3, 8): "O", (8, 6): "O"}


def button(slash: bool, hover: bool) -> list[list[str]]:
    g = [["D"] * 11 for _ in range(11)]
    for x in range(11):
        g[0][x] = "A"
        g[10][x] = "E"
    for y in range(11):
        g[y][0] = "A"
        g[y][10] = "E"
    g[0][10] = "B"
    g[10][0] = "A"
    face = "H" if hover else "D"
    for y in range(1, 10):
        for x in range(1, 10):
            g[y][x] = face
    for (x, y), c in SPARKS.items():
        g[y][x] = c
    if slash:
        for i in range(1, 10):
            g[i][i] = "C"
    return g


def png(width: int, height: int, pixels: list[list[tuple]]) -> bytes:
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in pixels)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main() -> None:
    sheet = [[(0, 0, 0, 0)] * 64 for _ in range(16)]
    for n, (slash, hover) in enumerate(((True, False), (True, True), (False, False), (False, True))):
        for y, row in enumerate(button(slash, hover)):
            for x, c in enumerate(row):
                sheet[y][n * 11 + x] = COLOURS[c]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png(64, 16, sheet))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
