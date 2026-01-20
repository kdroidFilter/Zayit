package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ProgressBarState {
    private var _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    fun setProgress(progress: Float) {
        _progress.value = progress
    }

    fun resetProgress() {
        _progress.value = 0f
    }

    fun improveBy(value: Float) {
        _progress.value += value
    }
}
