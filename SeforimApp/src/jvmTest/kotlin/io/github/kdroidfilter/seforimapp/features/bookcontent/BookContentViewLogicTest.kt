package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.computePageScrollTargetIndex
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.isLineFullyVisible
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.shouldPlaceAltHeadingsInsideBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for logic extracted from BookContentView:
 * - Alt heading placement (inside vs outside selection bar)
 * - Line visibility check (for keyboard navigation scroll decisions)
 * - Page scroll target index computation (Page Up/Down)
 */
class BookContentViewLogicTest {
    // ==================== shouldPlaceAltHeadingsInsideBar ====================

    @Test
    fun `alt headings inside bar when consecutive selection with headings`() {
        assertTrue(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = true,
                isPrevSelected = true,
                hasAltHeadings = true,
            ),
        )
    }

    @Test
    fun `alt headings outside bar when single line selected`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = true,
                isPrevSelected = false,
                hasAltHeadings = true,
            ),
        )
    }

    @Test
    fun `alt headings outside bar when line not selected`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = false,
                isPrevSelected = true,
                hasAltHeadings = true,
            ),
        )
    }

    @Test
    fun `no alt headings - never inside bar`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = true,
                isPrevSelected = true,
                hasAltHeadings = false,
            ),
        )
    }

    @Test
    fun `alt headings outside bar when neither selected`() {
        assertFalse(
            shouldPlaceAltHeadingsInsideBar(
                isCurrentSelected = false,
                isPrevSelected = false,
                hasAltHeadings = true,
            ),
        )
    }

    // ==================== isLineFullyVisible ====================

    @Test
    fun `line fully visible within viewport`() {
        assertTrue(isLineFullyVisible(itemOffset = 0, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line fully visible at viewport edge`() {
        assertTrue(isLineFullyVisible(itemOffset = 400, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line partially visible - extends beyond viewport`() {
        assertFalse(isLineFullyVisible(itemOffset = 450, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line partially visible - starts before viewport`() {
        assertFalse(isLineFullyVisible(itemOffset = -10, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line not found - null offset`() {
        assertFalse(isLineFullyVisible(itemOffset = null, itemSize = 100, viewportEnd = 500))
    }

    @Test
    fun `line not found - null size`() {
        assertFalse(isLineFullyVisible(itemOffset = 0, itemSize = null, viewportEnd = 500))
    }

    @Test
    fun `line not found - both null`() {
        assertFalse(isLineFullyVisible(itemOffset = null, itemSize = null, viewportEnd = 500))
    }

    // ==================== computePageScrollTargetIndex ====================

    @Test
    fun `page down - scrolls to last fully visible item`() {
        // Items 0..9 visible, item 8 is last fully visible (ends at offset 900 <= viewport 1000)
        val indices = (0..9).toList()
        val endOffsets = indices.associateWith { (it + 1) * 100 } // item 9 ends at 1000
        // But item 9 ends exactly at 1000, so it IS fully visible too
        val target =
            computePageScrollTargetIndex(
                forward = true,
                visibleItemIndices = indices,
                visibleItemEndOffsets = endOffsets,
                viewportEnd = 1000,
                firstVisibleItemIndex = 0,
            )
        assertEquals(9, target) // item 9 ends at 1000 == viewportEnd, so it's included
    }

    @Test
    fun `page down - last item partially visible`() {
        val indices = (0..9).toList()
        // Item 9 extends beyond viewport
        val endOffsets = indices.associateWith { (it + 1) * 100 + if (it == 9) 50 else 0 }
        val target =
            computePageScrollTargetIndex(
                forward = true,
                visibleItemIndices = indices,
                visibleItemEndOffsets = endOffsets,
                viewportEnd = 1000,
                firstVisibleItemIndex = 0,
            )
        assertEquals(8, target) // item 8 ends at 900 <= 1000
    }

    @Test
    fun `page down - falls back to last visible when none fully visible`() {
        // Single item that extends beyond viewport
        val target =
            computePageScrollTargetIndex(
                forward = true,
                visibleItemIndices = listOf(5),
                visibleItemEndOffsets = mapOf(5 to 2000),
                viewportEnd = 1000,
                firstVisibleItemIndex = 5,
            )
        assertEquals(5, target)
    }

    @Test
    fun `page up - scrolls backward by visible count`() {
        // 10 items visible, first is index 20
        val indices = (20..29).toList()
        val endOffsets = indices.associateWith { it * 100 }
        val target =
            computePageScrollTargetIndex(
                forward = false,
                visibleItemIndices = indices,
                visibleItemEndOffsets = endOffsets,
                viewportEnd = 3000,
                firstVisibleItemIndex = 20,
            )
        // 20 - 10 + 1 = 11
        assertEquals(11, target)
    }

    @Test
    fun `page up - clamps to zero at beginning`() {
        val indices = (0..4).toList()
        val endOffsets = indices.associateWith { (it + 1) * 200 }
        val target =
            computePageScrollTargetIndex(
                forward = false,
                visibleItemIndices = indices,
                visibleItemEndOffsets = endOffsets,
                viewportEnd = 1000,
                firstVisibleItemIndex = 0,
            )
        assertEquals(0, target) // max(0 - 5 + 1, 0) = 0
    }

    @Test
    fun `page up - near beginning clamps correctly`() {
        val indices = (2..6).toList()
        val endOffsets = indices.associateWith { (it + 1) * 200 }
        val target =
            computePageScrollTargetIndex(
                forward = false,
                visibleItemIndices = indices,
                visibleItemEndOffsets = endOffsets,
                viewportEnd = 1400,
                firstVisibleItemIndex = 2,
            )
        // 2 - 5 + 1 = -2 => clamped to 0
        assertEquals(0, target)
    }

    @Test
    fun `returns null when no visible items`() {
        assertNull(
            computePageScrollTargetIndex(
                forward = true,
                visibleItemIndices = emptyList(),
                visibleItemEndOffsets = emptyMap(),
                viewportEnd = 1000,
                firstVisibleItemIndex = 0,
            ),
        )
    }

    @Test
    fun `page down with single fully visible item`() {
        val target =
            computePageScrollTargetIndex(
                forward = true,
                visibleItemIndices = listOf(5),
                visibleItemEndOffsets = mapOf(5 to 500),
                viewportEnd = 1000,
                firstVisibleItemIndex = 5,
            )
        assertEquals(5, target)
    }

    @Test
    fun `page up with single visible item`() {
        val target =
            computePageScrollTargetIndex(
                forward = false,
                visibleItemIndices = listOf(10),
                visibleItemEndOffsets = mapOf(10 to 500),
                viewportEnd = 1000,
                firstVisibleItemIndex = 10,
            )
        // 10 - 1 + 1 = 10
        assertEquals(10, target)
    }
}
