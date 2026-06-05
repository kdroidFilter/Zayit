package io.github.kdroidfilter.seforimapp.features.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.database.getUserSettingsDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class DataSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(DataSettingsState())
    val state: StateFlow<DataSettingsState> = _state.asStateFlow()

    fun onEvent(event: DataSettingsEvents) {
        when (event) {
            is DataSettingsEvents.StartExport -> exportData()
            is DataSettingsEvents.StartImport -> {} // Will be called with file path from UI
            is DataSettingsEvents.ClearMessages -> clearMessages()
            else -> {}
        }
    }

    fun importFromFile(importFile: File) {
        if (!importFile.exists()) {
            _state.update { it.copy(importError = "File not found") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(isImporting = true, importError = null) }
                val dbPath = getUserSettingsDatabasePath()
                val dbFile = File(dbPath)

                // Copy imported file to replace current DB
                Files.copy(
                    importFile.toPath(),
                    dbFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )

                _state.update {
                    it.copy(
                        isImporting = false,
                        lastImportPath = importFile.absolutePath,
                        successMessage = "Data imported successfully. Please restart the app.",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isImporting = false, importError = e.message ?: "Import failed") }
            }
        }
    }

    private fun exportData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(isExporting = true, exportError = null) }
                val dbPath = getUserSettingsDatabasePath()
                val dbFile = File(dbPath)

                if (!dbFile.exists()) {
                    _state.update { it.copy(isExporting = false, exportError = "Database not found") }
                    return@launch
                }

                // Create export directory in user's Downloads
                val exportDir =
                    File(
                        System.getProperty("user.home"),
                        "Downloads/Zayit",
                    )
                exportDir.mkdirs()

                // Create timestamped backup file
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val exportFile = File(exportDir, "zayit_backup_$timestamp.db")

                Files.copy(
                    dbFile.toPath(),
                    exportFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )

                _state.update {
                    it.copy(
                        isExporting = false,
                        lastExportPath = exportFile.absolutePath,
                        successMessage = "Data exported to: ${exportFile.name}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, exportError = e.message ?: "Export failed") }
            }
        }
    }

    private fun clearMessages() {
        _state.update { it.copy(exportError = null, importError = null, successMessage = null) }
    }
}
