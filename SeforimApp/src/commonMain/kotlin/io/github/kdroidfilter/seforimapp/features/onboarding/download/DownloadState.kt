package io.github.kdroidfilter.seforimapp.features.onboarding.download

/** Categorizes a pre-download gate failure so the UI can show a localized message. */
enum class DownloadErrorKind {
    /** The previous database could not be removed (locked); a restart will retry it. */
    CLEANUP_FAILED,

    /** Not enough free disk space remains for a fresh install. */
    INSUFFICIENT_SPACE,
}

data class DownloadState(
    val inProgress: Boolean,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long,
    val errorMessage: String? = null,
    val errorKind: DownloadErrorKind? = null,
    val completed: Boolean = false,
)
