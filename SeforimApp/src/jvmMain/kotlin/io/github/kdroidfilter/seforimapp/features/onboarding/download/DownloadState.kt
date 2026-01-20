package io.github.kdroidfilter.seforimapp.features.onboarding.download

data class DownloadState(
    val inProgress: Boolean,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speedBytesPerSec: Long,
    val errorMessage: String? = null,
    val completed: Boolean = false,
)
