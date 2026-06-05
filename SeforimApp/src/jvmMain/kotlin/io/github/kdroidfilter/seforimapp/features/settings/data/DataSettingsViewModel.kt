package io.github.kdroidfilter.seforimapp.features.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.platformtools.appmanager.restartApplication
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

    fun exportToFile(exportDir: File) {
        if (!exportDir.isDirectory) {
            _state.update { it.copy(exportFailed = true, exportedFileName = null) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(isExporting = true, exportFailed = false, exportedFileName = null) }
                val dbFile = File(getUserSettingsDatabasePath())

                if (!dbFile.exists()) {
                    _state.update { it.copy(isExporting = false, exportFailed = true) }
                    return@launch
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val exportFile = File(exportDir, "zayit_backup_$timestamp.db")

                Files.copy(
                    dbFile.toPath(),
                    exportFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )

                _state.update {
                    it.copy(isExporting = false, exportedFileName = exportFile.name)
                }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, exportFailed = true) }
            }
        }
    }

    fun importFromFile(importFile: File) {
        if (!importFile.exists()) {
            _state.update { it.copy(importFailed = true, importSucceeded = false) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.update { it.copy(isImporting = true, importFailed = false, importSucceeded = false) }
                val dbFile = File(getUserSettingsDatabasePath())

                // Copy imported file to replace current DB
                Files.copy(
                    importFile.toPath(),
                    dbFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )

                // The running app holds an open connection to the old DB; restart to load the imported one.
                _state.update { it.copy(isImporting = false, importSucceeded = true) }
                restartApplication()
            } catch (e: Exception) {
                _state.update { it.copy(isImporting = false, importFailed = true) }
            }
        }
    }
}
