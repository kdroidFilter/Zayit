package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressBarStateTest {
    @Test
    fun `initial progress is zero`() {
        ProgressBarState.resetProgress()
        assertEquals(0f, ProgressBarState.progress.value)
    }

    @Test
    fun `setProgress updates progress value`() {
        ProgressBarState.setProgress(0.5f)
        assertEquals(0.5f, ProgressBarState.progress.value)
        ProgressBarState.resetProgress()
    }

    @Test
    fun `resetProgress sets progress to zero`() {
        ProgressBarState.setProgress(0.75f)
        ProgressBarState.resetProgress()
        assertEquals(0f, ProgressBarState.progress.value)
    }

    @Test
    fun `improveBy adds to current progress`() {
        ProgressBarState.resetProgress()
        ProgressBarState.improveBy(0.1f)
        assertEquals(0.1f, ProgressBarState.progress.value)
        ProgressBarState.improveBy(0.2f)
        assertEquals(0.3f, ProgressBarState.progress.value, 0.001f)
        ProgressBarState.resetProgress()
    }

    @Test
    fun `setProgress can set to 1f for complete`() {
        ProgressBarState.setProgress(1f)
        assertEquals(1f, ProgressBarState.progress.value)
        ProgressBarState.resetProgress()
    }

    @Test
    fun `multiple improveBy calls accumulate correctly`() {
        ProgressBarState.resetProgress()
        repeat(10) {
            ProgressBarState.improveBy(0.1f)
        }
        assertEquals(1f, ProgressBarState.progress.value, 0.001f)
        ProgressBarState.resetProgress()
    }
}
