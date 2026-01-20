package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvailableDiskSpaceUseCaseTest {
    @Test
    fun `required space constants are correctly defined`() {
        assertEquals(11L, AvailableDiskSpaceUseCase.REQUIRED_SPACE_GB)
        assertEquals(2.5, AvailableDiskSpaceUseCase.TEMPORARY_SPACE_GB)
        assertEquals(8.5, AvailableDiskSpaceUseCase.FINAL_SPACE_GB)
    }

    @Test
    fun `required space bytes is calculated correctly`() {
        val expectedBytes = 11L * 1024 * 1024 * 1024
        assertEquals(expectedBytes, AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES)
    }

    @Test
    fun `getAvailableDiskSpace returns positive value`() {
        val useCase = AvailableDiskSpaceUseCase()
        val availableSpace = useCase.getAvailableDiskSpace()
        assertTrue(availableSpace > 0, "Available disk space should be positive")
    }

    @Test
    fun `getTotalDiskSpace returns positive value`() {
        val useCase = AvailableDiskSpaceUseCase()
        val totalSpace = useCase.getTotalDiskSpace()
        assertTrue(totalSpace > 0, "Total disk space should be positive")
    }

    @Test
    fun `total disk space is greater than or equal to available space`() {
        val useCase = AvailableDiskSpaceUseCase()
        val totalSpace = useCase.getTotalDiskSpace()
        val availableSpace = useCase.getAvailableDiskSpace()
        assertTrue(
            totalSpace >= availableSpace,
            "Total space ($totalSpace) should be >= available space ($availableSpace)",
        )
    }

    @Test
    fun `getRemainingSpaceAfterInstall returns consistent value`() {
        val useCase = AvailableDiskSpaceUseCase()
        val availableSpace = useCase.getAvailableDiskSpace()
        val remainingSpace = useCase.getRemainingSpaceAfterInstall()
        // Remaining space should be less than available space by approximately REQUIRED_SPACE_BYTES
        // We allow a small tolerance for disk changes between calls
        val expectedApprox = availableSpace - AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES
        val tolerance = 1024 * 1024 * 100 // 100 MB tolerance
        assertTrue(
            kotlin.math.abs(remainingSpace - expectedApprox) <= tolerance,
            "Remaining space ($remainingSpace) should be approximately equal to available ($availableSpace) - required",
        )
    }

    @Test
    fun `hasEnoughSpace is consistent with available space`() {
        val useCase = AvailableDiskSpaceUseCase()
        val availableSpace = useCase.getAvailableDiskSpace()
        val hasEnough = useCase.hasEnoughSpace()
        val expectedHasEnough = availableSpace >= AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES
        assertEquals(expectedHasEnough, hasEnough)
    }
}
