package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import kotlinx.coroutines.test.runTest
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
    fun `available disk space returns positive value`() =
        runTest {
            val info = AvailableDiskSpaceUseCase().getDiskSpaceInfo()
            assertTrue(info.availableBytes > 0, "Available disk space should be positive")
        }

    @Test
    fun `total disk space returns positive value`() =
        runTest {
            val info = AvailableDiskSpaceUseCase().getDiskSpaceInfo()
            assertTrue(info.totalBytes > 0, "Total disk space should be positive")
        }

    @Test
    fun `total disk space is greater than or equal to available space`() =
        runTest {
            val info = AvailableDiskSpaceUseCase().getDiskSpaceInfo()
            assertTrue(
                info.totalBytes >= info.availableBytes,
                "Total space (${info.totalBytes}) should be >= available space (${info.availableBytes})",
            )
        }

    @Test
    fun `remainingAfterInstall is consistent with available space`() =
        runTest {
            val info = AvailableDiskSpaceUseCase().getDiskSpaceInfo()
            val expectedRemaining = info.availableBytes - AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES
            assertEquals(expectedRemaining, info.remainingAfterInstall)
        }

    @Test
    fun `hasEnoughSpace is consistent with available space`() =
        runTest {
            val info = AvailableDiskSpaceUseCase().getDiskSpaceInfo()
            val expectedHasEnough = info.availableBytes >= AvailableDiskSpaceUseCase.REQUIRED_SPACE_BYTES
            assertEquals(expectedHasEnough, info.hasEnoughSpace)
        }
}
