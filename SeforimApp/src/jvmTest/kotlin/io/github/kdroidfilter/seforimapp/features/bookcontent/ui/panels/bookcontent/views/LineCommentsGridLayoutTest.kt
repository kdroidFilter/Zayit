package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-logic coverage for the commentaries grid sizing and page rebalancing helpers.
 * These tests exercise the behaviour guaranteed by [computeCommentariesGridCapacity]
 * and [computeCommentariesPageLayout] without requiring Compose.
 */
class LineCommentsGridLayoutTest {
    // ==================== Grid capacity: font scaling ====================

    @Test
    fun `reference font on reference pane yields expected 2x2`() {
        val c = computeCommentariesGridCapacity(paneWidthDp = 800f, paneHeightDp = 600f, commentTextSize = 14f)
        // 800 / 320 = 2.5 → 2 cols ; 600 / 150 = 4 → capped to MAX_ROWS_PER_PAGE = 2
        assertEquals(2, c.cols)
        assertEquals(2, c.rows)
        assertEquals(4, c.perPage)
    }

    @Test
    fun `upward font deviation is capped at reference size (no extra columns)`() {
        // With a 28pt font (2x), the pure proportional scaling would push minWidth up
        // and reduce cols. The spec caps the scale at 1, so capacity must match the
        // reference case.
        val ref = computeCommentariesGridCapacity(paneWidthDp = 1200f, paneHeightDp = 600f, commentTextSize = 14f)
        val big = computeCommentariesGridCapacity(paneWidthDp = 1200f, paneHeightDp = 600f, commentTextSize = 28f)
        assertEquals(ref, big)
    }

    @Test
    fun `downward font deviation widens the grid`() {
        // 1200dp pane at reference: 1200 / 320 = 3.75 → 3 cols.
        val ref = computeCommentariesGridCapacity(paneWidthDp = 1200f, paneHeightDp = 600f, commentTextSize = 14f)
        // Smaller font amplifies the downward deviation and should allow 4+ cols.
        val small = computeCommentariesGridCapacity(paneWidthDp = 1200f, paneHeightDp = 600f, commentTextSize = 12.25f)
        assertTrue(small.cols > ref.cols, "expected more cols at smaller font, got ref=${ref.cols} small=${small.cols}")
    }

    @Test
    fun `downward scale is floored so cells never become absurdly narrow`() {
        // Extreme small font: floor kicks in so width per cell does not shrink past ~55% of reference.
        val c = computeCommentariesGridCapacity(paneWidthDp = 1200f, paneHeightDp = 600f, commentTextSize = 1f)
        // At MIN_TEXT_SCALE = 0.55 → minWidth = 176dp → 1200 / 176 = 6.8 → 6 cols.
        assertTrue(c.cols <= 7, "expected the floor to keep cols reasonable, got ${c.cols}")
    }

    // ==================== Grid capacity: row/col guarantees ====================

    @Test
    fun `narrow pane collapses to 1x1`() {
        // Width < NARROW_PANE_WIDTH (420) AND height < MIN_CELL_HEIGHT*2 (300) ⇒
        // the ≥2-cells guarantee is waived and the grid collapses to a single cell.
        val c = computeCommentariesGridCapacity(paneWidthDp = 300f, paneHeightDp = 200f, commentTextSize = 14f)
        assertEquals(1, c.cols)
        assertEquals(1, c.rows)
        assertEquals(1, c.perPage)
    }

    @Test
    fun `landscape pane forces a second column when capacity is under 2`() {
        // 420dp width is exactly on the NARROW_PANE_WIDTH threshold. Single col would
        // violate the "≥2 cells per page" guarantee, so cols must be bumped to 2.
        val c = computeCommentariesGridCapacity(paneWidthDp = 500f, paneHeightDp = 140f, commentTextSize = 14f)
        assertEquals(2, c.cols)
        assertEquals(1, c.rows)
        assertEquals(2, c.perPage)
    }

    @Test
    fun `tall single-column pane keeps one col and fills two rows`() {
        // Pane too narrow for a second col, but tall enough for two rows naturally.
        val c = computeCommentariesGridCapacity(paneWidthDp = 430f, paneHeightDp = 900f, commentTextSize = 14f)
        assertEquals(1, c.cols)
        assertEquals(2, c.rows)
        assertEquals(2, c.perPage)
    }

    @Test
    fun `rows are capped by MAX_ROWS_PER_PAGE regardless of pane height`() {
        val c = computeCommentariesGridCapacity(paneWidthDp = 800f, paneHeightDp = 10_000f, commentTextSize = 14f)
        assertEquals(2, c.rows)
    }

    // ==================== Page layout: partial-page rebalancing ====================

    @Test
    fun `full page keeps requested col count`() {
        val l = computeCommentariesPageLayout(itemCount = 4, cols = 2)
        assertEquals(2, l.rows)
        assertEquals(2, l.colsPerRow)
    }

    @Test
    fun `four items in a five-col grid stay on a single row to fill the width`() {
        // With 5 cols available, 4 items pack into a single row; the Compose-side
        // weight(1f) will then stretch each cell to consume the extra space.
        val l = computeCommentariesPageLayout(itemCount = 4, cols = 5)
        assertEquals(1, l.rows)
        assertEquals(4, l.colsPerRow)
    }

    @Test
    fun `three items in a 2-col grid fill both rows evenly`() {
        // rowsNeeded = ceil(3/2) = 2 ; colsPerRow = ceil(3/2) = 2 → last row has 1 item.
        val l = computeCommentariesPageLayout(itemCount = 3, cols = 2)
        assertEquals(2, l.rows)
        assertEquals(2, l.colsPerRow)
    }

    @Test
    fun `five items in a 3-col grid spread over two rows`() {
        // rowsNeeded = ceil(5/3) = 2 ; colsPerRow = ceil(5/2) = 3.
        val l = computeCommentariesPageLayout(itemCount = 5, cols = 3)
        assertEquals(2, l.rows)
        assertEquals(3, l.colsPerRow)
    }

    @Test
    fun `single item produces a single cell`() {
        val l = computeCommentariesPageLayout(itemCount = 1, cols = 4)
        assertEquals(1, l.rows)
        assertEquals(1, l.colsPerRow)
    }

    @Test
    fun `zero items returns empty layout`() {
        val l = computeCommentariesPageLayout(itemCount = 0, cols = 4)
        assertEquals(0, l.rows)
        assertEquals(0, l.colsPerRow)
    }

    @Test
    fun `zero cols returns empty layout`() {
        val l = computeCommentariesPageLayout(itemCount = 5, cols = 0)
        assertEquals(0, l.rows)
        assertEquals(0, l.colsPerRow)
    }
}
