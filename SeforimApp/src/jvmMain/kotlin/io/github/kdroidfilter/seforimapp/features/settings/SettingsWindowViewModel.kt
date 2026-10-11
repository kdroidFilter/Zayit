package io.github.kdroidfilter.seforimapp.features.settings

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Minimal ViewModel to manage Settings window visibility only
@ContributesIntoMap(AppScope::class)
@ViewModelKey
@Inject
class SettingsWindowViewModel(
    private val desktopManager: DesktopManager,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsWindowState(isVisible = false))
    val state: StateFlow<SettingsWindowState> = _state.asStateFlow()

    fun onEvent(events: SettingsWindowEvents) {
        when (events) {
            is SettingsWindowEvents.OnOpen ->
                _state.value =
                    SettingsWindowState(
                        isVisible = true,
                        ownerWindowId = desktopManager.focusedWindowId.value,
                    )
            is SettingsWindowEvents.OnOpenTo ->
                _state.value =
                    SettingsWindowState(
                        isVisible = true,
                        initialDestination = events.destination,
                        ownerWindowId = desktopManager.focusedWindowId.value,
                    )
            is SettingsWindowEvents.OnClose -> _state.value = SettingsWindowState(isVisible = false)
        }
    }
}
