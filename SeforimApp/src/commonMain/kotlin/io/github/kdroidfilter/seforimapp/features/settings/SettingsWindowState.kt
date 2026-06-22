package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination

// Window-level settings state: only controls visibility of the Settings window.
data class SettingsWindowState(
    val isVisible: Boolean = false,
    val initialDestination: SettingsDestination? = null,
)
