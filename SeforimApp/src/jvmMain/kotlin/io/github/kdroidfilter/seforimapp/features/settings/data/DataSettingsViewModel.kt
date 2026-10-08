package io.github.kdroidfilter.seforimapp.features.settings.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nucleusframework.core.runtime.AppRestarter.restartApplication
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.backup.BackupManager
import io.github.kdroidfilter.seforimapp.backup.LocalFileDestination
import io.github.kdroidfilter.seforimapp.backup.drive.DriveSyncState
import io.github.kdroidfilter.seforimapp.backup.drive.GoogleDriveSync
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class DataSettingsViewModel(
    private val appSettings: AppSettings,
    private val backupManager: BackupManager,
    private val driveSync: GoogleDriveSync,
) : ViewModel() {
    private val _state = MutableStateFlow(DataSettingsState())
    val state: StateFlow<DataSettingsState> = _state.asStateFlow()

    val isDriveAvailable: Boolean = driveSync.isAvailable
    val driveState: StateFlow<DriveSyncState> = driveSync.state

    fun connectDrive() = driveSync.connect()

    fun cancelDriveConnect() = driveSync.cancelConnect()

    fun disconnectDrive() = driveSync.disconnect()

    fun backupToDrive() = driveSync.backupNow()

    fun restoreFromDrive() = driveSync.restore()

    private fun restartAfterRestore() {
        _state.update { it.copy(importSucceeded = true) }
        restartApplication()
    }

    fun exportToFile(exportDir: File) {
        if (!exportDir.isDirectory) {
            _state.update { it.copy(exportFailed = true, exportedFileName = null) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, exportFailed = false, exportedFileName = null) }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val exportFile = File(exportDir, "zayit_backup_$timestamp.$BACKUP_EXTENSION")
            try {
                backupManager.backup(LocalFileDestination(exportFile))
                _state.update { it.copy(isExporting = false, exportedFileName = exportFile.name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, exportFailed = true) }
            }
        }
    }

    fun importFromFile(importFile: File) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importFailed = false, importSucceeded = false) }
            val staged =
                try {
                    backupManager.restore(LocalFileDestination(importFile))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
            _state.update { it.copy(isImporting = false, importFailed = !staged) }
            if (staged) restartAfterRestore()
        }
    }

    /**
     * Wipes everything: books database, search indexes and all personal data (notes, highlights,
     * session) — both the managed databases directory and any custom DB location — then clears the
     * settings and restarts the app.
     */
    fun resetApp() {
        viewModelScope.launch(Dispatchers.IO) {
            val dbDir = File(FileKit.databasesDir.path)
            // Read the custom DB path before clearing settings (clearAll() drops it).
            val customDbPath = runCatching { appSettings.getDatabasePath() }.getOrNull()

            appSettings.clearAll()

            // Delete every file/directory in the managed databases directory (this also holds the
            // user settings DB with notes and highlights).
            if (dbDir.exists()) {
                dbDir.listFiles()?.forEach { file ->
                    runCatching {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                }
            }

            // Also clean a custom DB location, if the user pointed the books DB elsewhere.
            if (!customDbPath.isNullOrBlank()) {
                val customDbFile = File(customDbPath)
                val customBaseDir = customDbFile.parentFile
                runCatching { if (customDbFile.exists()) customDbFile.delete() }
                if (customBaseDir != null && customBaseDir.exists() && customBaseDir != dbDir) {
                    listOf(
                        customDbFile.name + ".lucene",
                        customDbFile.name + ".lookup.lucene",
                        "lexical.db",
                        "catalog.pb",
                        "release_info.txt",
                    ).forEach { name ->
                        val f = File(customBaseDir, name)
                        if (f.exists()) {
                            runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }
                        }
                    }
                }
            }

            _state.update { it.copy(resetDone = true) }
            restartApplication()
        }
    }

    companion object {
        const val BACKUP_EXTENSION = "zip"

        // The exports made before the archive format: a bare copy of the user database
        const val LEGACY_BACKUP_EXTENSION = "db"
    }
}
