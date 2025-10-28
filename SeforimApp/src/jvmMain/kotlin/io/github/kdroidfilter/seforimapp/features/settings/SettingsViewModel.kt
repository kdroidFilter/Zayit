package io.github.kdroidfilter.seforimapp.features.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Minimal ViewModel to manage Settings window visibility only
class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(isVisible = false))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onEvent(events: SettingsEvents) {
        when (events) {
            is SettingsEvents.onOpen -> _state.value = _state.value.copy(isVisible = true)
            is SettingsEvents.onClose -> _state.value = _state.value.copy(isVisible = false)
        }
    }
}
