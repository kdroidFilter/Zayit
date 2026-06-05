package io.github.kdroidfilter.seforimapp.core.presentation.text

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import io.github.kdroidfilter.seforimapp.core.annotations.UserNote
import kotlin.math.max
import kotlin.math.min

/**
 * Maps user [notes] (offsets stored against the original, with-diacritics text) to character
 * ranges in the displayed text. When diacritics are hidden, [originalText] is used to remap
 * offsets onto the stripped text. Returned ranges are `start until end`, clamped to [displayLength].
 */
fun noteDisplayRanges(
    notes: List<UserNote>,
    originalText: String?,
    showDiacritics: Boolean,
    displayLength: Int,
): List<IntRange> {
    if (notes.isEmpty()) return emptyList()
    val originalToStrippedMap =
        if (!showDiacritics && originalText != null) createOriginalToStrippedMap(originalText) else null
    return notes.mapNotNull { note ->
        val start =
            if (originalToStrippedMap != null) mapOriginalToStripped(note.startOffset, originalToStrippedMap) else note.startOffset
        val end =
            if (originalToStrippedMap != null) mapOriginalToStripped(note.endOffset, originalToStrippedMap) else note.endOffset
        val clampedStart = start.coerceIn(0, displayLength)
        val clampedEnd = end.coerceAtMost(displayLength)
        if (clampedEnd > clampedStart) clampedStart until clampedEnd else null
    }
}

/**
 * Draws a dotted grey underline under each note [ranges] using [layout]. Handles wrapped lines and
 * RTL text (the horizontal bounds are normalized per visual line).
 */
fun DrawScope.drawNoteUnderlines(
    layout: TextLayoutResult,
    ranges: List<IntRange>,
    color: Color,
) {
    if (ranges.isEmpty()) return
    val stroke = density // 1.dp in px
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f * stroke, 3f * stroke), 0f)
    for (range in ranges) {
        val startOffset = range.first
        val endOffset = range.last + 1
        if (endOffset <= startOffset || startOffset >= layout.layoutInput.text.length) continue
        val firstLine = layout.getLineForOffset(startOffset)
        val lastLine = layout.getLineForOffset((endOffset - 1).coerceAtLeast(startOffset))
        for (line in firstLine..lastLine) {
            val segStart = if (line == firstLine) startOffset else layout.getLineStart(line)
            val segEnd = if (line == lastLine) endOffset else layout.getLineEnd(line, visibleEnd = true)
            val x1 = layout.getHorizontalPosition(segStart, usePrimaryDirection = true)
            val x2 = layout.getHorizontalPosition(segEnd, usePrimaryDirection = true)
            val y = layout.getLineBottom(line) - stroke
            drawLine(
                color = color,
                start = Offset(min(x1, x2), y),
                end = Offset(max(x1, x2), y),
                strokeWidth = stroke,
                pathEffect = dash,
            )
        }
    }
}
