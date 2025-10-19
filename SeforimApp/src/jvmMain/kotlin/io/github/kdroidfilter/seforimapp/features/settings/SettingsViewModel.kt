package io.github.kdroidfilter.seforimapp.features.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import io.github.kdroidfilter.seforimapp.core.settings.AppSettings

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            closedAutomaticallyBookTreePaneOnNewBookSelected = AppSettings.getCloseBookTreeOnNewBookSelected()
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onEvent(events: SettingsEvents) {
        when (events) {
            is SettingsEvents.onOpen -> _state.value = _state.value.copy(isVisible = true)
            is SettingsEvents.onClose -> _state.value = _state.value.copy(isVisible = false)
            is SettingsEvents.SetCloseBookTreeOnNewBookSelected -> {
                AppSettings.setCloseBookTreeOnNewBookSelected(events.value)
                _state.value = _state.value.copy(closedAutomaticallyBookTreePaneOnNewBookSelected = events.value)
            }
        }
    }
}