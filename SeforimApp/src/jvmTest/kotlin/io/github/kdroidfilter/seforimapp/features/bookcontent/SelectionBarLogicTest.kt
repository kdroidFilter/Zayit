package io.github.kdroidfilter.seforimapp.features.bookcontent

import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.shouldExtendToNext
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.shouldUseThickBar
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for selection bar visual logic.
 * Locks down the thick/thin bar behavior and bar extension rules.
 */
class SelectionBarLogicTest {
    // ==================== shouldUseThickBar ====================

    @Test
    fun `primary line always uses thick bar`() {
        assertTrue(shouldUseThickBar(lineId = 10L, primarySelectedLineId = 10L, isTocEntrySelection = true))
        assertTrue(shouldUseThickBar(lineId = 10L, primarySelectedLineId = 10L, isTocEntrySelection = false))
    }

    @Test
    fun `Ctrl+click selection uses thick bar for all lines`() {
        // isTocEntrySelection = false means Ctrl+click mode
        assertTrue(shouldUseThickBar(lineId = 20L, primarySelectedLineId = 10L, isTocEntrySelection = false))
        assertTrue(shouldUseThickBar(lineId = 30L, primarySelectedLineId = 10L, isTocEntrySelection = false))
    }

    @Test
    fun `TOC selection uses thin bar for secondary lines`() {
        // Non-primary line in TOC selection mode
        assertFalse(shouldUseThickBar(lineId = 20L, primarySelectedLineId = 10L, isTocEntrySelection = true))
        assertFalse(shouldUseThickBar(lineId = 30L, primarySelectedLineId = 10L, isTocEntrySelection = true))
    }

    @Test
    fun `no primary selected line in non-TOC mode still uses thick bar`() {
        assertTrue(shouldUseThickBar(lineId = 10L, primarySelectedLineId = null, isTocEntrySelection = false))
    }

    @Test
    fun `no primary selected line in TOC mode uses thin bar`() {
        assertFalse(shouldUseThickBar(lineId = 10L, primarySelectedLineId = null, isTocEntrySelection = true))
    }

    // ==================== shouldExtendToNext ====================

    @Test
    fun `extends when current and next are both selected with same bar style`() {
        assertTrue(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L,
                selectedLineIds = setOf(10L, 20L),
                currentUseThickBar = true,
                nextUseThickBar = true,
            ),
        )
    }

    @Test
    fun `extends with thin bar when both lines use thin style`() {
        assertTrue(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L,
                selectedLineIds = setOf(10L, 20L),
                currentUseThickBar = false,
                nextUseThickBar = false,
            ),
        )
    }

    @Test
    fun `does not extend when current line is not selected`() {
        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = false,
                nextLineId = 20L,
                selectedLineIds = setOf(20L),
                currentUseThickBar = true,
                nextUseThickBar = true,
            ),
        )
    }

    @Test
    fun `does not extend when next line is not selected`() {
        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L,
                selectedLineIds = setOf(10L),
                currentUseThickBar = true,
                nextUseThickBar = true,
            ),
        )
    }

    @Test
    fun `does not extend when next line is null`() {
        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = null,
                selectedLineIds = setOf(10L),
                currentUseThickBar = true,
                nextUseThickBar = true,
            ),
        )
    }

    @Test
    fun `does not extend when bar styles differ - thick to thin transition`() {
        // Primary line (thick) followed by secondary TOC line (thin)
        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L,
                selectedLineIds = setOf(10L, 20L),
                currentUseThickBar = true,
                nextUseThickBar = false,
            ),
        )
    }

    @Test
    fun `does not extend when bar styles differ - thin to thick transition`() {
        // Secondary TOC line (thin) followed by primary line (thick)
        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L,
                selectedLineIds = setOf(10L, 20L),
                currentUseThickBar = false,
                nextUseThickBar = true,
            ),
        )
    }

    // ==================== Combined scenarios ====================

    @Test
    fun `TOC selection - primary heading followed by secondary lines`() {
        val primaryId = 10L
        val selectedIds = setOf(10L, 11L, 12L, 13L)

        // Primary heading line (thick bar)
        val headingThick = shouldUseThickBar(10L, primaryId, isTocEntrySelection = true)
        assertTrue(headingThick)

        // Next line is secondary (thin bar)
        val nextThick = shouldUseThickBar(11L, primaryId, isTocEntrySelection = true)
        assertFalse(nextThick)

        // Bar should NOT extend from heading to first secondary (different styles)
        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 11L,
                selectedLineIds = selectedIds,
                currentUseThickBar = headingThick,
                nextUseThickBar = nextThick,
            ),
        )
    }

    @Test
    fun `TOC selection - consecutive secondary lines extend to each other`() {
        val primaryId = 10L
        val selectedIds = setOf(10L, 11L, 12L, 13L)

        val line11Thick = shouldUseThickBar(11L, primaryId, isTocEntrySelection = true)
        val line12Thick = shouldUseThickBar(12L, primaryId, isTocEntrySelection = true)
        assertFalse(line11Thick)
        assertFalse(line12Thick)

        // Bar should extend between consecutive secondary lines
        assertTrue(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 12L,
                selectedLineIds = selectedIds,
                currentUseThickBar = line11Thick,
                nextUseThickBar = line12Thick,
            ),
        )
    }

    @Test
    fun `Ctrl+click - all selected lines use thick bar and extend`() {
        val primaryId = 10L
        val selectedIds = setOf(10L, 20L, 30L)

        // All lines thick in Ctrl+click mode
        assertTrue(shouldUseThickBar(10L, primaryId, isTocEntrySelection = false))
        assertTrue(shouldUseThickBar(20L, primaryId, isTocEntrySelection = false))
        assertTrue(shouldUseThickBar(30L, primaryId, isTocEntrySelection = false))

        // Consecutive selected lines extend
        assertTrue(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L,
                selectedLineIds = selectedIds,
                currentUseThickBar = true,
                nextUseThickBar = true,
            ),
        )
    }

    @Test
    fun `Ctrl+click - non-consecutive selected lines do not extend`() {
        val selectedIds = setOf(10L, 30L) // 20L is not selected

        assertFalse(
            shouldExtendToNext(
                isCurrentSelected = true,
                nextLineId = 20L, // next in list but not selected
                selectedLineIds = selectedIds,
                currentUseThickBar = true,
                nextUseThickBar = true,
            ),
        )
    }
}
