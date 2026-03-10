package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.kdroidfilter.nucleus.systemcolor.systemAccentColor
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

/**
 * Predefined accent color presets for the application theme.
 * [System] uses the OS accent color (falls back to Jewel's blue when unavailable).
 * [Default] uses Jewel's built-in blue; other entries provide light/dark variants.
 */
enum class AccentColor {
    System,
    Default,
    Teal,
    Green,
    Gold,
    ;

    /**
     * Returns the static color for non-System presets.
     * For [System], returns the Jewel default blue (use [resolveColor] in composable contexts).
     */
    fun forMode(isDark: Boolean): Color =
        when (this) {
            System, Default ->
                if (isDark) {
                    IntUiDarkTheme.colors.blueOrNull(6) ?: Color(0xFF3574F0)
                } else {
                    IntUiLightTheme.colors.blueOrNull(4) ?: Color(0xFF4682FA)
                }
            Teal -> if (isDark) Color(0xFF2FC2B6) else Color(0xFF1A998E)
            Green -> if (isDark) Color(0xFF5AB869) else Color(0xFF3D9A50)
            Gold -> if (isDark) Color(0xFFD4A843) else Color(0xFFBE9117)
        }

    /**
     * Composable-aware resolution that queries the OS accent color for [System].
     * Falls back to [forMode] when the platform doesn't provide an accent color.
     */
    @Composable
    fun resolveColor(isDark: Boolean): Color =
        when (this) {
            System -> systemAccentColor() ?: forMode(isDark)
            else -> forMode(isDark)
        }

    /** Resolve for display in the settings UI. */
    @Composable
    fun displayColor(isDark: Boolean): Color = resolveColor(isDark)
}
