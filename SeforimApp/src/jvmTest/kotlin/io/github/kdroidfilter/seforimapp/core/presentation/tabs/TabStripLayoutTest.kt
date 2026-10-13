package io.github.kdroidfilter.seforimapp.core.presentation.tabs

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabStripLayoutTest {
    private fun layout(
        stripWidth: Dp,
        tabCount: Int,
        pinnedCount: Int = 0,
        pinnedWidth: Dp = 0.dp,
        ghostWidth: Dp = 0.dp,
        wasTooNarrow: Boolean = false,
    ) = computeTabStripLayout(
        stripWidth = stripWidth,
        tabCount = tabCount,
        pinnedCount = pinnedCount,
        pinnedWidth = pinnedWidth,
        chipWidth = CHIP,
        ghostWidth = ghostWidth,
        maxTabWidth = MAX_TAB,
        wasTooNarrow = wasTooNarrow,
    )

    @Test
    fun `wide strip shows every tab capped at the max width`() {
        val l = layout(2000.dp, tabCount = 5)
        assertFalse(l.overflowing)
        assertFalse(l.currentTabAsIcon)
        assertEquals(5, l.visibleCount)
        assertEquals(MAX_TAB, l.tabWidth)
        assertEquals(MaxReservedDragArea, l.reservedDragArea)
        assertTrue(l.showAddButton)
    }

    @Test
    fun `tabs never go below the readable width - the rest overflow`() {
        val l = layout(1000.dp, tabCount = 30)
        assertTrue(l.overflowing)
        assertTrue(l.visibleCount in 1 until 30)
        assertTrue(l.tabWidth >= MinReadableTabWidth)
        assertTrue(l.chipShowsCount)
    }

    @Test
    fun `no readable tab fits - the current one shows as its icon, chevron alone`() {
        val l = layout(150.dp, tabCount = 13)
        assertTrue(l.currentTabAsIcon)
        assertTrue(l.overflowing)
        assertEquals(0, l.visibleCount)
        assertEquals(PinnedTabWidth, l.tabWidth)
        assertFalse(l.chipShowsCount)
    }

    @Test
    fun `the plus gives way only when icon, chevron and plus can't all fit`() {
        assertTrue(layout(150.dp, tabCount = 13).showAddButton)
        assertFalse(layout(110.dp, tabCount = 13).showAddButton)
    }

    @Test
    fun `a lone tab too narrow keeps the plus`() {
        val l = layout(100.dp, tabCount = 1)
        assertTrue(l.currentTabAsIcon)
        assertTrue(l.showAddButton)
    }

    @Test
    fun `the drag area shrinks before the last readable tab goes`() {
        // Room for one readable tab only once the drag area gives up part of its 40dp.
        val l = layout(AddButtonWithDividerWidth + MinReadableTabWidth + 20.dp, tabCount = 1)
        assertFalse(l.currentTabAsIcon)
        assertEquals(20.dp, l.reservedDragArea)
    }

    @Test
    fun `hysteresis keeps overflowing until well past the threshold`() {
        // Each of 2 tabs just over the readable width: fine from scratch, still too narrow when it was.
        val width = AddButtonWithDividerWidth + MaxReservedDragArea + (MinReadableTabWidth + 3.dp) * 2
        assertFalse(layout(width, tabCount = 2).tooNarrow)
        assertTrue(layout(width, tabCount = 2, wasTooNarrow = true).tooNarrow)
    }

    @Test
    fun `a drop ghost never switches the strip into overflow`() {
        val width = AddButtonWithDividerWidth + MaxReservedDragArea + (MinReadableTabWidth + 10.dp) * 4
        assertFalse(layout(width, tabCount = 4, ghostWidth = 150.dp).tooNarrow)
    }

    @Test
    fun `strip min width only counts for several tabs`() {
        assertEquals(0.dp, tabStripMinWidth(1))
        assertEquals(PinnedTabWidth + TabOverflowChevronWidth + MinReservedDragArea, tabStripMinWidth(2))
    }

    @Test
    fun `tab window keeps its place while the current tab is in it`() {
        val window = TabWindow()
        val tabs = (0 until 10).toList()
        assertEquals(listOf(0, 1, 2), window.follow(tabs, 3, selected = 0))
        assertEquals(listOf(0, 1, 2), window.follow(tabs, 3, selected = 2))
    }

    @Test
    fun `tab window shifts just enough to take the current tab in`() {
        val window = TabWindow()
        val tabs = (0 until 10).toList()
        assertEquals(listOf(3, 4, 5), window.follow(tabs, 3, selected = 5))
        assertEquals(listOf(1, 2, 3), window.follow(tabs, 3, selected = 1))
    }

    @Test
    fun `tab window clamps to the list and survives a shrinking list`() {
        val window = TabWindow()
        assertEquals(listOf(7, 8, 9), window.follow((0 until 10).toList(), 3, selected = 9))
        assertEquals(listOf(2, 3, 4), window.follow((0 until 5).toList(), 3, selected = -1))
        assertEquals(listOf(0, 1), window.follow((0 until 2).toList(), 3, selected = 0))
    }

    private companion object {
        val CHIP = 80.dp
        val MAX_TAB = 180.dp
    }
}
