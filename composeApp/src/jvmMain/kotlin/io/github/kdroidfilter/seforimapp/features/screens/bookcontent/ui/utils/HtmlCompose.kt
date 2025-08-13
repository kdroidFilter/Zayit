package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.core.utils.HtmlParser

/**
 * Unified HTML -> AnnotatedString rendering used by BookContentView, LineCommentsView, and LineTargumView.
 * It relies on HtmlParser to produce ParsedHtmlElement and then applies consistent styling rules.
 */
fun buildAnnotatedFromHtml(
    html: String,
    baseTextSize: Float
): AnnotatedString {
    val parsedElements = HtmlParser().parse(html)
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

            if (e.isBold) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
            if (e.isItalic) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }
            if (e.isHeader || e.headerLevel != null) {
                val size = when (e.headerLevel) {
                    1 -> (baseTextSize * 1.5f).sp
                    2 -> (baseTextSize * 1.25f).sp
                    3 -> (baseTextSize * 1.125f).sp
                    4 -> baseTextSize.sp
                    else -> baseTextSize.sp
                }
                addStyle(SpanStyle(fontSize = size), start, end)
            } else {
                addStyle(SpanStyle(fontSize = baseTextSize.sp), start, end)
            }
        }
    }
}
