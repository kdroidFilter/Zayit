package io.github.kdroidfilter.seforim.htmlparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlParserTest {

    @Test
    fun parsesSimpleText() {
        val html = "<div>שלום עולם</div>"
        val result = HtmlParser().parse(html)
        assertEquals(1, result.size)
        assertEquals("שלום עולם", result.first().text)
    }

    @Test
    fun parsesFootnoteMarker() {
        val html = """פי'<sup class="footnote-marker">34</sup> ועכשיו"""

        val result = HtmlParser().parse(html)

        assertEquals(3, result.size)

        assertEquals("פי'", result[0].text)
        assertFalse(result[0].isFootnoteMarker)

        assertEquals("34", result[1].text)
        assertTrue(result[1].isFootnoteMarker)
        assertFalse(result[1].isFootnoteContent)

        // Note: space is preserved from the HTML
        assertEquals(" ועכשיו", result[2].text)
        assertFalse(result[2].isFootnoteMarker)
    }

    @Test
    fun parsesFootnoteContent() {
        val html = """<i class="footnote">כיון שאמרו בגמ'</i>"""

        val result = HtmlParser().parse(html)

        assertEquals(1, result.size)
        assertEquals("כיון שאמרו בגמ'", result[0].text)
        assertTrue(result[0].isFootnoteContent)
        assertFalse(result[0].isFootnoteMarker)
        assertFalse(result[0].isItalic) // Should not be marked as regular italic
    }

    @Test
    fun parsesCompleteFootnoteStructure() {
        val html = """פי'<sup class="footnote-marker">34</sup><i class="footnote">כיון שאמרו</i> ועכשיו נהגו"""

        val result = HtmlParser().parse(html)

        assertEquals(4, result.size)

        // Main text before marker
        assertEquals("פי'", result[0].text)
        assertFalse(result[0].isFootnoteMarker)
        assertFalse(result[0].isFootnoteContent)

        // Footnote marker
        assertEquals("34", result[1].text)
        assertTrue(result[1].isFootnoteMarker)

        // Footnote content
        assertEquals("כיון שאמרו", result[2].text)
        assertTrue(result[2].isFootnoteContent)

        // Main text after footnote (with leading space from HTML)
        assertEquals(" ועכשיו נהגו", result[3].text)
        assertFalse(result[3].isFootnoteMarker)
        assertFalse(result[3].isFootnoteContent)
    }

    @Test
    fun regularItalicIsNotFootnote() {
        val html = """<i>טקסט רגיל באיטליק</i>"""

        val result = HtmlParser().parse(html)

        assertEquals(1, result.size)
        assertTrue(result[0].isItalic)
        assertFalse(result[0].isFootnoteContent)
    }

    @Test
    fun parsesRealFootnoteFromRaahOnBerakhot() {
        // Real example from רא"ה על ברכות
        val html = """פי'<sup class="footnote-marker">34</sup><i class="footnote">כיון שאמרו בגמ' אע"פ שקרא אדם ק"ש בבהכ"נ וכו'</i> ועכשיו נהגו לקרות"""

        val result = HtmlParser().parse(html)

        // Should have: text, marker, footnote content, text
        assertTrue(result.size >= 4)

        // Check that we have both marker and content detected
        val hasMarker = result.any { it.isFootnoteMarker }
        val hasContent = result.any { it.isFootnoteContent }
        assertTrue(hasMarker, "Should detect footnote marker")
        assertTrue(hasContent, "Should detect footnote content")

        // Check marker is "34"
        val marker = result.find { it.isFootnoteMarker }
        assertEquals("34", marker?.text)

        // Check content starts with expected text
        val content = result.find { it.isFootnoteContent }
        assertTrue(content?.text?.startsWith("כיון שאמרו") == true)
    }
}
