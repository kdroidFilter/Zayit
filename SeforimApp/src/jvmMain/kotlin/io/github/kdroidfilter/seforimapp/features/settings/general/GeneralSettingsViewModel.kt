package io.github.kdroidfilter.seforimapp.features.settings.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.platformtools.appmanager.restartApplication
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class)
@ViewModelKey(GeneralSettingsViewModel::class)
class GeneralSettingsViewModel @Inject constructor() : ViewModel() {

    private val dbPath = MutableStateFlow(AppSettings.getDatabasePath())
    private val closeTree = MutableStateFlow(AppSettings.getCloseBookTreeOnNewBookSelected())
    private val persist = MutableStateFlow(AppSettings.isPersistSessionEnabled())
    private val ramSaver = MutableStateFlow(AppSettings.isRamSaverEnabled())
    private val resetDone = MutableStateFlow(false)

    val state = combine(
        dbPath, closeTree, persist, ramSaver, resetDone
    ) { path, c, p, ram, r ->
        GeneralSettingsState(
            databasePath = path,
            closeTreeOnNewBook = c,
            persistSession = p,
            ramSaver = ram,
            resetDone = r
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GeneralSettingsState(
            databasePath = dbPath.value,
            closeTreeOnNewBook = closeTree.value,
            persistSession = persist.value,
            ramSaver = ramSaver.value,
            resetDone = resetDone.value,
        )
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
            is GeneralSettingsEvents.SetRamSaver -> {
                AppSettings.setRamSaverEnabled(event.value)
                ramSaver.value = event.value
            }
            is GeneralSettingsEvents.ResetApp -> {
                val dbPath = runCatching { AppSettings.getDatabasePath() }.getOrNull()
                if (!dbPath.isNullOrBlank()) {
                    val dbFile = File(dbPath)
                    val baseDir = dbFile.parentFile
                    // Delete DB file
                    runCatching { if (dbFile.exists()) dbFile.delete() }
                    // Delete Lucene index directories next to the DB
                    if (baseDir != null && baseDir.exists()) {
                        val dirNames = listOf(
                            dbFile.name + ".lucene",
                            dbFile.name + ".lookup.lucene",
                            dbFile.name + ".luceneindex",
                            dbFile.name + ".lookupindex",
                        )
                        dirNames.forEach { name ->
                            val d = File(baseDir, name)
                            if (d.exists()) runCatching { d.deleteRecursively() }
                        }
                    }
                }
                AppSettings.clearAll()
                restartApplication()
                resetDone.value = true
            }
        }
    }
}
