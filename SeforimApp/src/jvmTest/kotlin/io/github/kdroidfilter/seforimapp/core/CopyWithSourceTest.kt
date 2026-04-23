package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Synthetic edge-case coverage for [buildCopyWithSourcePayload], [formatHebrewSourceReference]
 * and [resolveLineRangeFromSelection]. These do not touch the database and run on any host.
 */
class CopyWithSourceTest {
    private fun book(
        title: String = "ספר",
        heRef: String? = "ספר",
    ): Book =
        Book(
            id = 1L,
            categoryId = 1L,
            sourceId = 0L,
            title = title,
            heRef = heRef,
        )

    private fun line(
        id: Long,
        index: Int,
        content: String = "",
        heRef: String? = null,
    ): Line =
        Line(
            id = id,
            bookId = 1L,
            lineIndex = index,
            content = content,
            heRef = heRef,
        )

    // -------------------------------------------------------------------------------------------
    // resolveLineRangeFromSelection
    // -------------------------------------------------------------------------------------------

    @Test
    fun `selection that does not overlap any loaded line returns null`() {
        val lines = listOf(line(1, 0, "<p>שלום עולם</p>"))
        assertNull(resolveLineRangeFromSelection("texte qui n'existe pas dans le contenu", lines))
    }

    @Test
    fun `empty inputs return null`() {
        assertNull(resolveLineRangeFromSelection("", listOf(line(1, 0, "<p>x</p>"))))
        assertNull(resolveLineRangeFromSelection("anything", emptyList()))
    }

    @Test
    fun `single-line selection matches when fully contained in one line`() {
        val lines =
            listOf(
                line(1, 0, "<p>בראשית ברא אלהים את השמים ואת הארץ</p>"),
                line(2, 1, "<p>והארץ היתה תהו ובהו</p>"),
            )
        val range = resolveLineRangeFromSelection("ברא אלהים", lines)
        assertEquals(1L to 1L, range?.first?.id to range?.second?.id)
    }

    @Test
    fun `multi-line selection picks first and last by lineIndex`() {
        val lines =
            listOf(
                line(10, 0, "<p>ראשון</p>"),
                line(20, 1, "<p>שני</p>"),
                line(30, 2, "<p>שלישי</p>"),
            )
        val range = resolveLineRangeFromSelection("ראשון שני שלישי", lines)
        assertEquals(10L to 30L, range?.first?.id to range?.second?.id)
    }

    @Test
    fun `diacritics-stripped selection still matches diacritized line content`() {
        // Line content has nikud, selection (as the user copies it after toggling off diacritics)
        // does not. Normalization on both sides should still yield a match.
        val lines = listOf(line(1, 0, "<p>בְּרֵאשִׁית בָּרָא אֱלֹהִים</p>", heRef = "בראשית א, א"))
        val range = resolveLineRangeFromSelection("בראשית ברא אלהים", lines)
        assertEquals(1L, range?.first?.id)
    }

    // -------------------------------------------------------------------------------------------
    // formatHebrewSourceReference — fallbacks
    // -------------------------------------------------------------------------------------------

    @Test
    fun `null heRef on both lines falls back to book ref`() {
        val b = book(title = "ספר", heRef = "ספר")
        val l = line(1, 0, heRef = null)
        assertEquals("ספר", formatHebrewSourceReference(b, rootTitle = null, firstLine = l, lastLine = l))
    }

    @Test
    fun `book with null heRef uses title as fallback`() {
        val b = book(title = "כותרת", heRef = null)
        val l = line(1, 0, heRef = "כותרת ב")
        assertEquals("כותרת ב", formatHebrewSourceReference(b, rootTitle = null, firstLine = l, lastLine = l))
    }

    @Test
    fun `embedded prefix is preserved verbatim`() {
        val b = book(heRef = "בראשית")
        val l = line(1, 0, heRef = "בראשית א, א")
        assertEquals("בראשית א, א", formatHebrewSourceReference(b, rootTitle = "תנ\"ך", firstLine = l, lastLine = l))
    }

    @Test
    fun `non-embedded heRef is prefixed with book ref`() {
        val b = book(heRef = "סוכה")
        val l = line(1, 0, heRef = "ב., ב")
        assertEquals("סוכה ב.", formatHebrewSourceReference(b, rootTitle = "תלמוד", firstLine = l, lastLine = l))
    }

    // -------------------------------------------------------------------------------------------
    // formatHebrewSourceReference — Talmud rule
    // -------------------------------------------------------------------------------------------

    @Test
    fun `talmud trim removes only the last segment`() {
        val b = book(heRef = "תלמוד ירושלמי דמאי")
        val l = line(1, 0, heRef = "תלמוד ירושלמי דמאי א, א, א")
        assertEquals(
            "תלמוד ירושלמי דמאי א, א",
            formatHebrewSourceReference(b, rootTitle = "תלמוד", firstLine = l, lastLine = l),
        )
    }

    @Test
    fun `talmud trim is a no-op when heRef has no comma`() {
        val b = book(heRef = "סוכה")
        val l = line(1, 0, heRef = "סוכה ב.")
        assertEquals("סוכה ב.", formatHebrewSourceReference(b, rootTitle = "תלמוד", firstLine = l, lastLine = l))
    }

    @Test
    fun `non-talmud root keeps every segment`() {
        val b = book(heRef = "שולחן ערוך אורח חיים")
        val l = line(1, 0, heRef = "שולחן ערוך אורח חיים סימן לא, סעיף א")
        assertEquals(
            "שולחן ערוך אורח חיים סימן לא, סעיף א",
            formatHebrewSourceReference(b, rootTitle = "הלכה", firstLine = l, lastLine = l),
        )
    }

    @Test
    fun `unknown root title behaves like non-talmud`() {
        val b = book(heRef = "ספר")
        val l = line(1, 0, heRef = "ספר א, א")
        assertEquals("ספר א, א", formatHebrewSourceReference(b, rootTitle = null, firstLine = l, lastLine = l))
        assertEquals("ספר א, א", formatHebrewSourceReference(b, rootTitle = "מדרש", firstLine = l, lastLine = l))
    }

    // -------------------------------------------------------------------------------------------
    // formatHebrewSourceReference — ranges
    // -------------------------------------------------------------------------------------------

    @Test
    fun `range collapses when first and last share the same heRef`() {
        val b = book(heRef = "בראשית")
        val l1 = line(1, 0, heRef = "בראשית א, א")
        val l2 = line(2, 1, heRef = "בראשית א, א")
        assertEquals(
            "בראשית א, א",
            formatHebrewSourceReference(b, rootTitle = "תנ\"ך", firstLine = l1, lastLine = l2),
        )
    }

    @Test
    fun `range with distinct heRefs produces dash-separated output`() {
        val b = book(heRef = "בראשית")
        val l1 = line(1, 0, heRef = "בראשית א, א")
        val l2 = line(2, 1, heRef = "בראשית א, ה")
        assertEquals(
            "בראשית א, א – בראשית א, ה",
            formatHebrewSourceReference(b, rootTitle = "תנ\"ך", firstLine = l1, lastLine = l2),
        )
    }

    @Test
    fun `talmud range trims both ends`() {
        val b = book(heRef = "סוכה")
        val l1 = line(1, 0, heRef = "סוכה ב., ב")
        val l2 = line(2, 1, heRef = "סוכה ב:, ה")
        assertEquals(
            "סוכה ב. – סוכה ב:",
            formatHebrewSourceReference(b, rootTitle = "תלמוד", firstLine = l1, lastLine = l2),
        )
    }

    @Test
    fun `range with one null heRef falls back to the non-null end`() {
        val b = book(heRef = "בראשית")
        val l1 = line(1, 0, heRef = null)
        val l2 = line(2, 1, heRef = "בראשית א, ה")
        assertEquals(
            "בראשית א, ה",
            formatHebrewSourceReference(b, rootTitle = "תנ\"ך", firstLine = l1, lastLine = l2),
        )
    }

    // -------------------------------------------------------------------------------------------
    // buildCopyWithSourcePayload — full pipeline
    // -------------------------------------------------------------------------------------------

    @Test
    fun `payload appends reference in trailing parens after blank line`() {
        val b = book(heRef = "בראשית")
        val l = line(1, 0, content = "<p>בראשית ברא אלהים</p>", heRef = "בראשית א, א")
        val payload =
            buildCopyWithSourcePayload(
                selectedText = "בראשית ברא אלהים",
                book = b,
                rootTitle = "תנ\"ך",
                loadedLines = listOf(l),
            )
        assertEquals("בראשית ברא אלהים\n\n(בראשית א, א)", payload)
    }

    @Test
    fun `payload falls back to book ref when selection cannot be matched`() {
        val b = book(heRef = "בראשית")
        val l = line(1, 0, content = "<p>שלום</p>", heRef = "בראשית א, א")
        val payload =
            buildCopyWithSourcePayload(
                selectedText = "texte introuvable",
                book = b,
                rootTitle = "תנ\"ך",
                loadedLines = listOf(l),
            )
        assertEquals("texte introuvable\n\n(בראשית)", payload)
    }

    @Test
    fun `payload falls back to book title when both heRefs are absent`() {
        val b = book(title = "כותרת", heRef = null)
        val l = line(1, 0, content = "<p>contenu</p>", heRef = null)
        val payload =
            buildCopyWithSourcePayload(
                selectedText = "contenu",
                book = b,
                rootTitle = null,
                loadedLines = listOf(l),
            )
        assertEquals("contenu\n\n(כותרת)", payload)
    }

    @Test
    fun `payload uses talmud trim end-to-end`() {
        val b = book(heRef = "סוכה")
        val l = line(1, 0, content = "<p>תנן</p>", heRef = "סוכה ב., ב")
        val payload =
            buildCopyWithSourcePayload(
                selectedText = "תנן",
                book = b,
                rootTitle = "תלמוד",
                loadedLines = listOf(l),
            )
        assertEquals("תנן\n\n(סוכה ב.)", payload)
    }
}
