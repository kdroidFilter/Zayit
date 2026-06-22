package io.github.kdroidfilter.seforimapp.core.presentation.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive holder for the [0f, 1f] progress shown by the installer/update windows.
 * Subclassed by the per-flow singletons so each window keeps its own progress while
 * sharing the underlying state plumbing.
 */
open class ProgressBarController {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun setProgress(progress: Float) {
        _progress.value = progress.coerceIn(0f, 1f)
    }

    fun resetProgress() {
        _progress.value = 0f
    }

    fun improveBy(value: Float) {
        _progress.value = (_progress.value + value).coerceIn(0f, 1f)
    }
}
