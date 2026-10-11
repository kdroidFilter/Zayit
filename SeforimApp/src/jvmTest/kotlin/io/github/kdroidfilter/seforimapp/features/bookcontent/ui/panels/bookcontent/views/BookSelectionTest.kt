package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookSelectionTest {
    @Test
    fun `a plain press only anchors`() {
        val selection = BookSelection()
        assertFalse(selection.press(TextPoint(2, 5), shift = false))
        assertNull(selection.range)
    }

    @Test
    fun `a Shift press without an anchor is left to Compose`() {
        val selection = BookSelection()
        assertFalse(selection.press(TextPoint(2, 5), shift = true))
        assertNull(selection.range)
    }

    @Test
    fun `a Shift press extends from the anchor, downward or upward`() {
        val selection = BookSelection()
        selection.press(TextPoint(2, 5), shift = false)
        assertTrue(selection.press(TextPoint(400, 3), shift = true))
        assertEquals(TextPoint(2, 5)..TextPoint(400, 3), selection.range)
        // From the same anchor, as in a text editor
        assertTrue(selection.press(TextPoint(1, 7), shift = true))
        assertEquals(TextPoint(1, 7)..TextPoint(2, 5), selection.range)
    }

    @Test
    fun `a plain press clears the selection`() {
        val selection = BookSelection()
        selection.press(TextPoint(2, 5), shift = false)
        selection.press(TextPoint(9, 0), shift = true)
        selection.press(TextPoint(4, 1), shift = false)
        assertNull(selection.range)
    }

    @Test
    fun `each line gets its part of the range`() {
        val range = TextPoint(2, 5)..TextPoint(4, 3)
        assertEquals(5 until 10, range.offsetsIn(2, 10))
        assertEquals(0 until 8, range.offsetsIn(3, 8))
        assertEquals(0 until 3, range.offsetsIn(4, 10))
        assertTrue(range.offsetsIn(1, 10).isEmpty())
        assertTrue(range.offsetsIn(5, 10).isEmpty())
        assertEquals(3 until 6, (TextPoint(7, 3)..TextPoint(7, 6)).offsetsIn(7, 20))
        // Offsets past a line's end are cut to it
        assertTrue(range.offsetsIn(2, 4).isEmpty())
        assertEquals(0 until 2, range.offsetsIn(4, 2))
    }

    private val lines = mapOf(2 to "את השמים ואת הארץ", 3 to "והארץ היתה תהו", 4 to "ויאמר אלהים יהי אור")

    @Test
    fun `a short word is the occurrence that holds the press, not the first one`() {
        // "את" twice on the line: pressed in the second one (a double click, or a short drag from it)
        assertEquals(TextPoint(2, 10)..TextPoint(2, 12), locateSelection("את", TextPoint(2, 11), lines))
        assertEquals(TextPoint(2, 0)..TextPoint(2, 2), locateSelection("את", TextPoint(2, 1), lines))
        // A single letter, dragged from its start
        assertEquals(TextPoint(4, 6)..TextPoint(4, 7), locateSelection("א", TextPoint(4, 6), lines))
    }

    @Test
    fun `dragged down from the press`() {
        assertEquals(
            TextPoint(2, 9)..TextPoint(4, 5),
            locateSelection("ואת הארץ\nוהארץ היתה תהו\nויאמר", TextPoint(2, 9), lines),
        )
    }

    @Test
    fun `dragged up from the press`() {
        assertEquals(
            TextPoint(2, 9)..TextPoint(4, 5),
            locateSelection("ואת הארץ\nוהארץ היתה תהו\nויאמר", TextPoint(4, 5), lines),
        )
    }

    @Test
    fun `a selection not started at the press is not located`() {
        assertNull(locateSelection("אור", TextPoint(2, 3), lines))
        assertNull(locateSelection("ואת הארץ\nמשהו אחר\nויאמר", TextPoint(2, 9), lines))
    }

    @Test
    fun `highlights are stored at the same offsets as before`() {
        val line =
            io.github.kdroidfilter.seforimlibrary.core.models.Line(
                id = 7,
                bookId = 1,
                lineIndex = 3,
                content = "<b>בְּרֵאשִׁ֖ית</b> בָּרָ֣א אֱלֹהִ֑ים אֵ֥ת הַשָּׁמַ֖יִם",
            )
        val original =
            io.github.kdroidfilter.seforim.htmlparser
                .buildAnnotatedFromHtml(line.content, baseTextSize = 16f, boldScale = 1f)
                .text
        for (mode in io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode.entries) {
            val displayed = displayedLineText(line.content, mode)
            val word = mode.apply("אֱלֹהִ֑ים")
            val start = displayed.indexOf(word)
            val ours = highlightRangesOf(TextPoint(3, start)..TextPoint(3, start + word.length), listOf(line), mode)
            val before =
                io.github.kdroidfilter.seforimapp.core.annotations
                    .resolveHighlightRange(original, word, mode)
            assertEquals(
                listOf(
                    io.github.kdroidfilter.seforimapp.core.annotations
                        .LineHighlightRange(7, before!!),
                ),
                ours,
                "$mode",
            )
        }
    }

    @Test
    fun `no exact highlight when stripping the plain text does not give the displayed one`() {
        // A point written as an entity: kept when the reading mode strips the HTML, gone from the stripped plain text
        val line =
            io.github.kdroidfilter.seforimlibrary.core.models
                .Line(id = 8, bookId = 1, lineIndex = 4, content = "שלום&#1463; עולם")
        val mode = io.github.kdroidfilter.seforimapp.core.presentation.text.DiacriticsMode.None
        assertNull(highlightRangesOf(TextPoint(4, 0)..TextPoint(4, 4), listOf(line), mode))
    }
}
