package io.github.kdroidfilter.seforimapp.features.settings.display

import io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing

sealed interface DisplaySettingsEvents {
    data class SetShowHomeWallpaper(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetCompactMode(
        val value: Boolean,
    ) : DisplaySettingsEvents

    data class SetMaxCommentatorsPerPage(
        val value: Int,
    ) : DisplaySettingsEvents

    data class SetAntiAliasing(
        val value: WidgetAntiAliasing,
    ) : DisplaySettingsEvents
}
