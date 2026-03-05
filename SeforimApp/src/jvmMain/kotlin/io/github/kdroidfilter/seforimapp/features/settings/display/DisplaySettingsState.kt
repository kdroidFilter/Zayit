package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.runtime.Immutable

@Immutable
data class DisplaySettingsState(
    val showZmanimWidgets: Boolean = true,
    val compactMode: Boolean = false,
    val useOpenGl: Boolean = false,
) {
    companion object {
        val preview =
            DisplaySettingsState(
                showZmanimWidgets = true,
                compactMode = false,
                useOpenGl = false,
            )
    }
}
