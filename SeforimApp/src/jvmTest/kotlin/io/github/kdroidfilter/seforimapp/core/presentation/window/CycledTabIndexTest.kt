package io.github.kdroidfilter.seforimapp.core.presentation.window

import kotlin.test.Test
import kotlin.test.assertEquals

class CycledTabIndexTest {
    // The group reads right to left from its end: index 0 is the leftmost tab.

    @Test
    fun `Ctrl+Tab moves left, wrapping to the rightmost tab`() {
        assertEquals(1, cycledTabIndex(current = 2, count = 4, backward = false))
        assertEquals(3, cycledTabIndex(current = 0, count = 4, backward = false))
    }

    @Test
    fun `Ctrl+Shift+Tab moves right, wrapping to the leftmost tab`() {
        assertEquals(3, cycledTabIndex(current = 2, count = 4, backward = true))
        assertEquals(0, cycledTabIndex(current = 3, count = 4, backward = true))
    }

    @Test
    fun `a single tab stays selected`() {
        assertEquals(0, cycledTabIndex(current = 0, count = 1, backward = false))
        assertEquals(0, cycledTabIndex(current = 0, count = 1, backward = true))
    }
}
