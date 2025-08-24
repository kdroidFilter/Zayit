package io.github.kdroidfilter.seforimapp.features.windows.settings

sealed class SettingsEvents {
    object onOpen : SettingsEvents()
    object onClose : SettingsEvents()
    data class SetCloseBookTreeOnNewBookSelected(val value: Boolean) : SettingsEvents()
}