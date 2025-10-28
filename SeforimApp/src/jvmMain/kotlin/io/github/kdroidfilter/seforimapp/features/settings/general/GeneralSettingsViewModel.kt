package io.github.kdroidfilter.seforimapp.features.settings.general

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.platformtools.appmanager.restartApplication
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class GeneralSettingsViewModel : ViewModel() {

    private val dbPath = MutableStateFlow(AppSettings.getDatabasePath())
    private val closeTree = MutableStateFlow(AppSettings.getCloseBookTreeOnNewBookSelected())
    private val persist = MutableStateFlow(AppSettings.isPersistSessionEnabled())
    private val resetDone = MutableStateFlow(false)

    val state = combine(
        dbPath, closeTree, persist, resetDone
    ) { path, c, p, r ->
        GeneralSettingsState(
            databasePath = path,
            closeTreeOnNewBook = c,
            persistSession = p,
            resetDone = r
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GeneralSettingsState(
            databasePath = dbPath.value,
            closeTreeOnNewBook = closeTree.value,
            persistSession = persist.value,
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
            is GeneralSettingsEvents.ResetApp -> {
                runCatching { AppSettings.getDatabasePath() }
                    .getOrNull()
                    ?.let { path -> kotlin.runCatching { java.io.File(path).delete() } }
                AppSettings.clearAll()
                restartApplication()
                resetDone.value = true
            }
        }
    }
}

