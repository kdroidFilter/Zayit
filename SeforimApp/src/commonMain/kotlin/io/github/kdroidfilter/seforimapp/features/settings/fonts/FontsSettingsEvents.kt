package io.github.kdroidfilter.seforimapp.features.settings.fonts

sealed interface FontsSettingsEvents {
    data class SetBookFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetCommentaryFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetTargumFont(
        val code: String,
    ) : FontsSettingsEvents

    data class SetSourceFont(
        val code: String,
    ) : FontsSettingsEvents

    data object ResetToDefaults : FontsSettingsEvents
}
