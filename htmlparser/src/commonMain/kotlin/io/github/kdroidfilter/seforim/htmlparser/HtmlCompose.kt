package io.github.kdroidfilter.seforim.htmlparser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Unified HTML -> AnnotatedString rendering used by BookContentView, LineCommentsView, and LineTargumView.
 * It relies on HtmlParser to produce ParsedHtmlElement and then applies consistent styling rules.
 */
fun buildAnnotatedFromHtml(
    html: String,
    baseTextSize: Float
): AnnotatedString {
    val parsedElements = HtmlParser().parse(html)

    // Optimization: we only add styles if necessary
    val headerSizes = floatArrayOf(
        baseTextSize * 1.5f,    // h1
        baseTextSize * 1.25f,   // h2
        baseTextSize * 1.125f,  // h3
        baseTextSize,           // h4
        baseTextSize,           // h5
        baseTextSize            // h6
    )
    val defaultSize = baseTextSize.sp

    return buildAnnotatedString {
        parsedElements.forEach { e ->
            if (e.isLineBreak) {
                append("\n")
                return@forEach
            }
            if (e.text.isBlank()) return@forEach

            val start = length
            append(e.text)
            val end = length

            // Optimization: we only add styles if necessary
            if (e.isBold) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
            if (e.isItalic) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }

            // Optimized font size calculation
            val fontSize = when {
                e.headerLevel != null && e.headerLevel in 1..6 -> {
                    headerSizes[e.headerLevel - 1].sp
                }
                else -> defaultSize
            }
            addStyle(SpanStyle(fontSize = fontSize), start, end)
        }
    }
}
