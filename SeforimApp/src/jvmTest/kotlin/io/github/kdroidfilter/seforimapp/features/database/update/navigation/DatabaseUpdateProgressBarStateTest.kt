package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseUpdateProgressBarStateTest {
    @BeforeTest
    fun setup() {
        DatabaseUpdateProgressBarState.resetProgress()
    }

    @Test
    fun `initial progress is zero`() {
        assertEquals(0f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setProgress sets value`() {
        DatabaseUpdateProgressBarState.setProgress(0.5f)
        assertEquals(0.5f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setProgress coerces value to max 1`() {
        DatabaseUpdateProgressBarState.setProgress(1.5f)
        assertEquals(1f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setProgress coerces value to min 0`() {
        DatabaseUpdateProgressBarState.setProgress(-0.5f)
        assertEquals(0f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `resetProgress sets value to zero`() {
        DatabaseUpdateProgressBarState.setProgress(0.7f)
        DatabaseUpdateProgressBarState.resetProgress()
        assertEquals(0f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `improveBy adds to current value`() {
        DatabaseUpdateProgressBarState.setProgress(0.3f)
        DatabaseUpdateProgressBarState.improveBy(0.2f)
        assertEquals(0.5f, DatabaseUpdateProgressBarState.progress.value, 0.001f)
    }

    @Test
    fun `improveBy coerces to max 1`() {
        DatabaseUpdateProgressBarState.setProgress(0.9f)
        DatabaseUpdateProgressBarState.improveBy(0.5f)
        assertEquals(1f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setVersionCheckComplete sets to 10 percent`() {
        DatabaseUpdateProgressBarState.setVersionCheckComplete()
        assertEquals(0.1f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setOptionsSelected sets to 20 percent`() {
        DatabaseUpdateProgressBarState.setOptionsSelected()
        assertEquals(0.2f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setDownloadStarted sets to 30 percent`() {
        DatabaseUpdateProgressBarState.setDownloadStarted()
        assertEquals(0.3f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setDownloadProgress maps 0 to 30 percent`() {
        DatabaseUpdateProgressBarState.setDownloadProgress(0f)
        assertEquals(0.3f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setDownloadProgress maps 1 to 80 percent`() {
        DatabaseUpdateProgressBarState.setDownloadProgress(1f)
        assertEquals(0.8f, DatabaseUpdateProgressBarState.progress.value)
    }

    @Test
    fun `setDownloadProgress maps 0_5 to 55 percent`() {
        DatabaseUpdateProgressBarState.setDownloadProgress(0.5f)
        assertEquals(0.55f, DatabaseUpdateProgressBarState.progress.value, 0.001f)
    }

    @Test
    fun `setUpdateComplete sets to 100 percent`() {
        DatabaseUpdateProgressBarState.setUpdateComplete()
        assertEquals(1f, DatabaseUpdateProgressBarState.progress.value)
    }
}
