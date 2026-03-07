package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

/**
 * Predefined accent color presets for the application theme.
 * [Default] uses Jewel's built-in blue; other entries provide light/dark variants.
 */
enum class AccentColor {
    Default,
    Teal,
    Green,
    Gold,
    ;

    fun forMode(isDark: Boolean): Color =
        when (this) {
            Default ->
                if (isDark) {
                    IntUiDarkTheme.colors.blueOrNull(6) ?: Color(0xFF3574F0)
                } else {
                    IntUiLightTheme.colors.blueOrNull(4) ?: Color(0xFF4682FA)
                }
            Teal -> if (isDark) Color(0xFF2FC2B6) else Color(0xFF1A998E)
            Green -> if (isDark) Color(0xFF5AB869) else Color(0xFF3D9A50)
            Gold -> if (isDark) Color(0xFFD4A843) else Color(0xFFBE9117)
        }

    /** Resolve for display in the settings UI. */
    @Composable
    fun displayColor(isDark: Boolean): Color = forMode(isDark)
}
