package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination

// Window-level settings state: only controls visibility of the Settings window.
data class SettingsWindowState(
    val isVisible: Boolean = false,
    val initialDestination: SettingsDestination? = null,
    // Window that was focused when the dialog was opened. The dialog is composed
    // inside that window's content so it is modal to (and transient for) that
    // window only; null falls back to an application-scope, app-modal dialog.
    val ownerWindowId: String? = null,
)
