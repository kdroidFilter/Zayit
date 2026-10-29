package io.github.kdroidfilter.seforim.htmlparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlParserTest {
    @Test
    fun plainTextMatchesTheAnnotatedText() {
        listOf(
            "<div>שלום עולם</div>",
            "<b>בראשית</b> ברא<br>אלהים",
            """פי'<sup class="footnote-marker">34</sup> ועכשיו<span class="footnote">הערה</span> סוף""",
            "<h2>כותרת</h2><small>הגה</small> <i>נטוי</i>",
        ).forEach { html ->
            assertEquals(buildAnnotatedFromHtml(html, 16f).text, plainTextFromHtml(html), html)
        }
    }

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
        val html =
            """פי'<sup class="footnote-marker">34</sup><i class="footnote">כיון שאמרו בגמ' אע"פ שקרא אדם ק"ש בבהכ"נ וכו'</i> ועכשיו נהגו לקרות"""

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

    @Test
    fun parsesInlineBase64Image() {
        // 1x1 transparent PNG
        val pngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
        val html = """Before<img src="data:image/png;base64,$pngBase64" width="12" height="12">After"""

        val result = HtmlParser().parse(html)

        val image = result.firstOrNull { it.isImage }
        assertTrue(image != null, "Parser should emit an image element for data-URI <img>")
        assertEquals("image/png", image.imageMime)
        assertEquals(12, image.imageWidth)
        assertEquals(12, image.imageHeight)
        assertTrue((image.imageBytes?.size ?: 0) > 0)
        assertEquals(INLINE_IMAGE_PLACEHOLDER, image.text)

        // Surrounding text must not absorb the placeholder.
        assertTrue(result.any { it.text == "Before" })
        assertTrue(result.any { it.text == "After" })
    }

    @Test
    fun ignoresImageWithoutDataUri() {
        val html = """<img src="https://example.com/foo.png">"""
        val result = HtmlParser().parse(html)
        assertTrue(result.none { it.isImage }, "Remote <img> src should be dropped")
    }

    @Test
    fun refLinkKeepsItsTargetAndOnlyTheCitationIsALink() {
        val html = """שנאמר <a class="ref" href="seforim://line/123">(בראשית א, ד)</a> וירא"""
        val parsed = HtmlParser().parse(html)
        assertEquals("seforim://line/123", parsed.single { it.href != null }.href)

        val annotated = buildAnnotatedFromHtml(html, 16f)
        assertEquals("שנאמר (בראשית א, ד) וירא", annotated.text)
        val link = annotated.getLinkAnnotations(0, annotated.length).single()
        assertEquals("seforim://line/123", (link.item as androidx.compose.ui.text.LinkAnnotation.Url).url)
        assertEquals("(בראשית א, ד)", annotated.text.substring(link.start, link.end))
    }

    @Test
    fun refLinkIsColoredAndUnderlinedOnlyUnderThePointer() {
        // An underline would cross the vowels below the letters: only while hovered
        val color = androidx.compose.ui.graphics.Color.Red
        val annotated = buildAnnotatedFromHtml("""<a class="ref" href="seforim://line/1">וְאָכַלְתָּ</a>""", 16f, linkColor = color)
        val styles =
            (
                annotated
                    .getLinkAnnotations(
                        0,
                        annotated.length,
                    ).single()
                    .item as androidx.compose.ui.text.LinkAnnotation.Url
            ).styles!!
        assertEquals(color, styles.style?.color)
        assertEquals(null, styles.style?.textDecoration)
        assertEquals(androidx.compose.ui.text.style.TextDecoration.Underline, styles.hoveredStyle?.textDecoration)
    }

    @Test
    fun otherAnchorsStayPlainText() {
        val annotated = buildAnnotatedFromHtml("""ראה <a href="https://example.com">כאן</a>""", 16f)
        assertTrue(annotated.getLinkAnnotations(0, annotated.length).isEmpty())
    }

    @Test
    fun commentAnchorsMarkThePassagesTheyExplain() {
        val html =
            """ <i data-commentator="Bach" data-order="1.1"></i><i data-commentator="Prisha" data-order="1.1"></i>וכל היום """ +
                """<i data-commentator="Drisha" data-order="2.1"></i>י"א שלקטנים <i data-commentator="Prisha" data-order="3.1"></i>ולגדולים"""

        val annotated = buildAnnotatedFromHtml(html, 16f, anchorCommentators = setOf("Bach", "Prisha", "Drisha"))

        assertEquals("וכל היום י\"א שלקטנים ולגדולים", annotated.text)
        // Not asked for: no passage at all
        assertEquals(0, buildAnnotatedFromHtml(html, 16f).getStringAnnotations(COMMENT_SEGMENT_TAG, 0, annotated.length).size)
        assertEquals(plainTextFromHtml(html), annotated.text)
        val segments =
            annotated.getStringAnnotations(COMMENT_SEGMENT_TAG, 0, annotated.length).map {
                annotated.text.substring(it.start, it.end).trim() to commentAnchorsOf(it.item)
            }
        assertEquals(
            listOf(
                "וכל היום" to listOf(CommentAnchor("Bach", 1), CommentAnchor("Prisha", 1)),
                "י\"א שלקטנים" to listOf(CommentAnchor("Drisha", 2)),
                "ולגדולים" to listOf(CommentAnchor("Prisha", 3)),
            ),
            segments,
        )
    }

    @Test
    fun anAnchorBetweenWordsKeepsTheirSpace() {
        assertEquals("ויכוין בברכותיו", plainTextFromHtml("""ויכוין <i data-commentator="Bach" data-order="1.1"></i> בברכותיו"""))
        assertEquals("ויכוין בברכותיו", plainTextFromHtml("""ויכוין<i data-commentator="Bach" data-order="1.1"></i> בברכותיו"""))
        // Latin words keep the space their merge gives them
        assertEquals("abc def", plainTextFromHtml("""abc<i data-commentator="Bach" data-order="1.1"></i>def"""))
    }
}
