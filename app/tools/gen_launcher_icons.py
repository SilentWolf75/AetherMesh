"""Generate AetherMesh launcher mipmaps (legacy API 24-25 + preview).

Draws the brand mark inset into the Android adaptive-icon safe zone so
circular/squircle masks don't clip the signal arcs.
"""
from __future__ import annotations

from pathlib import Path
import math

from PIL import Image, ImageDraw

ROOT = Path(r"E:\Projects\Mesh C\app\app\src\main\res")

# Density → px (legacy launcher size)
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Brand colors (aligned with web-flasher/aethermesh-logo.svg)
BG0 = (6, 11, 20)
BG1 = (14, 22, 36)
BG2 = (21, 32, 51)
GRID = (255, 255, 255, 24)
GRID_X = (255, 255, 255, 15)
PEAK0 = (122, 212, 255)  # #7AD4FF
PEAK1 = (77, 163, 255)   # #4DA3FF
CYAN = (77, 163, 255)
CYAN_BRIGHT = (122, 212, 255)
MINT = (200, 245, 71)    # #C8F547
WHITE = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint_bg(draw: ImageDraw.ImageDraw, size: int):
    # Diagonal gradient
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            if t < 0.5:
                c = lerp(BG0, BG1, t * 2)
            else:
                c = lerp(BG1, BG2, (t - 0.5) * 2)
            draw.point((x, y), fill=c)

    # Subtle grid
    step = size / 6
    for i in range(1, 6):
        p = int(round(i * step))
        draw.line([(p, 0), (p, size - 1)], fill=GRID, width=max(1, size // 192))
        draw.line([(0, p), (size - 1, p)], fill=GRID, width=max(1, size // 192))
    draw.line([(0, 0), (size - 1, size - 1)], fill=GRID_X, width=max(1, size // 256))
    draw.line([(size - 1, 0), (0, size - 1)], fill=GRID_X, width=max(1, size // 256))


def xf(x: float, y: float, size: int, inset: float = 0.14):
    """Map design coords (0..108) into safe inset square."""
    s = size * (1 - 2 * inset) / 108.0
    ox = size * inset
    oy = size * inset
    return ox + x * s, oy + y * s


def stroke_width(design_w: float, size: int, inset: float = 0.14) -> int:
    s = size * (1 - 2 * inset) / 108.0
    return max(1, int(round(design_w * s)))


def draw_arc(draw, cx, cy, r, start_deg, end_deg, fill, width, steps=48):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        ang = math.radians(start_deg + (end_deg - start_deg) * t)
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    if len(pts) >= 2:
        draw.line(pts, fill=fill, width=width, joint="curve")


def paint_mark(draw: ImageDraw.ImageDraw, size: int):
    # Design space geometry (same as SVG), mapped into safe zone.
    def P(x, y):
        return xf(x, y, size)

    w_peak = stroke_width(5, size)
    w_bar = stroke_width(4, size)
    w_outer = stroke_width(3.5, size)
    w_inner = stroke_width(3, size)

    # Signal arcs (SVG: M38,20 A20,20 0 0,1 70,20 → center 54,20 radius 20)
    # Arc from 180° to 0° through top (counterclockwise in SVG with sweep=1 from left)
    # In SVG A20,20 0 0,1 from (38,20) to (70,20) with radii 20: center is (54,20),
    # going the short upper way? Actually with large-arc=0 sweep=1 from left to right
    # of a circle centered at (54,20): points (38,20) and (70,20) are on the circle.
    # Upper semicircle: angles from 180° to 0° via 270° (or -90).
    cx, cy = P(54, 20)
    # radius in px
    r_outer = abs(P(54, 20)[0] - P(38, 20)[0])
    r_inner = abs(P(54, 20)[0] - P(45, 20)[0])
    draw_arc(draw, cx, cy, r_outer, 180, 360, CYAN, w_outer)
    # Inner arc: M45,26 A12,12 → center (54,26)
    cx2, cy2 = P(54, 26)
    r_inner = abs(P(54, 26)[0] - P(45, 26)[0])
    draw_arc(draw, cx2, cy2, r_inner, 180, 360, CYAN_BRIGHT, w_inner)

    # Peak triangle legs
    p_l, p_apex, p_r = P(32, 76), P(54, 34), P(76, 76)
    draw.line([p_l, p_apex, p_r], fill=PEAK1, width=w_peak, joint="curve")
    # Soft highlight along left→apex
    draw.line([p_l, p_apex], fill=PEAK0, width=max(1, w_peak - 1), joint="curve")

    # Crossbar + stem
    draw.line([P(40, 62), P(68, 62)], fill=CYAN_BRIGHT, width=w_bar)
    draw.line([P(54, 34), P(54, 62)], fill=CYAN, width=w_bar)

    def node(x, y, r_design, color):
        cx, cy = P(x, y)
        r = abs(P(x, y)[0] - P(x - r_design, y)[0])
        bbox = [cx - r, cy - r, cx + r, cy + r]
        draw.ellipse(bbox, fill=color)

    node(54, 34, 5, WHITE)
    node(32, 76, 5, MINT)
    node(76, 76, 5, CYAN)
    node(54, 62, 4, WHITE)


def round_mask(img: Image.Image, radius_ratio: float = 0.22) -> Image.Image:
    """Optional rounded corners for round launcher variant."""
    size = img.size[0]
    radius = int(size * radius_ratio)
    mask = Image.new("L", (size, size), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img.convert("RGBA"), mask=mask)
    return out


def render(size: int) -> Image.Image:
    # Supersample for cleaner strokes
    ss = 4
    big = size * ss
    img = Image.new("RGB", (big, big), BG0)
    draw = ImageDraw.Draw(img, "RGBA")
    paint_bg(draw, big)
    paint_mark(draw, big)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def main():
    for folder, size in SIZES.items():
        out_dir = ROOT / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = render(size)
        icon.save(out_dir / "ic_launcher.webp", "WEBP", quality=95, method=6)
        round_mask(icon).save(out_dir / "ic_launcher_round.webp", "WEBP", quality=95, method=6)
        print(f"wrote {folder} {size}x{size}")


if __name__ == "__main__":
    main()
