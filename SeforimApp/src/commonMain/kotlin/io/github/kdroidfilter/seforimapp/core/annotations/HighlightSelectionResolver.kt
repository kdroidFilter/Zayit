package io.github.kdroidfilter.seforimapp.core.annotations

import io.github.kdroidfilter.seforimapp.core.presentation.text.stripNikudTeamimWithMap

/**
 * Resolves the character range a highlight should cover, given the selected text and a
 * line's plain text.
 *
 * Offsets are returned in the line's ORIGINAL coordinates (with diacritics), so a stored
 * highlight stays valid regardless of the current diacritics display setting.
 *
 * [linePlainText] MUST be the plain text of the rendered line WITH diacritics (i.e.
 * `buildAnnotatedFromHtml(line.content, ...).text`) so offsets align with what
 * [io.github.kdroidfilter.seforimapp.core.presentation.text.applyUserHighlights] applies.
 *
 * Returns null when the selection cannot be located within the line (e.g. a multi-line
 * selection whose text is longer than this line); the caller decides how to degrade.
 */
fun resolveHighlightRange(
    linePlainText: String,
    selectedText: String,
    showDiacritics: Boolean,
): IntRange? {
    val needle = selectedText.trim()
    if (needle.isEmpty() || linePlainText.isEmpty()) return null

    if (showDiacritics) {
        val start = linePlainText.indexOf(needle)
        if (start < 0) return null
        return start until (start + needle.length)
    }

    // Diacritics hidden: the selection is in stripped space (matching the rendered text, which
    // keeps ׳ ״). Locate it there, then map the bounds back to original coordinates for storage.
    val (strippedText, strippedToOriginal) = stripNikudTeamimWithMap(linePlainText)
    val start = strippedText.indexOf(needle)
    if (start < 0) return null
    val end = start + needle.length
    val originalStart = if (start < strippedToOriginal.size) strippedToOriginal[start] else linePlainText.length
    val originalEnd = if (end < strippedToOriginal.size) strippedToOriginal[end] else linePlainText.length
    if (originalEnd <= originalStart) return null
    return originalStart until originalEnd
}

/** A resolved highlight range for one line, in original (with-diacritics) coordinates. */
data class LineHighlightRange(
    val lineId: Long,
    val range: IntRange,
)

/**
 * Resolves the highlight ranges for a (possibly multi-line) selection.
 *
 * The platform joins selected lines with '\n', so [selectedText] is split into per-line
 * segments. [sortedVisibleLines] are all currently materialized lines as
 * (lineId, plain-text-with-diacritics), ordered by line index. The first segment is anchored
 * on the visible line that ends with it; each following segment maps to the next consecutive
 * line. Each (line, segment) pair is then resolved with [resolveHighlightRange], so the first
 * line keeps its selected suffix, inner lines are full, and the last line keeps its prefix.
 *
 * Offsets are returned in original (with-diacritics) coordinates.
 */
fun resolveHighlightRangesForSelection(
    sortedVisibleLines: List<Pair<Long, String>>,
    selectedText: String,
    showDiacritics: Boolean,
): List<LineHighlightRange> {
    if (sortedVisibleLines.isEmpty()) return emptyList()

    val segments = selectedText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (segments.isEmpty()) return emptyList()

    fun displayed(plain: String): String = if (showDiacritics) plain else stripNikudTeamimWithMap(plain).first

    if (segments.size == 1) {
        val seg = segments.first()
        val line = sortedVisibleLines.firstOrNull { displayed(it.second).contains(seg) } ?: return emptyList()
        val range = resolveHighlightRange(line.second, seg, showDiacritics) ?: return emptyList()
        return listOf(LineHighlightRange(line.first, range))
    }

    // Anchor the first segment on the line whose displayed text ends with it (full first line
    // ends with the whole segment too); fall back to containment.
    val firstSeg = segments.first()
    var startIndex = sortedVisibleLines.indexOfFirst { displayed(it.second).trimEnd().endsWith(firstSeg) }
    if (startIndex < 0) startIndex = sortedVisibleLines.indexOfFirst { displayed(it.second).contains(firstSeg) }
    if (startIndex < 0) return emptyList()

    val result = mutableListOf<LineHighlightRange>()
    for (offset in segments.indices) {
        val lineIndex = startIndex + offset
        if (lineIndex > sortedVisibleLines.lastIndex) break
        val (lineId, plain) = sortedVisibleLines[lineIndex]
        val range = resolveHighlightRange(plain, segments[offset], showDiacritics) ?: (0 until plain.length)
        result += LineHighlightRange(lineId, range)
    }
    return result
}
