package io.github.kdroidfilter.seforimapp.features.settings.general

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
@ViewModelKey
@Inject
class GeneralSettingsViewModel(
    private val appSettings: AppSettings,
) : ViewModel() {
    private val dbPath = MutableStateFlow(appSettings.getDatabasePath())
    private val closeTree = MutableStateFlow(appSettings.getCloseBookTreeOnNewBookSelected())
    private val persist = MutableStateFlow(appSettings.isPersistSessionEnabled())
    private val keepAwake = MutableStateFlow(appSettings.isKeepScreenAwakeOnBookEnabled())

    val state =
        combine(dbPath, closeTree, persist, keepAwake) { path, c, p, k ->
            GeneralSettingsState(
                databasePath = path,
                closeTreeOnNewBook = c,
                persistSession = p,
                keepScreenAwakeOnBook = k,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            GeneralSettingsState(
                databasePath = dbPath.value,
                closeTreeOnNewBook = closeTree.value,
                persistSession = persist.value,
                keepScreenAwakeOnBook = keepAwake.value,
            ),
        )

    fun onEvent(event: GeneralSettingsEvents) {
        when (event) {
            is GeneralSettingsEvents.SetCloseTreeOnNewBook -> {
                appSettings.setCloseBookTreeOnNewBookSelected(event.value)
                closeTree.value = event.value
            }
            is GeneralSettingsEvents.SetPersistSession -> {
                appSettings.setPersistSessionEnabled(event.value)
                persist.value = event.value
            }
            is GeneralSettingsEvents.SetKeepScreenAwakeOnBook -> {
                appSettings.setKeepScreenAwakeOnBookEnabled(event.value)
                keepAwake.value = event.value
            }
        }
    }
}
