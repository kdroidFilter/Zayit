package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.earthwidget.WidgetAntiAliasing

@Immutable
data class DisplaySettingsState(
    val showHomeWallpaper: Boolean = true,
    val compactMode: Boolean = false,
    val maxCommentatorsPerPage: Int = 0,
    val antiAliasing: WidgetAntiAliasing = WidgetAntiAliasing.OFF,
) {
    companion object {
        val preview =
            DisplaySettingsState(
                showHomeWallpaper = true,
                compactMode = false,
                maxCommentatorsPerPage = 0,
            )
    }
}
