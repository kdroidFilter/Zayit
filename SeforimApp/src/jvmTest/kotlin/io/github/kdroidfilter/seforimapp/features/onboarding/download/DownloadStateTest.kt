package io.github.kdroidfilter.seforimapp.features.onboarding.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadStateTest {
    @Test
    fun `state can be created with required values`() {
        val state =
            DownloadState(
                inProgress = true,
                progress = 0.5f,
                downloadedBytes = 50_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 1_000_000L,
            )

        assertTrue(state.inProgress)
        assertEquals(0.5f, state.progress)
        assertEquals(50_000_000L, state.downloadedBytes)
        assertEquals(100_000_000L, state.totalBytes)
        assertEquals(1_000_000L, state.speedBytesPerSec)
        assertNull(state.errorMessage)
        assertFalse(state.completed)
    }

    @Test
    fun `state can have error message`() {
        val state =
            DownloadState(
                inProgress = false,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = null,
                speedBytesPerSec = 0L,
                errorMessage = "Network error",
            )

        assertEquals("Network error", state.errorMessage)
        assertFalse(state.inProgress)
    }

    @Test
    fun `state can be completed`() {
        val state =
            DownloadState(
                inProgress = false,
                progress = 1f,
                downloadedBytes = 100_000_000L,
                totalBytes = 100_000_000L,
                speedBytesPerSec = 0L,
                completed = true,
            )

        assertTrue(state.completed)
        assertEquals(1f, state.progress)
    }

    @Test
    fun `totalBytes can be null for unknown size`() {
        val state =
            DownloadState(
                inProgress = true,
                progress = 0f,
                downloadedBytes = 1000L,
                totalBytes = null,
                speedBytesPerSec = 500L,
            )

        assertNull(state.totalBytes)
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            DownloadState(
                inProgress = true,
                progress = 0.3f,
                downloadedBytes = 30L,
                totalBytes = 100L,
                speedBytesPerSec = 10L,
            )
        val modified = original.copy(progress = 0.5f)

        assertEquals(0.5f, modified.progress)
        assertTrue(modified.inProgress)
        assertEquals(30L, modified.downloadedBytes)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = DownloadState(true, 0.5f, 50L, 100L, 10L)
        val state2 = DownloadState(true, 0.5f, 50L, 100L, 10L)

        assertEquals(state1, state2)
    }
}
