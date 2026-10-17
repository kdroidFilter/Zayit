package io.github.kdroidfilter.seforimapp.features.settings.data

import androidx.compose.runtime.Immutable

@Immutable
data class DataSettingsState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    // Holds the backup file name on a successful export; null otherwise.
    val exportedFileName: String? = null,
    val importSucceeded: Boolean = false,
    val exportFailed: Boolean = false,
    val importFailed: Boolean = false,
    val resetDone: Boolean = false,
)
