package io.github.kdroidfilter.seforimapp.core.presentation.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.kdroidfilter.seforimapp.core.UserHighlight

/**
 * Returns a copy of [annotated] with background highlight applied to all
 * diacritic-insensitive occurrences of [query] (Hebrew-aware). Activates when
 * [query] length >= 2. Works for Latin too via lowercase matching.
 */
fun highlightAnnotated(
    annotated: AnnotatedString,
    query: String?,
    highlightColor: Color = Color(0x66FFC107),
): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return annotated

    val ranges = findAllMatchesOriginal(annotated.text, q)
    if (ranges.isEmpty()) return annotated

    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    for (r in ranges) {
        val start = r.first.coerceIn(0, annotated.length)
        val end = r.last + 1
        if (end > start) builder.addStyle(SpanStyle(background = highlightColor), start, end.coerceAtMost(annotated.length))
    }
    return builder.toAnnotatedString()
}

/**
 * Like [highlightAnnotated], but allows emphasizing a specific current match
 * range (by its start offset in original text) with a different color.
 */
fun highlightAnnotatedWithCurrent(
    annotated: AnnotatedString,
    query: String?,
    currentStart: Int? = null,
    currentLength: Int? = null, // kept for API compatibility; not required here
    baseColor: Color,
    currentColor: Color,
): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return annotated

    val ranges = findAllMatchesOriginal(annotated.text, q)
    if (ranges.isEmpty()) return annotated

    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    for (r in ranges) {
        val start = r.first.coerceIn(0, annotated.length)
        val end = (r.last + 1).coerceAtMost(annotated.length)
        val color = if (currentStart != null && start == currentStart) currentColor else baseColor
        if (end > start) builder.addStyle(SpanStyle(background = color), start, end)
    }
    return builder.toAnnotatedString()
}

/**
 * Like [highlightAnnotatedWithCurrent], but highlights multiple terms.
 * Used for smart mode highlighting with dictionary expansion.
 */
fun highlightAnnotatedWithTerms(
    annotated: AnnotatedString,
    terms: List<String>,
    currentStart: Int? = null,
    baseColor: Color,
    currentColor: Color,
): AnnotatedString {
    if (terms.isEmpty()) return annotated

    // Collect all ranges from all terms
    val allRanges = mutableListOf<IntRange>()
    for (term in terms) {
        if (term.length >= 2) {
            allRanges.addAll(findAllMatchesOriginal(annotated.text, term))
        }
    }
    if (allRanges.isEmpty()) return annotated

    // Merge overlapping ranges to avoid double highlighting
    val sorted = allRanges.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    var current = sorted.first()
    for (i in 1 until sorted.size) {
        val next = sorted[i]
        current =
            if (next.first <= current.last + 1) {
                current.first..maxOf(current.last, next.last)
            } else {
                merged.add(current)
                next
            }
    }
    merged.add(current)

    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    for (r in merged) {
        val start = r.first.coerceIn(0, annotated.length)
        val end = (r.last + 1).coerceAtMost(annotated.length)
        val color = if (currentStart != null && start == currentStart) currentColor else baseColor
        if (end > start) builder.addStyle(SpanStyle(background = color), start, end)
    }
    return builder.toAnnotatedString()
}

/**
 * Applies user highlights to an annotated string for a specific line.
 * Only highlights that belong to this line are applied.
 * Each highlight uses its stored offsets for precise positioning.
 *
 * Highlights are stored with offsets relative to the original text (with diacritics).
 * When displaying without diacritics, the offsets are mapped to the stripped text positions.
 *
 * @param annotated The annotated string to apply highlights to
 * @param highlights List of highlights for this specific line
 * @param originalText The original text with diacritics (for offset mapping)
 * @param showDiacritics Whether diacritics are currently shown
 * @param highlightAlpha The alpha value for highlight colors (default 0.4)
 */
fun applyUserHighlights(
    annotated: AnnotatedString,
    highlights: List<UserHighlight>,
    originalText: String? = null,
    showDiacritics: Boolean = true,
    highlightAlpha: Float = 0.4f,
): AnnotatedString {
    if (highlights.isEmpty()) return annotated

    val builder = AnnotatedString.Builder()
    builder.append(annotated)

    // Create mapping from original to stripped if diacritics are hidden
    val originalToStrippedMap =
        if (!showDiacritics && originalText != null) {
            createOriginalToStrippedMap(originalText)
        } else {
            null
        }

    for (highlight in highlights) {
        // Map offsets if diacritics are hidden
        val (start, end) =
            if (originalToStrippedMap != null) {
                val mappedStart = mapOriginalToStripped(highlight.startOffset, originalToStrippedMap)
                val mappedEnd = mapOriginalToStripped(highlight.endOffset, originalToStrippedMap)
                mappedStart to mappedEnd
            } else {
                highlight.startOffset to highlight.endOffset
            }

        val clampedStart = start.coerceIn(0, annotated.length)
        val clampedEnd = end.coerceAtMost(annotated.length)
        val color = highlight.color.copy(alpha = highlightAlpha)

        if (clampedEnd > clampedStart) {
            builder.addStyle(SpanStyle(background = color), clampedStart, clampedEnd)
        }
    }

    return builder.toAnnotatedString()
}
