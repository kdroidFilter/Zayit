package io.github.kdroidfilter.seforimapp.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.platformtools.appmanager.restartApplication

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            closedAutomaticallyBookTreePaneOnNewBookSelected = AppSettings.getCloseBookTreeOnNewBookSelected(),
            databasePath = AppSettings.getDatabasePath()
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        // Observe database path changes to keep UI in sync
        viewModelScope.launch {
            AppSettings.databasePathFlow.collect { path ->
                _state.value = _state.value.copy(databasePath = path)
            }
        }
    }

    fun onEvent(events: SettingsEvents) {
        when (events) {
            is SettingsEvents.onOpen -> _state.value = _state.value.copy(isVisible = true)
            is SettingsEvents.onClose -> _state.value = _state.value.copy(isVisible = false)
            is SettingsEvents.SetCloseBookTreeOnNewBookSelected -> {
                AppSettings.setCloseBookTreeOnNewBookSelected(events.value)
                _state.value = _state.value.copy(closedAutomaticallyBookTreePaneOnNewBookSelected = events.value)
            }
            is SettingsEvents.ResetApp -> {
                // Delete database file if exists, then clear all settings
                runCatching { AppSettings.getDatabasePath() }
                    .getOrNull()
                    ?.let { path ->
                        kotlin.runCatching { java.io.File(path).delete() }
                    }
                AppSettings.clearAll()
                // Immediately restart the application after reset
                restartApplication()
                _state.value = _state.value.copy(resetDone = true)
            }
        }
    }
}
