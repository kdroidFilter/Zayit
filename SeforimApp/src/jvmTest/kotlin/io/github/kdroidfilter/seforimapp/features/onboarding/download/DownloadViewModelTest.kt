package io.github.kdroidfilter.seforimapp.features.onboarding.download

import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadPhase
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadViewModelTest {
    // DownloadState tests
    @Test
    fun `default DownloadState has IDLE phase`() {
        val state = DownloadState()
        assertEquals(DownloadPhase.IDLE, state.phase)
    }

    @Test
    fun `default DownloadState has zero progress`() {
        val state = DownloadState()
        assertEquals(0f, state.progressFloat)
    }

    @Test
    fun `default DownloadState has zero bytes`() {
        val state = DownloadState()
        assertEquals(0L, state.downloadedBytes)
        assertEquals(0L, state.totalBytes)
    }

    @Test
    fun `default DownloadState has no error`() {
        val state = DownloadState()
        assertNull(state.errorMessage)
    }

    @Test
    fun `default DownloadState can retry`() {
        val state = DownloadState()
        assertTrue(state.canRetry)
    }

    @Test
    fun `default DownloadState has empty url`() {
        val state = DownloadState()
        assertEquals("", state.activeUrl)
    }

    @Test
    fun `DownloadState with downloading phase`() {
        val state = DownloadState(
            phase = DownloadPhase.DOWNLOADING,
            progressFloat = 0.5f,
            downloadedBytes = 500L,
            totalBytes = 1000L,
        )
        assertEquals(DownloadPhase.DOWNLOADING, state.phase)
        assertEquals(0.5f, state.progressFloat)
    }

    @Test
    fun `DownloadState with error`() {
        val state = DownloadState(
            phase = DownloadPhase.ERROR,
            errorMessage = "Network error",
            canRetry = true,
        )
        assertEquals(DownloadPhase.ERROR, state.phase)
        assertEquals("Network error", state.errorMessage)
        assertTrue(state.canRetry)
    }

    @Test
    fun `DownloadState with completed phase`() {
        val state = DownloadState(phase = DownloadPhase.COMPLETED)
        assertEquals(DownloadPhase.COMPLETED, state.phase)
    }

    @Test
    fun `DownloadState copy changes specified values`() {
        val original = DownloadState()
        val copied = original.copy(
            phase = DownloadPhase.DOWNLOADING,
            progressFloat = 0.75f,
        )
        assertEquals(DownloadPhase.DOWNLOADING, copied.phase)
        assertEquals(0.75f, copied.progressFloat)
    }

    @Test
    fun `DownloadState equality`() {
        val state1 = DownloadState(phase = DownloadPhase.DOWNLOADING, progressFloat = 0.5f)
        val state2 = DownloadState(phase = DownloadPhase.DOWNLOADING, progressFloat = 0.5f)
        assertEquals(state1, state2)
    }

    @Test
    fun `DownloadState inequality`() {
        val state1 = DownloadState(phase = DownloadPhase.DOWNLOADING)
        val state2 = DownloadState(phase = DownloadPhase.COMPLETED)
        assertNotEquals(state1, state2)
    }

    // DownloadPhase tests
    @Test
    fun `DownloadPhase has all expected values`() {
        val phases = DownloadPhase.entries
        assertTrue(phases.contains(DownloadPhase.IDLE))
        assertTrue(phases.contains(DownloadPhase.DOWNLOADING))
        assertTrue(phases.contains(DownloadPhase.COMPLETED))
        assertTrue(phases.contains(DownloadPhase.ERROR))
    }

    @Test
    fun `DownloadPhase IDLE name is correct`() {
        assertEquals("IDLE", DownloadPhase.IDLE.name)
    }

    @Test
    fun `DownloadPhase DOWNLOADING name is correct`() {
        assertEquals("DOWNLOADING", DownloadPhase.DOWNLOADING.name)
    }

    @Test
    fun `DownloadPhase COMPLETED name is correct`() {
        assertEquals("COMPLETED", DownloadPhase.COMPLETED.name)
    }

    @Test
    fun `DownloadPhase ERROR name is correct`() {
        assertEquals("ERROR", DownloadPhase.ERROR.name)
    }

    // DownloadEvents tests
    @Test
    fun `StartDownload event exists`() {
        val event = DownloadEvents.StartDownload
        assertEquals(DownloadEvents.StartDownload, event)
    }

    @Test
    fun `CancelDownload event exists`() {
        val event = DownloadEvents.CancelDownload
        assertEquals(DownloadEvents.CancelDownload, event)
    }

    @Test
    fun `RetryDownload event exists`() {
        val event = DownloadEvents.RetryDownload
        assertEquals(DownloadEvents.RetryDownload, event)
    }

    @Test
    fun `events are sealed class subtypes`() {
        val start: DownloadEvents = DownloadEvents.StartDownload
        val cancel: DownloadEvents = DownloadEvents.CancelDownload
        val retry: DownloadEvents = DownloadEvents.RetryDownload

        assertTrue(start is DownloadEvents)
        assertTrue(cancel is DownloadEvents)
        assertTrue(retry is DownloadEvents)
    }

    @Test
    fun `when expression covers all event types`() {
        fun describe(event: DownloadEvents): String = when (event) {
            DownloadEvents.StartDownload -> "start"
            DownloadEvents.CancelDownload -> "cancel"
            DownloadEvents.RetryDownload -> "retry"
        }

        assertEquals("start", describe(DownloadEvents.StartDownload))
        assertEquals("cancel", describe(DownloadEvents.CancelDownload))
        assertEquals("retry", describe(DownloadEvents.RetryDownload))
    }
}
