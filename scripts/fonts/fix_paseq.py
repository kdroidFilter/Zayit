#!/usr/bin/env python3
"""Shows the paseq (U+05C0) in the bundled fonts that hide it (issue #473).

- Shofar Demi Bold hides every ta'am (GSUB turns it into a dotted circle, then into a zero-width
  space) and lists the paseq among them, though it separates words; its glyph is a zero-width mark.
  The paseq is taken out of that substitution.
- Hadasim CLM maps the paseq to an empty glyph.

In both, the paseq is drawn as a bar as thick as the font's stems, slanted in the oblique fonts.
Idempotent: a font whose paseq already has an outline is left untouched.

Usage: python3 scripts/fonts/fix_paseq.py  (requires fontTools)
"""

from dataclasses import dataclass
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

FONT_DIR = Path(__file__).resolve().parents[2] / "SeforimApp/src/commonMain/composeResources/font"


@dataclass(frozen=True)
class Bar:
    left: int
    stem: int
    height: int
    advance: int
    # x shift per unit of height, for the oblique fonts
    slant: float = 0.0


# Shofar: Shofar Regular's paseq, thickened for the Demi Bold weight and slanted like Shofar Regular
# Oblique's. Hadasim: the stem of its own "|" glyph, slanted back like its "|" in the oblique fonts.
FONTS = {
    "ShofarDemi-Bold.ttf": Bar(left=150, stem=220, height=1180, advance=520),
    "ShofarDemi-BoldOblique.ttf": Bar(left=150, stem=220, height=1180, advance=520, slant=-0.2305),
    "HadasimCLM-Regular.otf": Bar(left=110, stem=141, height=1150, advance=360),
    "HadasimCLM-RegularOblique.otf": Bar(left=260, stem=141, height=1150, advance=360, slant=-0.23),
    "HadasimCLM-Bold.otf": Bar(left=110, stem=191, height=1150, advance=410),
    "HadasimCLM-BoldOblique.otf": Bar(left=270, stem=191, height=1150, advance=410, slant=-0.23),
}


def paseq_glyph(font: TTFont) -> str:
    return font.getBestCmap()[0x05C0]


def has_outline(font: TTFont, glyph: str) -> bool:
    glyph_set = font.getGlyphSet()
    pen = BoundsPen(glyph_set)
    glyph_set[glyph].draw(pen)
    return pen.bounds is not None


def drop_from_substitutions(font: TTFont, glyph: str) -> None:
    if "GSUB" not in font:
        return
    for lookup in font["GSUB"].table.LookupList.Lookup:
        for sub in lookup.SubTable:
            mapping = getattr(sub, "mapping", None)
            if mapping and glyph in mapping:
                del mapping[glyph]


def draw_bar(pen, bar: Bar) -> None:
    top_shift = round(bar.height * bar.slant)
    pen.moveTo((bar.left, 0))
    pen.lineTo((bar.left + top_shift, bar.height))
    pen.lineTo((bar.left + bar.stem + top_shift, bar.height))
    pen.lineTo((bar.left + bar.stem, 0))
    pen.closePath()


def set_glyph(font: TTFont, glyph: str, bar: Bar) -> None:
    if "glyf" in font:
        pen = TTGlyphPen(font.getGlyphSet())
        draw_bar(pen, bar)
        outline = pen.glyph()
        outline.recalcBounds(font["glyf"])
        font["glyf"][glyph] = outline
        x_min = outline.xMin
    else:
        cff = font["CFF "].cff
        top_dict = cff.topDictIndex[0]
        private = top_dict.Private
        pen = T2CharStringPen(bar.advance - getattr(private, "nominalWidthX", 0), font.getGlyphSet())
        draw_bar(pen, bar)
        top_dict.CharStrings[glyph] = pen.getCharString(private=private, globalSubrs=cff.GlobalSubrs)
        x_min = min(bar.left, bar.left + round(bar.height * bar.slant))
    font["hmtx"][glyph] = (bar.advance, x_min)


def patch(path: Path, bar: Bar) -> None:
    font = TTFont(path)
    glyph = paseq_glyph(font)
    if has_outline(font, glyph) and font["hmtx"][glyph][0] == bar.advance:
        print(f"{path.name}: already patched")
        return
    drop_from_substitutions(font, glyph)
    set_glyph(font, glyph, bar)
    font.save(path)
    print(f"{path.name}: patched")


if __name__ == "__main__":
    for name, bar in FONTS.items():
        patch(FONT_DIR / name, bar)
