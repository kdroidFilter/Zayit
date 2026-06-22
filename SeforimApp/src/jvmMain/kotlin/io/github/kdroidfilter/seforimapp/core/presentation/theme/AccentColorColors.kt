package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.nucleusframework.systemcolor.systemAccentColor
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme

/**
 * Returns the static color for a preset. For [AccentColor.System]/[AccentColor.Default] returns
 * Jewel's default blue (use [resolveColor] in composable contexts for the OS accent).
 */
fun AccentColor.forMode(isDark: Boolean): Color =
    when (this) {
        AccentColor.System, AccentColor.Default ->
            if (isDark) {
                IntUiDarkTheme.colors.blueOrNull(6) ?: Color(0xFF3574F0)
            } else {
                IntUiLightTheme.colors.blueOrNull(4) ?: Color(0xFF4682FA)
            }
        AccentColor.Teal -> if (isDark) Color(0xFF2FC2B6) else Color(0xFF1A998E)
        AccentColor.Green -> if (isDark) Color(0xFF5AB869) else Color(0xFF3D9A50)
        AccentColor.Gold -> if (isDark) Color(0xFFD4A843) else Color(0xFFBE9117)
    }

/** Composable-aware resolution that queries the OS accent color for [AccentColor.System]. */
@Composable
fun AccentColor.resolveColor(isDark: Boolean): Color =
    when (this) {
        AccentColor.System -> systemAccentColor() ?: forMode(isDark)
        else -> forMode(isDark)
    }

/** Resolve for display in the settings UI. */
@Composable
fun AccentColor.displayColor(isDark: Boolean): Color = resolveColor(isDark)
