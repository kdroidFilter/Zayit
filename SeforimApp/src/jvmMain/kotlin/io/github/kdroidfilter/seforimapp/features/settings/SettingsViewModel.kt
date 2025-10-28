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
            persistSession = AppSettings.isPersistSessionEnabled(),
            databasePath = AppSettings.getDatabasePath(),
            bookFontCode = AppSettings.getBookFontCode(),
            commentaryFontCode = AppSettings.getCommentaryFontCode(),
            targumFontCode = AppSettings.getTargumFontCode(),
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

        // Observe font changes to keep UI in sync
        viewModelScope.launch {
            AppSettings.bookFontCodeFlow.collect { code ->
                _state.value = _state.value.copy(bookFontCode = code)
            }
        }
        viewModelScope.launch {
            AppSettings.commentaryFontCodeFlow.collect { code ->
                _state.value = _state.value.copy(commentaryFontCode = code)
            }
        }
        viewModelScope.launch {
            AppSettings.targumFontCodeFlow.collect { code ->
                _state.value = _state.value.copy(targumFontCode = code)
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
            is SettingsEvents.SetPersistSession -> {
                AppSettings.setPersistSessionEnabled(events.value)
                _state.value = _state.value.copy(persistSession = events.value)
            }
            is SettingsEvents.SetBookFont -> {
                AppSettings.setBookFontCode(events.code)
                _state.value = _state.value.copy(bookFontCode = events.code)
            }
            is SettingsEvents.SetCommentaryFont -> {
                AppSettings.setCommentaryFontCode(events.code)
                _state.value = _state.value.copy(commentaryFontCode = events.code)
            }
            is SettingsEvents.SetTargumFont -> {
                AppSettings.setTargumFontCode(events.code)
                _state.value = _state.value.copy(targumFontCode = events.code)
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
