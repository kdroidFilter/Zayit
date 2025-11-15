package io.github.kdroidfilter.seforimapp.core

import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MainAppState {

    private val _theme = MutableStateFlow(IntUiThemes.System)
    val theme: StateFlow<IntUiThemes> = _theme.asStateFlow()

    fun setTheme(theme: IntUiThemes) {
        _theme.value = theme
        AppSettings.setThemeMode(theme)
    }

    private val _showOnboarding = MutableStateFlow<Boolean?>(null)
    val showOnBoarding: StateFlow<Boolean?> = _showOnboarding.asStateFlow()

    fun setShowOnBoarding(value: Boolean?) {
        _showOnboarding.value = value
    }
}
