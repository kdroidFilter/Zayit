package io.github.kdroidfilter.seforim.htmlparser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp

/**
 * Unified HTML -> AnnotatedString rendering used by BookContentView, LineCommentsView, and LineTargumView.
 * It relies on HtmlParser to produce ParsedHtmlElement and then applies consistent styling rules.
 *
 * @param showFootnotes If true, footnote content is displayed inline (smaller, gray, italic).
 *                      If false, footnote content is hidden.
 * @param footnoteMarkerColor Color for footnote markers (default: blue)
 * @param footnoteContentColor Color for footnote content (default: gray)
 *
 * TODO: Footnotes handling needs improvement. Current implementation hides markers because they're not clickable.
 *       Options to consider:
 *       1. Make footnote markers clickable to show content in a popup/tooltip
 *       2. Extract footnotes to separate books with links at database generation level
 *       See: https://github.com/kdroidFilter/SeforimApp - footnotes issue
 */
fun buildAnnotatedFromHtml(
    html: String,
    baseTextSize: Float,
    boldScale: Float = 1f,
    boldColor: Color? = null,
    showFootnoteMarkers: Boolean = false,
    showFootnoteContent: Boolean = true,
    footnoteMarkerColor: Color = Color(0xFF1976D2),
    footnoteContentColor: Color = Color.Unspecified
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
    val defaultSize = baseTextSize
    val effectiveBoldScale = if (boldScale < 1f) 1f else boldScale

    return buildAnnotatedString {
        parsedElements.forEach { e ->
            if (e.isLineBreak) {
                append("\n")
                return@forEach
            }
            if (e.text.isBlank()) return@forEach

            // Skip footnote markers if showFootnoteMarkers is false
            if (e.isFootnoteMarker && !showFootnoteMarkers) {
                // Add space to prevent text from collapsing when marker is hidden
                if (length > 0 && !this.toAnnotatedString().text.endsWith(" ")) {
                    append(" ")
                }
                return@forEach
            }

            // Skip footnote content if showFootnoteContent is false
            if (e.isFootnoteContent && !showFootnoteContent) {
                return@forEach
            }

            val start = length
            append(e.text)
            val end = length

            // Handle footnote marker styling (superscript, colored)
            if (e.isFootnoteMarker) {
                addStyle(
                    SpanStyle(
                        color = footnoteMarkerColor,
                        fontSize = (defaultSize * 0.7f).sp,
                        fontWeight = FontWeight.Bold,
                        baselineShift = BaselineShift.Superscript
                    ),
                    start,
                    end
                )
                // Add a thin space after footnote marker for visual separation
                append("\u2009") // Thin space character
                return@forEach
            }

            // Handle footnote content styling (smaller, italic, optionally colored)
            if (e.isFootnoteContent) {
                val footnoteStyle = if (footnoteContentColor != Color.Unspecified) {
                    SpanStyle(
                        color = footnoteContentColor,
                        fontSize = (defaultSize * 0.75f).sp,
                        fontStyle = FontStyle.Italic
                    )
                } else {
                    SpanStyle(
                        fontSize = (defaultSize * 0.75f).sp,
                        fontStyle = FontStyle.Italic
                    )
                }
                addStyle(footnoteStyle, start, end)
                return@forEach
            }

            // Optimization: we only add styles if necessary
            if (e.isBold) {
                val boldStyle = if (boldColor != null) {
                    SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)
                } else {
                    SpanStyle(fontWeight = FontWeight.Bold)
                }
                addStyle(boldStyle, start, end)
            }
            if (e.isItalic) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }

            // Optimized font size calculation
            val baseSize = when {
                e.headerLevel != null && e.headerLevel in 1..6 -> {
                    headerSizes[e.headerLevel - 1]
                }
                e.isSmall -> defaultSize * 0.85f  // Small text (הגה) is 85% of base size
                else -> defaultSize
            }
            val fontSize = if (!e.isHeader && e.isBold) {
                (baseSize * effectiveBoldScale).sp
            } else {
                baseSize.sp
            }
            addStyle(SpanStyle(fontSize = fontSize), start, end)
        }
    }
}
