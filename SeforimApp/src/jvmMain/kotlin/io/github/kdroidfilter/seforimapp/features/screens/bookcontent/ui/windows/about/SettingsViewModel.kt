package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.windows.about

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun onEvent(events: SettingsEvents) {
        when (events) {
            is SettingsEvents.onOpen -> _state.value = _state.value.copy(isVisible = true)
            is SettingsEvents.onClose -> _state.value = _state.value.copy(isVisible = false)
        }
    }
}