package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressBarStateTest {
    private val progressBarState = ProgressBarState()

    @Test
    fun `initial progress is zero`() {
        progressBarState.resetProgress()
        assertEquals(0f, progressBarState.progress.value)
    }

    @Test
    fun `setProgress updates progress value`() {
        progressBarState.setProgress(0.5f)
        assertEquals(0.5f, progressBarState.progress.value)
        progressBarState.resetProgress()
    }

    @Test
    fun `resetProgress sets progress to zero`() {
        progressBarState.setProgress(0.75f)
        progressBarState.resetProgress()
        assertEquals(0f, progressBarState.progress.value)
    }

    @Test
    fun `improveBy adds to current progress`() {
        progressBarState.resetProgress()
        progressBarState.improveBy(0.1f)
        assertEquals(0.1f, progressBarState.progress.value)
        progressBarState.improveBy(0.2f)
        assertEquals(0.3f, progressBarState.progress.value, 0.001f)
        progressBarState.resetProgress()
    }

    @Test
    fun `setProgress can set to 1f for complete`() {
        progressBarState.setProgress(1f)
        assertEquals(1f, progressBarState.progress.value)
        progressBarState.resetProgress()
    }

    @Test
    fun `multiple improveBy calls accumulate correctly`() {
        progressBarState.resetProgress()
        repeat(10) {
            progressBarState.improveBy(0.1f)
        }
        assertEquals(1f, progressBarState.progress.value, 0.001f)
        progressBarState.resetProgress()
    }
}
