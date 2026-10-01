package io.github.kdroidfilter.seforimapp.core

import androidx.compose.runtime.Stable
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-level state holder for theme and onboarding.
 * Injected as a singleton via Metro DI.
 */
@Stable
class MainAppState(
    private val appSettings: AppSettings,
) {
    private val _theme = MutableStateFlow(appSettings.getThemeMode())
    val theme: StateFlow<IntUiThemes> = _theme.asStateFlow()

    fun setTheme(theme: IntUiThemes) {
        _theme.value = theme
        appSettings.setThemeMode(theme)
    }

    private val _themeStyle = MutableStateFlow(appSettings.getThemeStyle())
    val themeStyle: StateFlow<ThemeStyle> = _themeStyle.asStateFlow()

    fun setThemeStyle(style: ThemeStyle) {
        _themeStyle.value = style
        appSettings.setThemeStyle(style)
    }

    private val _accentColor = MutableStateFlow(appSettings.getAccentColor())
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    fun setAccentColor(accent: AccentColor) {
        _accentColor.value = accent
        appSettings.setAccentColor(accent)
    }

    private val _showOnboarding = MutableStateFlow<Boolean?>(null)
    val showOnBoarding: StateFlow<Boolean?> = _showOnboarding.asStateFlow()

    fun setShowOnBoarding(value: Boolean?) {
        _showOnboarding.value = value
    }
}
