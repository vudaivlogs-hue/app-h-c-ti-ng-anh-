#!/usr/bin/env python3
"""Optimize Enzii Dict bamboo rat mascot & logo assets into lightweight WebP drawables."""

from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT_DIR = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = Path(
    r"C:\Users\ADMIN\.gemini\antigravity\brain\2253dde5-b321-4d21-be79-eb1d0d16afd2"
)
RES_DRAWABLE_NODPI = ROOT_DIR / "app" / "src" / "main" / "res" / "drawable-nodpi"

SOURCES = {
    "mascot_dui_inquisitive.webp": ARTIFACT_DIR / "enzii_mascot_inquisitive_1795803794463.jpg",
    "mascot_dui_focused.webp": ARTIFACT_DIR / "enzii_mascot_focused_1795804176331.jpg",
    "mascot_dui_happy.webp": ARTIFACT_DIR / "enzii_mascot_celebratory_1795804288839.jpg",
    "mascot_dui_sleepy.webp": ARTIFACT_DIR / "enzii_mascot_sleepy_1795804386539.jpg",
    "mascot_dui_smart.webp": ARTIFACT_DIR / "enzii_mascot_scholarly_1795804502185.jpg",
}
BANNER_SRC = ARTIFACT_DIR / ".user_uploaded" / "media_1795803565718_73dcfd39.jpg"


def convert_square_webp(src: Path, dest: Path, size: int = 384, quality: int = 82) -> int:
    with Image.open(src) as img:
        rgb = img.convert("RGB")
        resized = rgb.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(dest, format="WEBP", quality=quality, method=6)
    return dest.stat().st_size


def convert_banner_webp(src: Path, dest: Path, width: int = 768, height: int = 432, quality: int = 84) -> int:
    with Image.open(src) as img:
        rgb = img.convert("RGB")
        src_w, src_h = rgb.size
        target_ratio = width / height
        src_ratio = src_w / src_h
        if src_ratio > target_ratio:
            new_w = int(src_h * target_ratio)
            left = (src_w - new_w) // 2
            rgb = rgb.crop((left, 0, left + new_w, src_h))
        elif src_ratio < target_ratio:
            new_h = int(src_w / target_ratio)
            top = (src_h - new_h) // 2
            rgb = rgb.crop((0, top, src_w, top + new_h))
        resized = rgb.resize((width, height), Image.Resampling.LANCZOS)
        resized.save(dest, format="WEBP", quality=quality, method=6)
    return dest.stat().st_size


def build_adaptive_foreground_webp(src: Path, dest: Path, canvas_size: int = 432) -> int:
    """
    Create a 432x432 transparent RGBA adaptive-icon foreground where the Dui mascot
    sits cleanly inside a cream-and-gold circular medallion occupying ~64% of the canvas,
    guaranteeing zero clipping across circle/squircle/rounded-square Android launcher masks.
    """
    with Image.open(src) as img:
        rgb = img.convert("RGB")
        w, h = rgb.size
        # Tight crop around the chubby mascot silhouette (keep headphones + paws cleanly inside)
        crop_box = (int(w * 0.14), int(h * 0.05), int(w * 0.86), int(h * 0.80))
        cropped = rgb.crop(crop_box)

    badge_diameter = int(canvas_size * 0.64)  # ~276 px inside 432x432 (Android safe zone is 66% = 288 px)
    badge_offset = (canvas_size - badge_diameter) // 2

    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))

    # Soft shadow under medallion
    shadow = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.ellipse(
        [
            badge_offset,
            badge_offset + 6,
            badge_offset + badge_diameter,
            badge_offset + badge_diameter + 6,
        ],
        fill=(8, 47, 45, 95),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=8))
    canvas = Image.alpha_composite(canvas, shadow)

    # Cream circular mask + warm gold outer ring
    medallion_img = cropped.resize((badge_diameter, badge_diameter), Image.Resampling.LANCZOS).convert("RGBA")
    mask = Image.new("L", (badge_diameter, badge_diameter), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([3, 3, badge_diameter - 4, badge_diameter - 4], fill=255)
    medallion_img.putalpha(mask)

    canvas.paste(medallion_img, (badge_offset, badge_offset), medallion_img)

    ring_draw = ImageDraw.Draw(canvas)
    ring_draw.ellipse(
        [
            badge_offset + 2,
            badge_offset + 2,
            badge_offset + badge_diameter - 3,
            badge_offset + badge_diameter - 3,
        ],
        outline=(245, 158, 11, 255),  # Warm Amber #F59E0B
        width=6,
    )

    canvas.save(dest, format="WEBP", quality=86, method=6)
    return dest.stat().st_size


def main() -> None:
    RES_DRAWABLE_NODPI.mkdir(parents=True, exist_ok=True)
    total_bytes = 0

    for out_name, src_path in SOURCES.items():
        if not src_path.exists():
            raise FileNotFoundError(f"Missing mascot source: {src_path}")
        size = convert_square_webp(src_path, RES_DRAWABLE_NODPI / out_name)
        total_bytes += size
        print(f"Generated {out_name}: {size:,} bytes")

    if not BANNER_SRC.exists():
        raise FileNotFoundError(f"Missing banner source: {BANNER_SRC}")
    banner_size = convert_banner_webp(BANNER_SRC, RES_DRAWABLE_NODPI / "logo_enzii_banner.webp")
    total_bytes += banner_size
    print(f"Generated logo_enzii_banner.webp: {banner_size:,} bytes")

    fg_size = build_adaptive_foreground_webp(
        SOURCES["mascot_dui_inquisitive.webp"],
        RES_DRAWABLE_NODPI / "ic_launcher_dui_foreground.webp",
    )
    total_bytes += fg_size
    print(f"Generated ic_launcher_dui_foreground.webp: {fg_size:,} bytes")
    print(f"TOTAL MASCOT ASSETS: {total_bytes / 1024:.1f} KB")


if __name__ == "__main__":
    main()
