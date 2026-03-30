package io.github.kdroidfilter.seforimapp.features.settings

import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination

// Window-level events only for opening/closing the Settings window
sealed class SettingsWindowEvents {
    data object OnOpen : SettingsWindowEvents()

    data class OnOpenTo(val destination: SettingsDestination) : SettingsWindowEvents()

    data object OnClose : SettingsWindowEvents()
}
