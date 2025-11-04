package io.github.kdroidfilter.seforimapp.core.presentation.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * Returns a copy of [annotated] with background highlight applied to all occurrences of [query].
 * Highlighting is case-insensitive and only activates when [query] has length >= 2.
 */
fun highlightAnnotated(
    annotated: AnnotatedString,
    query: String?,
    highlightColor: Color = Color(0x66FFC107)
): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return annotated
    val lower = annotated.text.lowercase()
    val qLower = q.lowercase()
    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    var idx = lower.indexOf(qLower)
    while (idx >= 0) {
        builder.addStyle(SpanStyle(background = highlightColor), idx, idx + qLower.length)
        idx = lower.indexOf(qLower, idx + qLower.length)
    }
    return builder.toAnnotatedString()
}

/**
 * Like [highlightAnnotated], but allows emphasizing a specific current match range
 * with a different color.
 */
fun highlightAnnotatedWithCurrent(
    annotated: AnnotatedString,
    query: String?,
    currentStart: Int? = null,
    currentLength: Int? = null,
    baseColor: Color,
    currentColor: Color,
): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.length < 2) return annotated
    val lower = annotated.text.lowercase()
    val qLower = q.lowercase()
    val builder = AnnotatedString.Builder()
    builder.append(annotated)
    var idx = lower.indexOf(qLower)
    while (idx >= 0) {
        val end = idx + qLower.length
        val color = if (currentStart != null && currentLength != null && idx == currentStart && qLower.length == currentLength) currentColor else baseColor
        builder.addStyle(SpanStyle(background = color), idx, end)
        idx = lower.indexOf(qLower, end)
    }
    return builder.toAnnotatedString()
}
