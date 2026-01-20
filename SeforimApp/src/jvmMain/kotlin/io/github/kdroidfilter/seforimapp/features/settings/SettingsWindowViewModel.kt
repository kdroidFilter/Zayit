package io.github.kdroidfilter.seforimapp.features.settings

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Minimal ViewModel to manage Settings window visibility only
@ContributesIntoMap(AppScope::class)
@ViewModelKey(SettingsWindowViewModel::class)
@Inject
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
