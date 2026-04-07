package io.github.kdroidfilter.seforimapp.features.settings.display

sealed interface DisplaySettingsEvents {
    data class SetShowZmanimWidgets(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetShowHomeWallpaper(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetCompactMode(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetUseOpenGl(
        val value: Boolean,
    ) : DisplaySettingsEvents
}
