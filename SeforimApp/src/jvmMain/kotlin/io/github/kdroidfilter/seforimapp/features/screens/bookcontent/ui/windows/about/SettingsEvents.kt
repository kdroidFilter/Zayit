package io.github.kdroidfilter.seforimapp.features.screens.bookcontent.ui.windows.about

sealed class SettingsEvents {
    object onOpen : SettingsEvents()
    object onClose : SettingsEvents()
}