package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AvailableDiskSpaceStateTest {
    @Test
    fun `default state has correct values`() {
        val state = AvailableDiskSpaceState()

        assertFalse(state.hasEnoughSpace)
        assertEquals(0L, state.availableDiskSpace)
        assertEquals(0L, state.remainingDiskSpaceAfterInstall)
        assertEquals(0L, state.totalDiskSpace)
    }

    @Test
    fun `state can be created with custom values`() {
        val state =
            AvailableDiskSpaceState(
                hasEnoughSpace = true,
                availableDiskSpace = 100_000_000_000L,
                remainingDiskSpaceAfterInstall = 88_000_000_000L,
                totalDiskSpace = 500_000_000_000L,
            )

        assertEquals(true, state.hasEnoughSpace)
        assertEquals(100_000_000_000L, state.availableDiskSpace)
        assertEquals(88_000_000_000L, state.remainingDiskSpaceAfterInstall)
        assertEquals(500_000_000_000L, state.totalDiskSpace)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            AvailableDiskSpaceState(
                hasEnoughSpace = true,
                availableDiskSpace = 100L,
                remainingDiskSpaceAfterInstall = 50L,
                totalDiskSpace = 200L,
            )
        val modified = original.copy(hasEnoughSpace = false)

        assertFalse(modified.hasEnoughSpace)
        assertEquals(100L, modified.availableDiskSpace)
        assertEquals(50L, modified.remainingDiskSpaceAfterInstall)
        assertEquals(200L, modified.totalDiskSpace)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = AvailableDiskSpaceState(hasEnoughSpace = true, availableDiskSpace = 100L)
        val state2 = AvailableDiskSpaceState(hasEnoughSpace = true, availableDiskSpace = 100L)
        val state3 = AvailableDiskSpaceState(hasEnoughSpace = false, availableDiskSpace = 100L)

        assertEquals(state1, state2)
        assertFalse(state1 == state3)
    }
}
