package io.github.kdroidfilter.seforimapp.core.presentation.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

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
        current = if (next.first <= current.last + 1) {
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
