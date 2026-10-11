package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShiftSelectionTest {
    @Test
    fun `a plain press only anchors`() {
        val selection = ShiftSelection()
        assertFalse(selection.press(TextPoint(2, 5), shift = false))
        assertNull(selection.range)
    }

    @Test
    fun `a Shift press without an anchor is left to Compose`() {
        val selection = ShiftSelection()
        assertFalse(selection.press(TextPoint(2, 5), shift = true))
        assertNull(selection.range)
    }

    @Test
    fun `a Shift press extends from the anchor, downward or upward`() {
        val selection = ShiftSelection()
        selection.press(TextPoint(2, 5), shift = false)
        assertTrue(selection.press(TextPoint(400, 3), shift = true))
        assertEquals(TextPoint(2, 5)..TextPoint(400, 3), selection.range)
        // From the same anchor, as in a text editor
        assertTrue(selection.press(TextPoint(1, 7), shift = true))
        assertEquals(TextPoint(1, 7)..TextPoint(2, 5), selection.range)
    }

    @Test
    fun `a plain press clears the selection`() {
        val selection = ShiftSelection()
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
}
