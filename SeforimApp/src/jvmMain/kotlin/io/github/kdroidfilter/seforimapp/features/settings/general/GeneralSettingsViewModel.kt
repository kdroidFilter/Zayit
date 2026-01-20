package io.github.kdroidfilter.seforimapp.features.settings.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.platformtools.appmanager.restartApplication
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

@ContributesIntoMap(AppScope::class)
@ViewModelKey(GeneralSettingsViewModel::class)
@Inject
class GeneralSettingsViewModel : ViewModel() {
    private val dbPath = MutableStateFlow(AppSettings.getDatabasePath())
    private val closeTree = MutableStateFlow(AppSettings.getCloseBookTreeOnNewBookSelected())
    private val persist = MutableStateFlow(AppSettings.isPersistSessionEnabled())
    private val showZmanim = MutableStateFlow(AppSettings.isShowZmanimWidgetsEnabled())
    private val useOpenGl = MutableStateFlow(AppSettings.isUseOpenGlEnabled())
    private val resetDone = MutableStateFlow(false)

    val state =
        combine(
            combine(dbPath, closeTree, persist) { path, c, p -> Triple(path, c, p) },
            combine(showZmanim, useOpenGl, resetDone) { z, gl, r -> Triple(z, gl, r) },
        ) { (path, c, p), (z, gl, r) ->
            GeneralSettingsState(
                databasePath = path,
                closeTreeOnNewBook = c,
                persistSession = p,
                showZmanimWidgets = z,
                useOpenGl = gl,
                resetDone = r,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GeneralSettingsState(
                databasePath = dbPath.value,
                closeTreeOnNewBook = closeTree.value,
                persistSession = persist.value,
                showZmanimWidgets = showZmanim.value,
                useOpenGl = useOpenGl.value,
                resetDone = resetDone.value,
            ),
        )

    fun onEvent(event: GeneralSettingsEvents) {
        when (event) {
            is GeneralSettingsEvents.SetCloseTreeOnNewBook -> {
                AppSettings.setCloseBookTreeOnNewBookSelected(event.value)
                closeTree.value = event.value
            }
            is GeneralSettingsEvents.SetPersistSession -> {
                AppSettings.setPersistSessionEnabled(event.value)
                persist.value = event.value
            }
            is GeneralSettingsEvents.SetShowZmanimWidgets -> {
                AppSettings.setShowZmanimWidgetsEnabled(event.value)
                showZmanim.value = event.value
            }
            is GeneralSettingsEvents.SetUseOpenGl -> {
                AppSettings.setUseOpenGlEnabled(event.value)
                useOpenGl.value = event.value
            }
            is GeneralSettingsEvents.ResetApp -> {
                // Get the databases directory
                val dbDir = File(FileKit.databasesDir.path)

                // Get custom DB path BEFORE clearing settings
                val customDbPath = runCatching { AppSettings.getDatabasePath() }.getOrNull()

                // Clear settings first
                AppSettings.clearAll()

                // Delete all files and directories in the databases directory
                if (dbDir.exists()) {
                    dbDir.listFiles()?.forEach { file ->
                        runCatching {
                            if (file.isDirectory) {
                                file.deleteRecursively()
                            } else {
                                file.delete()
                            }
                        }
                    }
                }

                // Also delete database files in custom path (if user selected a different location)
                if (!customDbPath.isNullOrBlank()) {
                    val customDbFile = File(customDbPath)
                    val customBaseDir = customDbFile.parentFile

                    // Delete the database file
                    runCatching { if (customDbFile.exists()) customDbFile.delete() }

                    // Delete related files in the custom directory
                    if (customBaseDir != null && customBaseDir.exists() && customBaseDir != dbDir) {
                        val relatedPatterns =
                            listOf(
                                customDbFile.name + ".lucene",
                                customDbFile.name + ".lookup.lucene",
                                "lexical.db",
                                "catalog.pb",
                                "release_info.txt",
                            )
                        relatedPatterns.forEach { name ->
                            val f = File(customBaseDir, name)
                            if (f.exists()) {
                                runCatching {
                                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                                }
                            }
                        }
                    }
                }

                restartApplication()
                resetDone.value = true
            }
        }
    }
}
