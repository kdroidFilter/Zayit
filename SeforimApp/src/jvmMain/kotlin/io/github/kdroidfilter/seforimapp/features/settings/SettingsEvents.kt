package io.github.kdroidfilter.seforimapp.features.settings

sealed class SettingsEvents {
    object onOpen : SettingsEvents()
    object onClose : SettingsEvents()
    data class SetCloseBookTreeOnNewBookSelected(val value: Boolean) : SettingsEvents()
    data class SetPersistSession(val value: Boolean) : SettingsEvents()
    data class SetBookFont(val code: String) : SettingsEvents()
    data class SetCommentaryFont(val code: String) : SettingsEvents()
    data class SetTargumFont(val code: String) : SettingsEvents()
    object ResetApp : SettingsEvents()
}
