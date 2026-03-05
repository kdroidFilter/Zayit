package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class)
@ViewModelKey(DisplaySettingsViewModel::class)
@Inject
class DisplaySettingsViewModel : ViewModel() {
    private val showZmanim = MutableStateFlow(AppSettings.isShowZmanimWidgetsEnabled())
    private val compactMode = MutableStateFlow(AppSettings.isCompactModeEnabled())
    private val useOpenGl = MutableStateFlow(AppSettings.isUseOpenGlEnabled())

    val state =
        combine(showZmanim, compactMode, useOpenGl) { z, compact, gl ->
            DisplaySettingsState(
                showZmanimWidgets = z,
                compactMode = compact,
                useOpenGl = gl,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DisplaySettingsState(
                showZmanimWidgets = showZmanim.value,
                compactMode = compactMode.value,
                useOpenGl = useOpenGl.value,
            ),
        )

    fun onEvent(event: DisplaySettingsEvents) {
        when (event) {
            is DisplaySettingsEvents.SetShowZmanimWidgets -> {
                AppSettings.setShowZmanimWidgetsEnabled(event.value)
                showZmanim.value = event.value
            }
            is DisplaySettingsEvents.SetCompactMode -> {
                AppSettings.setCompactModeEnabled(event.value)
                compactMode.value = event.value
            }
            is DisplaySettingsEvents.SetUseOpenGl -> {
                AppSettings.setUseOpenGlEnabled(event.value)
                useOpenGl.value = event.value
            }
        }
    }
}
