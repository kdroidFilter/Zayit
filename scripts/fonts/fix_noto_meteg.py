#!/usr/bin/env python3
"""Moves the meteg (U+05BD) beside the below-vowel it follows in the bundled Noto Hebrew fonts.

Noto Serif Hebrew and Noto Rashi Hebrew anchor the meteg and the below vowels at the same point,
so a tsere + meteg reads as a qamats (issue #442). This adds an mkmk lookup that attaches the meteg
to the left of the preceding below vowel. Idempotent: a font already patched is left untouched.

Usage: python3 scripts/fonts/fix_noto_meteg.py  (requires fontTools)
"""

from pathlib import Path

from fontTools.otlLib import builder
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import otTables

FONT_DIR = Path(__file__).resolve().parents[2] / "SeforimApp/src/commonMain/composeResources/font"
FONTS = ["notoserifhebrew.ttf", "notorashihebrew.ttf"]

METEG = "uni05BD"
# Sheva, hatafs, hiriq, tsere, segol, patah, qamats, qubuts, qamats qatan.
BELOW_VOWELS = [f"uni{c:04X}" for c in (*range(0x05B0, 0x05B9), 0x05BB, 0x05C7)]
# Space between the vowel's left edge and the meteg, in font units.
GAP = 40


def bounds(font: TTFont, glyph: str):
    glyph_set = font.getGlyphSet()
    pen = BoundsPen(glyph_set)
    glyph_set[glyph].draw(pen)
    return pen.bounds


def build_mark_mark_subtable(marks, bases, glyph_map):
    # otlLib only exposes a MarkBasePos builder; MarkMarkPos has the same shape.
    base_pos = builder.buildMarkBasePosSubtable(marks, bases, glyph_map)
    sub = otTables.MarkMarkPos()
    sub.Format = 1
    sub.Mark1Coverage = base_pos.MarkCoverage
    sub.Mark2Coverage = base_pos.BaseCoverage
    sub.ClassCount = base_pos.ClassCount
    sub.Mark1Array = base_pos.MarkArray
    sub.Mark2Array = otTables.Mark2Array()
    sub.Mark2Array.Mark2Record = []
    for base_record in base_pos.BaseArray.BaseRecord:
        record = otTables.Mark2Record()
        record.Mark2Anchor = base_record.BaseAnchor
        sub.Mark2Array.Mark2Record.append(record)
    sub.Mark2Array.Mark2Count = len(sub.Mark2Array.Mark2Record)
    return sub


def already_patched(gpos) -> bool:
    for lookup in gpos.LookupList.Lookup:
        if lookup.LookupType != 6:
            continue
        for sub in lookup.SubTable:
            if sub.Mark1Coverage.glyphs == [METEG]:
                return True
    return False


def patch(path: Path) -> None:
    font = TTFont(path)
    gpos = font["GPOS"].table
    gdef = font["GDEF"].table
    if already_patched(gpos):
        print(f"{path.name}: already patched")
        return

    vowels = [g for g in BELOW_VOWELS if g in font.getGlyphOrder()]
    meteg_x_max = bounds(font, METEG)[2]
    subtable = build_mark_mark_subtable(
        {METEG: (0, builder.buildAnchor(meteg_x_max, 0))},
        {v: {0: builder.buildAnchor(bounds(font, v)[0] - GAP, 0)} for v in vowels},
        font.getReverseGlyphMap(),
    )

    # Skip every mark but the below vowels and the meteg (dagesh, teamim) when looking back.
    mark_sets = gdef.MarkGlyphSetsDef
    mark_sets.Coverage.append(builder.buildCoverage([METEG, *vowels], font.getReverseGlyphMap()))
    mark_sets.MarkSetCount = len(mark_sets.Coverage)

    lookup = otTables.Lookup()
    lookup.LookupType = 6
    lookup.LookupFlag = 0x0010  # UseMarkFilteringSet
    lookup.MarkFilteringSet = mark_sets.MarkSetCount - 1
    lookup.SubTable = [subtable]
    lookup.SubTableCount = 1
    gpos.LookupList.Lookup.append(lookup)
    gpos.LookupList.LookupCount = len(gpos.LookupList.Lookup)
    lookup_index = gpos.LookupList.LookupCount - 1

    for record in gpos.FeatureList.FeatureRecord:
        if record.FeatureTag == "mkmk":
            record.Feature.LookupListIndex.append(lookup_index)
            record.Feature.LookupCount = len(record.Feature.LookupListIndex)

    font.save(path)
    print(f"{path.name}: patched")


if __name__ == "__main__":
    for name in FONTS:
        patch(FONT_DIR / name)
