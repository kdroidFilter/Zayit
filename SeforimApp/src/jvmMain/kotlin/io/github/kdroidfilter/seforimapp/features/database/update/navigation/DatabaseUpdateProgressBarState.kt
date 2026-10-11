package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import io.github.kdroidfilter.seforimapp.core.presentation.components.ProgressBarController

class DatabaseUpdateProgressBarState : ProgressBarController() {
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

    private companion object {
        // Progress steps for database update
        const val VERSION_CHECK_STEP = 0.1f // 10% - Version check completed
        const val OPTIONS_SELECTED_STEP = 0.2f // 20% - Update option selected
        const val DOWNLOAD_START_STEP = 0.3f // 30% - Download/file selection started
        const val DOWNLOAD_COMPLETE_STEP = 0.8f // 80% - Download/extraction completed
        const val UPDATE_COMPLETE_STEP = 1.0f // 100% - Update finished
    }
}
