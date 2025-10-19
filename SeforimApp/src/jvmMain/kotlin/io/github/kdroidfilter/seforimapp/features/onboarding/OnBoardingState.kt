package io.github.kdroidfilter.seforimapp.features.onboarding

data class OnBoardingState(
    val isDatabaseLoaded: Boolean,
    val downloadingInProgress: Boolean,
    val downloadProgress: Float,
    val errorMessage: String? = null,
    val extractingInProgress: Boolean,
    val extractProgress: Float,
    // Real-time download metrics
    val downloadedBytes: Long = 0L,
    val downloadTotalBytes: Long? = null,
    val downloadSpeedBytesPerSec: Long = 0L,
) {
    companion object {

        // Database is fully available; nothing to download or extract
        val previewLoaded = OnBoardingState(
            isDatabaseLoaded = true,
            downloadingInProgress = false,
            downloadProgress = 1f,
            errorMessage = null,
            extractingInProgress = false,
            extractProgress = 1f
        )

        // A download is currently in progress
        val previewDownloading = OnBoardingState(
            isDatabaseLoaded = false,
            downloadingInProgress = true,
            downloadProgress = 0.42f,
            errorMessage = null,
            extractingInProgress = false,
            extractProgress = 0f,
            downloadedBytes = 850L * 1024L * 1024L, // ~850 MB
            downloadTotalBytes = 2L * 1024L * 1024L * 1024L, // 2 GB
            downloadSpeedBytesPerSec = 12L * 1024L * 1024L // 12 MB/s
        )

        // Extraction is currently in progress after a completed download
        val previewExtracting = OnBoardingState(
            isDatabaseLoaded = false,
            downloadingInProgress = false,
            downloadProgress = 1f,
            errorMessage = null,
            extractingInProgress = true,
            extractProgress = 0.3f
        )

        // An error occurred during either download or extraction
        val previewError = OnBoardingState(
            isDatabaseLoaded = false,
            downloadingInProgress = false,
            downloadProgress = 0f,
            errorMessage = "Network error",
            extractingInProgress = false,
            extractProgress = 0f
        )
    }
}
