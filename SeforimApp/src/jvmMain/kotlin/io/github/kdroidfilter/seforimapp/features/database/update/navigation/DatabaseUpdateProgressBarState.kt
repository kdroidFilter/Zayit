package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DatabaseUpdateProgressBarState {
    private var _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    // Progress steps for database update
    private const val VERSION_CHECK_STEP = 0.1f // 10% - Version check completed
    private const val OPTIONS_SELECTED_STEP = 0.2f // 20% - Update option selected
    private const val DOWNLOAD_START_STEP = 0.3f // 30% - Download/file selection started
    private const val DOWNLOAD_COMPLETE_STEP = 0.8f // 80% - Download/extraction completed
    private const val UPDATE_COMPLETE_STEP = 1.0f // 100% - Update finished

    fun setProgress(progress: Float) {
        _progress.value = progress.coerceIn(0f, 1f)
    }

    fun resetProgress() {
        _progress.value = 0f
    }

    fun improveBy(value: Float) {
        _progress.value = (_progress.value + value).coerceIn(0f, 1f)
    }

    // Helper methods for specific steps
    fun setVersionCheckComplete() {
        setProgress(VERSION_CHECK_STEP)
    }

    fun setOptionsSelected() {
        setProgress(OPTIONS_SELECTED_STEP)
    }

    fun setDownloadStarted() {
        setProgress(DOWNLOAD_START_STEP)
    }

    fun setDownloadProgress(downloadProgress: Float) {
        // Map download progress to the range 0.3 to 0.8 (30% to 80%)
        val mappedProgress = DOWNLOAD_START_STEP + (downloadProgress * (DOWNLOAD_COMPLETE_STEP - DOWNLOAD_START_STEP))
        setProgress(mappedProgress)
    }

    fun setUpdateComplete() {
        setProgress(UPDATE_COMPLETE_STEP)
    }
}
