package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Immutable

@Immutable
data class DisplaySettingsState(
    val showHomeWallpaper: Boolean = true,
    val compactMode: Boolean = false,
    val maxCommentatorsPerPage: Int = 0,
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
