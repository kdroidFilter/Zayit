package io.github.kdroidfilter.seforimapp.integration

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.kdroidfilter.seforim.htmlparser.HtmlParser
import io.github.kdroidfilter.seforim.htmlparser.buildAnnotatedFromHtml
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotated
import io.github.kdroidfilter.seforimapp.core.presentation.text.highlightAnnotatedWithCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the search highlighting system.
 * Tests HtmlParser, buildAnnotatedFromHtml, and highlight functions.
 */
class SearchHighlightingIntegrationTest {
    private val parser = HtmlParser()

    // ==================== HtmlParser Bold Tag Recognition Tests ====================

    @Test
    fun `HtmlParser recognizes b tag as bold`() {
        val html = "<b>bold text</b>"
        val result = parser.parse(html)

        assertEquals(1, result.size)
        assertEquals("bold text", result[0].text)
        assertTrue(result[0].isBold)
    }

    @Test
    fun `HtmlParser recognizes strong tag as bold`() {
        val html = "<strong>strong text</strong>"
        val result = parser.parse(html)

        assertEquals(1, result.size)
        assertEquals("strong text", result[0].text)
        assertTrue(result[0].isBold)
    }

    @Test
    fun `HtmlParser handles mixed bold and normal text`() {
        val html = "normal <b>bold</b> normal"
        val result = parser.parse(html)

        assertTrue(result.size >= 2)
        val normalParts = result.filter { !it.isBold && it.text.contains("normal") }
        val boldParts = result.filter { it.isBold }

        assertTrue(normalParts.isNotEmpty())
        assertTrue(boldParts.isNotEmpty())
        assertEquals("bold", boldParts.firstOrNull()?.text?.trim())
    }

    @Test
    fun `HtmlParser handles nested bold tags`() {
        val html = "<b><strong>nested bold</strong></b>"
        val result = parser.parse(html)

        assertEquals(1, result.size)
        assertEquals("nested bold", result[0].text)
        assertTrue(result[0].isBold)
    }

    @Test
    fun `HtmlParser handles bold with Hebrew text`() {
        val html = "<b>בראשית</b> ברא"
        val result = parser.parse(html)

        val boldPart = result.find { it.isBold }
        assertTrue(boldPart != null)
        assertEquals("בראשית", boldPart!!.text.trim())
    }

    @Test
    fun `HtmlParser handles multiple bold sections`() {
        val html = "<b>first</b> middle <b>second</b>"
        val result = parser.parse(html)

        val boldParts = result.filter { it.isBold }
        assertEquals(2, boldParts.size)
        assertEquals("first", boldParts[0].text.trim())
        assertEquals("second", boldParts[1].text.trim())
    }

    @Test
    fun `HtmlParser handles empty bold tags`() {
        val html = "text <b></b> more text"
        val result = parser.parse(html)

        // Empty bold tags should not produce empty bold elements
        val boldParts = result.filter { it.isBold }
        assertTrue(boldParts.isEmpty() || boldParts.all { it.text.isNotBlank() })
    }

    @Test
    fun `HtmlParser preserves text outside bold tags`() {
        val html = "before <b>bold</b> after"
        val result = parser.parse(html)

        val allText = result.joinToString("") { it.text }
        assertTrue(allText.contains("before"))
        assertTrue(allText.contains("bold"))
        assertTrue(allText.contains("after"))
    }

    @Test
    fun `HtmlParser handles bold with italic`() {
        val html = "<b><i>bold italic</i></b>"
        val result = parser.parse(html)

        assertEquals(1, result.size)
        assertTrue(result[0].isBold)
        assertTrue(result[0].isItalic)
    }

    @Test
    fun `HtmlParser handles self-closing br in bold`() {
        val html = "<b>line1<br/>line2</b>"
        val result = parser.parse(html)

        val boldParts = result.filter { it.isBold && !it.isLineBreak }
        assertTrue(boldParts.size >= 2 || boldParts.any { it.text.contains("line1") && it.text.contains("line2") })
    }

    // ==================== buildAnnotatedFromHtml Bold Styling Tests ====================

    @Test
    fun `buildAnnotatedFromHtml applies bold style to b tag`() {
        val html = "<b>bold</b>"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        assertEquals("bold", annotated.text)

        val boldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        assertTrue(boldSpans.isNotEmpty())
    }

    @Test
    fun `buildAnnotatedFromHtml applies bold style to strong tag`() {
        val html = "<strong>strong</strong>"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        assertEquals("strong", annotated.text)

        val boldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        assertTrue(boldSpans.isNotEmpty())
    }

    @Test
    fun `buildAnnotatedFromHtml preserves text around bold`() {
        val html = "before <b>bold</b> after"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        assertTrue(annotated.text.contains("before"))
        assertTrue(annotated.text.contains("bold"))
        assertTrue(annotated.text.contains("after"))
    }

    @Test
    fun `buildAnnotatedFromHtml applies boldColor when specified`() {
        val html = "<b>colored bold</b>"
        val boldColor = Color.Red
        val annotated =
            buildAnnotatedFromHtml(
                html,
                baseTextSize = 16f,
                boldColor = boldColor,
            )

        val coloredBoldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold && it.item.color == boldColor
            }
        assertTrue(coloredBoldSpans.isNotEmpty())
    }

    @Test
    fun `buildAnnotatedFromHtml applies boldScale when specified`() {
        val html = "<b>scaled bold</b>"
        val annotated =
            buildAnnotatedFromHtml(
                html,
                baseTextSize = 16f,
                boldScale = 1.2f,
            )

        // The scaled size should be applied
        val fontSizeSpans =
            annotated.spanStyles.filter {
                it.item.fontSize.value > 0
            }
        assertTrue(fontSizeSpans.isNotEmpty())
    }

    @Test
    fun `buildAnnotatedFromHtml handles Hebrew bold text`() {
        val html = "<b>בראשית ברא אלהים</b>"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        assertEquals("בראשית ברא אלהים", annotated.text)

        val boldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        assertTrue(boldSpans.isNotEmpty())
    }

    @Test
    fun `buildAnnotatedFromHtml handles multiple bold sections correctly`() {
        val html = "<b>first</b> middle <b>second</b>"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        val boldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        // Should have at least 2 bold spans (one for each b tag)
        assertTrue(boldSpans.size >= 2)
    }

    @Test
    fun `buildAnnotatedFromHtml handles search result snippet with bold`() {
        // Simulate a search result snippet with highlighted terms
        val html = "בתחילה <b>ברא</b> אלהים את השמים ואת <b>הארץ</b>"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        val boldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        assertEquals(2, boldSpans.size)

        // Verify the bold text is in the right positions
        val boldText1 = annotated.text.substring(boldSpans[0].start, boldSpans[0].end)
        val boldText2 = annotated.text.substring(boldSpans[1].start, boldSpans[1].end)
        assertTrue(boldText1 == "ברא" || boldText2 == "ברא")
        assertTrue(boldText1 == "הארץ" || boldText2 == "הארץ")
    }

    // ==================== highlightAnnotated Tests ====================

    @Test
    fun `highlightAnnotated returns original when query is null`() {
        val original = AnnotatedString("test text")
        val result = highlightAnnotated(original, null)

        assertEquals(original.text, result.text)
        assertEquals(original.spanStyles.size, result.spanStyles.size)
    }

    @Test
    fun `highlightAnnotated returns original when query is empty`() {
        val original = AnnotatedString("test text")
        val result = highlightAnnotated(original, "")

        assertEquals(original.text, result.text)
    }

    @Test
    fun `highlightAnnotated returns original when query is too short`() {
        val original = AnnotatedString("test text")
        val result = highlightAnnotated(original, "t")

        assertEquals(original.text, result.text)
        assertEquals(original.spanStyles.size, result.spanStyles.size)
    }

    @Test
    fun `highlightAnnotated highlights matching text`() {
        val original = AnnotatedString("test text here")
        val result = highlightAnnotated(original, "test")

        // Should have a background highlight span
        val highlightSpans =
            result.spanStyles.filter {
                it.item.background != Color.Unspecified
            }
        assertTrue(highlightSpans.isNotEmpty())
    }

    @Test
    fun `highlightAnnotated highlights multiple occurrences`() {
        val original = AnnotatedString("test one test two test three")
        val result = highlightAnnotated(original, "test")

        val highlightSpans =
            result.spanStyles.filter {
                it.item.background != Color.Unspecified
            }
        assertEquals(3, highlightSpans.size)
    }

    @Test
    fun `highlightAnnotated is case insensitive for Latin`() {
        val original = AnnotatedString("Test TEST test")
        val result = highlightAnnotated(original, "test")

        val highlightSpans =
            result.spanStyles.filter {
                it.item.background != Color.Unspecified
            }
        assertEquals(3, highlightSpans.size)
    }

    @Test
    fun `highlightAnnotated handles Hebrew text`() {
        val original = AnnotatedString("בראשית ברא אלהים")
        val result = highlightAnnotated(original, "ברא")

        val highlightSpans =
            result.spanStyles.filter {
                it.item.background != Color.Unspecified
            }
        // Should find "ברא" in both "בראשית" (contains ברא) and "ברא"
        assertTrue(highlightSpans.isNotEmpty())
    }

    @Test
    fun `highlightAnnotated uses custom highlight color`() {
        val original = AnnotatedString("test text")
        val customColor = Color(0xFFFF0000)
        val result = highlightAnnotated(original, "test", highlightColor = customColor)

        val highlightSpans =
            result.spanStyles.filter {
                it.item.background == customColor
            }
        assertTrue(highlightSpans.isNotEmpty())
    }

    @Test
    fun `highlightAnnotated preserves original styles`() {
        val original =
            AnnotatedString
                .Builder()
                .apply {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append("bold test")
                    pop()
                }.toAnnotatedString()

        val result = highlightAnnotated(original, "test")

        // Should still have the bold style
        val boldSpans =
            result.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        assertTrue(boldSpans.isNotEmpty())

        // And also have highlight
        val highlightSpans =
            result.spanStyles.filter {
                it.item.background != Color.Unspecified
            }
        assertTrue(highlightSpans.isNotEmpty())
    }

    // ==================== highlightAnnotatedWithCurrent Tests ====================

    @Test
    fun `highlightAnnotatedWithCurrent differentiates current match`() {
        val original = AnnotatedString("test one test two")
        val baseColor = Color(0x66FFC107)
        val currentColor = Color(0xFFFF5722)

        val result =
            highlightAnnotatedWithCurrent(
                annotated = original,
                query = "test",
                currentStart = 0, // First occurrence
                baseColor = baseColor,
                currentColor = currentColor,
            )

        // Should have both colors in the result
        val baseColorSpans =
            result.spanStyles.filter {
                it.item.background == baseColor
            }
        val currentColorSpans =
            result.spanStyles.filter {
                it.item.background == currentColor
            }

        assertEquals(1, currentColorSpans.size)
        assertEquals(1, baseColorSpans.size)
    }

    @Test
    fun `highlightAnnotatedWithCurrent highlights second occurrence as current`() {
        val original = AnnotatedString("test one test two")
        val baseColor = Color(0x66FFC107)
        val currentColor = Color(0xFFFF5722)

        // Find the second occurrence's start index
        val secondTestStart = original.text.indexOf("test", 5)

        val result =
            highlightAnnotatedWithCurrent(
                annotated = original,
                query = "test",
                currentStart = secondTestStart,
                baseColor = baseColor,
                currentColor = currentColor,
            )

        val currentColorSpans =
            result.spanStyles.filter {
                it.item.background == currentColor
            }
        assertEquals(1, currentColorSpans.size)

        // Verify the current span is at the second occurrence
        val currentSpan = currentColorSpans.first()
        assertEquals(secondTestStart, currentSpan.start)
    }

    @Test
    fun `highlightAnnotatedWithCurrent returns original when query too short`() {
        val original = AnnotatedString("test text")
        val result =
            highlightAnnotatedWithCurrent(
                annotated = original,
                query = "t",
                currentStart = 0,
                baseColor = Color.Yellow,
                currentColor = Color.Red,
            )

        assertEquals(original.text, result.text)
    }

    // ==================== End-to-End HTML to Highlight Tests ====================

    @Test
    fun `full pipeline from HTML bold to additional highlight`() {
        // Step 1: Parse HTML with bold tags (simulating search snippet)
        val html = "בתחילה <b>ברא</b> אלהים"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        // Verify bold is applied
        val boldSpans =
            annotated.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        assertTrue(boldSpans.isNotEmpty())

        // Step 2: Apply find-in-page highlight on top
        val highlighted = highlightAnnotated(annotated, "אלהים")

        // Verify both bold and highlight exist
        val boldSpansAfter =
            highlighted.spanStyles.filter {
                it.item.fontWeight == FontWeight.Bold
            }
        val highlightSpans =
            highlighted.spanStyles.filter {
                it.item.background != Color.Unspecified
            }

        assertTrue(boldSpansAfter.isNotEmpty())
        assertTrue(highlightSpans.isNotEmpty())
    }

    @Test
    fun `highlight on already bold text works correctly`() {
        // HTML with bold text
        val html = "<b>test text</b>"
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        // Highlight within the bold text
        val highlighted = highlightAnnotated(annotated, "test")

        // Both bold and highlight should be present
        val hasBold = highlighted.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        val hasHighlight = highlighted.spanStyles.any { it.item.background != Color.Unspecified }

        assertTrue(hasBold)
        assertTrue(hasHighlight)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `HtmlParser handles malformed HTML gracefully`() {
        val html = "<b>unclosed bold"
        val result = parser.parse(html)

        // Should still parse without crashing
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `HtmlParser handles empty HTML`() {
        val html = ""
        val result = parser.parse(html)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildAnnotatedFromHtml handles empty input`() {
        val html = ""
        val annotated = buildAnnotatedFromHtml(html, baseTextSize = 16f)

        assertEquals("", annotated.text)
    }

    @Test
    fun `highlightAnnotated handles empty text`() {
        val original = AnnotatedString("")
        val result = highlightAnnotated(original, "test")

        assertEquals("", result.text)
    }

    @Test
    fun `highlightAnnotated handles no matches`() {
        val original = AnnotatedString("hello world")
        val result = highlightAnnotated(original, "xyz")

        assertEquals(original.text, result.text)
        val highlightSpans =
            result.spanStyles.filter {
                it.item.background != Color.Unspecified
            }
        assertTrue(highlightSpans.isEmpty())
    }

    @Test
    fun `highlighting preserves text content exactly`() {
        val text = "בראשית ברא אלהים את השמים ואת הארץ"
        val original = AnnotatedString(text)
        val result = highlightAnnotated(original, "ברא")

        assertEquals(text, result.text)
    }
}
