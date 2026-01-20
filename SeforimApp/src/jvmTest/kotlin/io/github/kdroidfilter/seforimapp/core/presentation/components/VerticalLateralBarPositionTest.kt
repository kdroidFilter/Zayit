package io.github.kdroidfilter.seforimapp.core.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerticalLateralBarPositionTest {
    @Test
    fun `VerticalLateralBarPosition has two values`() {
        val values = VerticalLateralBarPosition.entries
        assertEquals(2, values.size)
    }

    @Test
    fun `Start position exists`() {
        val start = VerticalLateralBarPosition.Start
        assertEquals("Start", start.name)
    }

    @Test
    fun `End position exists`() {
        val end = VerticalLateralBarPosition.End
        assertEquals("End", end.name)
    }

    @Test
    fun `entries are in expected order`() {
        val entries = VerticalLateralBarPosition.entries
        assertEquals(VerticalLateralBarPosition.Start, entries[0])
        assertEquals(VerticalLateralBarPosition.End, entries[1])
    }

    @Test
    fun `valueOf works for valid names`() {
        assertEquals(VerticalLateralBarPosition.Start, VerticalLateralBarPosition.valueOf("Start"))
        assertEquals(VerticalLateralBarPosition.End, VerticalLateralBarPosition.valueOf("End"))
    }

    @Test
    fun `ordinal values are correct`() {
        assertEquals(0, VerticalLateralBarPosition.Start.ordinal)
        assertEquals(1, VerticalLateralBarPosition.End.ordinal)
    }

    @Test
    fun `positions are comparable`() {
        assertTrue(VerticalLateralBarPosition.Start < VerticalLateralBarPosition.End)
    }
}
