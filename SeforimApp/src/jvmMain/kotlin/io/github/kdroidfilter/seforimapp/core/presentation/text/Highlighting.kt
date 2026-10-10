package io.github.kdroidfilter.seforimapp.core.presentation.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import io.github.kdroidfilter.seforimapp.core.annotations.UserHighlight

/** Background of in-text search matches. */
val SearchHighlightColor = Color(0x66FFC107)

/** Background of the current find-in-page match, set apart from the others as in Chrome. */
val CurrentFindMatchColor = Color(0xCCFF9632)

/**
 * Returns a copy of [annotated] with background highlight applied to all
 * diacritic-insensitive occurrences of [query] (Hebrew-aware). Activates when
 * [query] length >= 2. Works for Latin too via lowercase matching.
 */
fun highlightAnnotated(
    annotated: AnnotatedString,
    query: String?,
    highlightColor: Color = SearchHighlightColor,
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

/** A copy of this text with a [color] background over each of [ranges]. */
fun AnnotatedString.withBackground(
    ranges: List<IntRange>,
    color: Color,
): AnnotatedString {
    if (ranges.isEmpty()) return this
    val builder = AnnotatedString.Builder()
    builder.append(this)
    for (r in ranges) {
        val start = r.first.coerceIn(0, length)
        val end = (r.last + 1).coerceAtMost(length)
        if (end > start) builder.addStyle(SpanStyle(background = color), start, end)
    }
    return builder.toAnnotatedString()
}

/**
 * Like [highlightAnnotated], but emphasizes the [currentIndex]-th match (from 0) with a different
 * color. A rank, unlike an offset, holds whatever diacritics are hidden.
 */
fun highlightAnnotatedWithCurrent(
    annotated: AnnotatedString,
    query: String?,
    baseColor: Color,
    currentColor: Color,
    currentIndex: Int? = null,
): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return annotated

    val ranges = findAllMatchesOriginal(annotated.text, q)
    if (ranges.isEmpty()) return annotated

    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    ranges.forEachIndexed { i, r ->
        val start = r.first.coerceIn(0, annotated.length)
        val end = (r.last + 1).coerceAtMost(annotated.length)
        val color = if (i == currentIndex) currentColor else baseColor
        if (end > start) builder.addStyle(SpanStyle(background = color), start, end)
    }
    return builder.toAnnotatedString()
}

/**
 * Applies persisted user [highlights] (belonging to a single line) to [annotated].
 *
 * Highlight offsets are stored relative to the original text (with diacritics). When
 * some diacritics are hidden, [originalText] is used to remap offsets onto the stripped
 * text that is actually displayed.
 */
fun applyUserHighlights(
    annotated: AnnotatedString,
    highlights: List<UserHighlight>,
    originalText: String? = null,
    diacritics: DiacriticsMode = DiacriticsMode.All,
    highlightAlpha: Float = 0.4f,
): AnnotatedString {
    if (highlights.isEmpty()) return annotated

    val originalToStrippedMap =
        if (diacritics != DiacriticsMode.All && originalText != null) createOriginalToStrippedMap(originalText, diacritics) else null

    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    for (highlight in highlights) {
        val (start, end) =
            if (originalToStrippedMap != null) {
                mapOriginalToStripped(highlight.startOffset, originalToStrippedMap) to
                    mapOriginalToStripped(highlight.endOffset, originalToStrippedMap)
            } else {
                highlight.startOffset to highlight.endOffset
            }
        val clampedStart = start.coerceIn(0, annotated.length)
        val clampedEnd = end.coerceAtMost(annotated.length)
        if (clampedEnd > clampedStart) {
            builder.addStyle(
                SpanStyle(background = highlight.color.copy(alpha = highlightAlpha)),
                clampedStart,
                clampedEnd,
            )
        }
    }
    return builder.toAnnotatedString()
}
