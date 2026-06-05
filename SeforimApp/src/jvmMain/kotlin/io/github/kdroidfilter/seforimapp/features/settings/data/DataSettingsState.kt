package io.github.kdroidfilter.seforimapp.features.settings.data

import androidx.compose.runtime.Immutable

@Immutable
data class DataSettingsState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val lastExportPath: String? = null,
    val lastImportPath: String? = null,
    val exportError: String? = null,
    val importError: String? = null,
    val successMessage: String? = null,
    val resetDone: Boolean = false,
)
