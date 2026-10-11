package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseUpdateProgressBarStateTest {
    private val progressBarState = DatabaseUpdateProgressBarState()

    @BeforeTest
    fun setup() {
        progressBarState.resetProgress()
    }

    @Test
    fun `initial progress is zero`() {
        assertEquals(0f, progressBarState.progress.value)
    }

    @Test
    fun `setProgress sets value`() {
        progressBarState.setProgress(0.5f)
        assertEquals(0.5f, progressBarState.progress.value)
    }

    @Test
    fun `setProgress coerces value to max 1`() {
        progressBarState.setProgress(1.5f)
        assertEquals(1f, progressBarState.progress.value)
    }

    @Test
    fun `setProgress coerces value to min 0`() {
        progressBarState.setProgress(-0.5f)
        assertEquals(0f, progressBarState.progress.value)
    }

    @Test
    fun `resetProgress sets value to zero`() {
        progressBarState.setProgress(0.7f)
        progressBarState.resetProgress()
        assertEquals(0f, progressBarState.progress.value)
    }

    @Test
    fun `improveBy adds to current value`() {
        progressBarState.setProgress(0.3f)
        progressBarState.improveBy(0.2f)
        assertEquals(0.5f, progressBarState.progress.value, 0.001f)
    }

    @Test
    fun `improveBy coerces to max 1`() {
        progressBarState.setProgress(0.9f)
        progressBarState.improveBy(0.5f)
        assertEquals(1f, progressBarState.progress.value)
    }

    @Test
    fun `setVersionCheckComplete sets to 10 percent`() {
        progressBarState.setVersionCheckComplete()
        assertEquals(0.1f, progressBarState.progress.value)
    }

    @Test
    fun `setOptionsSelected sets to 20 percent`() {
        progressBarState.setOptionsSelected()
        assertEquals(0.2f, progressBarState.progress.value)
    }

    @Test
    fun `setDownloadStarted sets to 30 percent`() {
        progressBarState.setDownloadStarted()
        assertEquals(0.3f, progressBarState.progress.value)
    }

    @Test
    fun `setDownloadProgress maps 0 to 30 percent`() {
        progressBarState.setDownloadProgress(0f)
        assertEquals(0.3f, progressBarState.progress.value)
    }

    @Test
    fun `setDownloadProgress maps 1 to 80 percent`() {
        progressBarState.setDownloadProgress(1f)
        assertEquals(0.8f, progressBarState.progress.value)
    }

    @Test
    fun `setDownloadProgress maps 0_5 to 55 percent`() {
        progressBarState.setDownloadProgress(0.5f)
        assertEquals(0.55f, progressBarState.progress.value, 0.001f)
    }

    @Test
    fun `setUpdateComplete sets to 100 percent`() {
        progressBarState.setUpdateComplete()
        assertEquals(1f, progressBarState.progress.value)
    }
}
