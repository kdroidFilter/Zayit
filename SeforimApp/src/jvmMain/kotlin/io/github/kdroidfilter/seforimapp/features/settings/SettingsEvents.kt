package io.github.kdroidfilter.seforimapp.features.settings

// Window-level events only for opening/closing the Settings window
sealed class SettingsEvents {
    object onOpen : SettingsEvents()
    object onClose : SettingsEvents()
}
