package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionMotionTest {
    private val lines = mapOf(0 to "בראשית ברא", 1 to "את השמים", 2 to "ואת הארץ")
    private val textOf: (Int) -> String? = { lines[it] }

    // ==================== Direction ====================

    @Test
    fun `left moves toward the end in a right-to-left paragraph, right in a left-to-right one`() {
        assertTrue(towardsEnd(left = true, rtl = true))
        assertFalse(towardsEnd(left = false, rtl = true))
        assertTrue(towardsEnd(left = false, rtl = false))
        assertFalse(towardsEnd(left = true, rtl = false))
    }

    @Test
    fun `a paragraph's direction from its text`() {
        assertTrue(isRtlText("בראשית ברא"))
        assertFalse(isRtlText("In the beginning"))
        // No strong character: the book's direction
        assertTrue(isRtlText("12, 13"))
        assertTrue(isRtlText(""))
    }

    // ==================== Characters ====================

    @Test
    fun `a character forward and back within a line`() {
        assertEquals(TextPoint(0, 4), moveByCharacter(TextPoint(0, 3), forward = true, textOf))
        assertEquals(TextPoint(0, 2), moveByCharacter(TextPoint(0, 3), forward = false, textOf))
    }

    @Test
    fun `a character crosses into the adjacent line at the edges`() {
        assertEquals(TextPoint(1, 0), moveByCharacter(TextPoint(0, 10), forward = true, textOf))
        assertEquals(TextPoint(0, 10), moveByCharacter(TextPoint(1, 0), forward = false, textOf))
    }

    @Test
    fun `a character stops at the book's ends`() {
        assertEquals(TextPoint(2, 8), moveByCharacter(TextPoint(2, 8), forward = true, textOf))
        assertEquals(TextPoint(0, 0), moveByCharacter(TextPoint(0, 0), forward = false, textOf))
    }

    @Test
    fun `a letter moves with its nikud`() {
        // בְּ: bet, sheva, dagesh, one character on screen
        val pointed = mapOf(0 to "בְּרֵאשִׁית")
        assertEquals(TextPoint(0, 3), moveByCharacter(TextPoint(0, 0), forward = true) { pointed[it] })
        assertEquals(TextPoint(0, 0), moveByCharacter(TextPoint(0, 3), forward = false) { pointed[it] })
    }

    @Test
    fun `an offset past the line's end is taken as its end`() {
        assertEquals(TextPoint(0, 9), moveByCharacter(TextPoint(0, 99), forward = false, textOf))
    }

    // ==================== Words ====================

    @Test
    fun `a word forward goes to the end of the word, then of the next`() {
        assertEquals(TextPoint(0, 6), moveByWord(TextPoint(0, 0), forward = true, textOf))
        assertEquals(TextPoint(0, 6), moveByWord(TextPoint(0, 2), forward = true, textOf))
        assertEquals(TextPoint(0, 10), moveByWord(TextPoint(0, 6), forward = true, textOf))
    }

    @Test
    fun `a word back goes to the start of the word, then of the previous`() {
        assertEquals(TextPoint(0, 7), moveByWord(TextPoint(0, 10), forward = false, textOf))
        assertEquals(TextPoint(0, 0), moveByWord(TextPoint(0, 7), forward = false, textOf))
        assertEquals(TextPoint(0, 0), moveByWord(TextPoint(0, 3), forward = false, textOf))
    }

    @Test
    fun `a word crosses lines from a line's edge`() {
        assertEquals(TextPoint(1, 0), moveByWord(TextPoint(0, 10), forward = true, textOf))
        assertEquals(TextPoint(0, 10), moveByWord(TextPoint(1, 0), forward = false, textOf))
    }

    @Test
    fun `punctuation is not a word, an abbreviation is one`() {
        val text = mapOf(0 to "אמר רש״י, וכן")
        assertEquals(listOf(0 until 3, 4 until 8, 10 until 13), wordsOf(text.getValue(0)))
        assertEquals(TextPoint(0, 13), moveByWord(TextPoint(0, 8), forward = true) { text[it] })
    }

    // ==================== Visual lines ====================

    /** A line wrapped every [perLine] characters, each [width] px wide; x grows with the offset (direction aside). */
    private class Wrapped(
        override val length: Int,
        private val perLine: Int,
        private val width: Float = 10f,
    ) : LineGeometry {
        override val lineCount = (length + perLine - 1) / perLine

        override fun lineOf(offset: Int) = (offset / perLine).coerceAtMost(lineCount - 1)

        override fun xOf(offset: Int) = (offset - lineOf(offset) * perLine) * width

        override fun offsetAt(
            visualLine: Int,
            x: Float,
        ): Int {
            val start = visualLine * perLine
            val end = minOf(start + perLine, length)
            return (start + (x / width).toInt()).coerceIn(start, end)
        }
    }

    @Test
    fun `down and up within a wrapped line keep the x`() {
        val geometry = Wrapped(length = 30, perLine = 10)
        val (down, x) = moveByVisualLine(TextPoint(0, 3), down = true, goalX = null, geometryOf = { geometry }, textOf = { "x".repeat(30) })
        assertEquals(TextPoint(0, 13), down)
        assertEquals(30f, x)
        val (up, _) = moveByVisualLine(down, down = false, goalX = x, geometryOf = { geometry }, textOf = { "x".repeat(30) })
        assertEquals(TextPoint(0, 3), up)
    }

    @Test
    fun `the goal x is kept through a short line`() {
        val geometries = mapOf(0 to Wrapped(30, 10), 1 to Wrapped(2, 10), 2 to Wrapped(30, 10))
        val texts = mapOf(0 to "x".repeat(30), 1 to "xx", 2 to "x".repeat(30))
        val (short, x) = moveByVisualLine(TextPoint(0, 27), true, null, { geometries[it] }, { texts[it] })
        assertEquals(TextPoint(1, 2), short)
        val (back, _) = moveByVisualLine(short, true, x, { geometries[it] }, { texts[it] })
        assertEquals(TextPoint(2, 7), back)
    }

    @Test
    fun `from the last visual line down into the next book line's first, from the first up into the previous one's last`() {
        val geometries = mapOf(0 to Wrapped(25, 10), 1 to Wrapped(25, 10))
        val texts = mapOf(0 to "x".repeat(25), 1 to "x".repeat(25))
        assertEquals(TextPoint(1, 4), moveByVisualLine(TextPoint(0, 24), true, null, { geometries[it] }, { texts[it] }).first)
        assertEquals(TextPoint(0, 23), moveByVisualLine(TextPoint(1, 3), false, null, { geometries[it] }, { texts[it] }).first)
    }

    @Test
    fun `into a line not laid out at the same offset`() {
        val geometry = Wrapped(10, 10)
        assertEquals(
            TextPoint(1, 4),
            moveByVisualLine(TextPoint(0, 4), true, null, { if (it == 0) geometry else null }, textOf).first,
        )
        // Without the focus line's layout either
        assertEquals(TextPoint(2, 5), moveByVisualLine(TextPoint(1, 5), true, null, { null }, textOf).first)
        // Cut to the shorter line
        assertEquals(TextPoint(1, 8), moveByVisualLine(TextPoint(0, 10), true, null, { null }, textOf).first)
    }

    @Test
    fun `past the book's ends, to the line's start or end`() {
        assertEquals(TextPoint(2, 8), moveByVisualLine(TextPoint(2, 3), true, null, { null }, textOf).first)
        assertEquals(TextPoint(0, 0), moveByVisualLine(TextPoint(0, 3), false, null, { null }, textOf).first)
    }
}
