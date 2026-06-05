package io.github.kdroidfilter.seforimapp.features.settings.data

sealed interface DataSettingsEvents {
    data object StartExport : DataSettingsEvents

    data class ExportCompleted(
        val path: String,
    ) : DataSettingsEvents

    data class ExportFailed(
        val error: String,
    ) : DataSettingsEvents

    data object StartImport : DataSettingsEvents

    data class ImportCompleted(
        val message: String,
    ) : DataSettingsEvents

    data class ImportFailed(
        val error: String,
    ) : DataSettingsEvents

    data object ClearMessages : DataSettingsEvents
}
