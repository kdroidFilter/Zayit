package io.github.kdroidfilter.seforimapp.features.settings

// Window-level events only for opening/closing the Settings window
sealed class SettingsWindowEvents {
    object onOpen : SettingsWindowEvents()

    object onClose : SettingsWindowEvents()
}
