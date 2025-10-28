package io.github.kdroidfilter.seforimapp.features.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Minimal ViewModel to manage Settings window visibility only
class SettingsWindowViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsWindowState(isVisible = false))
    val state: StateFlow<SettingsWindowState> = _state.asStateFlow()

    fun onEvent(events: SettingsWindowEvents) {
        when (events) {
            is SettingsWindowEvents.onOpen -> _state.value = _state.value.copy(isVisible = true)
            is SettingsWindowEvents.onClose -> _state.value = _state.value.copy(isVisible = false)
        }
    }
}
