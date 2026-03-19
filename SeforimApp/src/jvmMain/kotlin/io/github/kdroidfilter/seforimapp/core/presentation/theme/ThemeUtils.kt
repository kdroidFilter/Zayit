package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notoserifhebrew

/**
 * Utilities to build consistent Jewel theme definitions and related styling across the app.
 */
object ThemeUtils {
    /**
     * Provides the app's default text style (centralized so callers don't repeat it).
     */
    @Composable
    fun defaultTextStyle(): TextStyle =
        TextStyle(
            fontFamily =
                FontFamily(
                    Font(resource = Res.font.notoserifhebrew),
                ),
        )

    @Composable
    fun isDarkTheme(): Boolean {
        val mainAppState = LocalAppGraph.current.mainAppState
        val theme = mainAppState.theme.collectAsState().value
        return when (theme) {
            IntUiThemes.Light -> false
            IntUiThemes.Dark -> true
            IntUiThemes.System -> isSystemInDarkMode()
        }
    }

    /** Jewel's primary accent color blended at 25% over the panel background,
     *  used as the title bar gradient tint. Fully opaque to avoid the AWT
     *  window background bleeding through semi-transparent pixels. */
    @Composable
    fun titleBarGradientColor(): Color {
        val accent = JewelTheme.globalColors.outlines.focused
        val bg = JewelTheme.globalColors.panelBackground
        val t = 0.25f
        return Color(
            red = bg.red * (1f - t) + accent.red * t,
            green = bg.green * (1f - t) + accent.green * t,
            blue = bg.blue * (1f - t) + accent.blue * t,
            alpha = 1f,
        )
    }

    /**
     * Builds a Jewel theme definition driven by three axes:
     * - theme mode (Light / Dark / System) — controls brightness
     * - theme style (Classic / Islands) — controls the color palette
     * - accent color — optionally overrides the primary accent
     */
    @Composable
    fun buildThemeDefinition() =
        run {
            val mainAppState = LocalAppGraph.current.mainAppState
            val isDark = isDarkTheme()
            val themeStyle = mainAppState.themeStyle.collectAsState().value
            val accentColor = mainAppState.accentColor.collectAsState().value
            val accent = accentColor.resolveColor(isDark)
            val disabledValues = if (isDark) DisabledAppearanceValues.dark() else DisabledAppearanceValues.light()
            val iconData = accentIconData(accent, isDark)

            when (themeStyle) {
                ThemeStyle.Islands ->
                    if (isDark) {
                        JewelTheme.darkThemeDefinition(
                            colors = islandsDarkGlobalColors(accent),
                            iconData = iconData,
                            defaultTextStyle = defaultTextStyle(),
                            disabledAppearanceValues = disabledValues,
                        )
                    } else {
                        JewelTheme.lightThemeDefinition(
                            colors = lightIslandsGlobalColors(accent),
                            iconData = iconData,
                            defaultTextStyle = defaultTextStyle(),
                            disabledAppearanceValues = disabledValues,
                        )
                    }
                ThemeStyle.Classic ->
                    if (isDark) {
                        JewelTheme.darkThemeDefinition(
                            colors = classicDarkGlobalColors(accent),
                            iconData = iconData,
                            defaultTextStyle = defaultTextStyle(),
                            disabledAppearanceValues = disabledValues,
                        )
                    } else {
                        JewelTheme.lightThemeDefinition(
                            colors = classicLightGlobalColors(accent),
                            iconData = iconData,
                            defaultTextStyle = defaultTextStyle(),
                            disabledAppearanceValues = disabledValues,
                        )
                    }
            }
        }

    /**
     * Builds the [ComponentStyling] matching the current theme style and accent color.
     */
    @Composable
    fun buildComponentStyling(): ComponentStyling {
        val mainAppState = LocalAppGraph.current.mainAppState
        val isDark = isDarkTheme()
        val themeStyle = mainAppState.themeStyle.collectAsState().value
        val accent =
            mainAppState.accentColor
                .collectAsState()
                .value
                .resolveColor(isDark)
        return when (themeStyle) {
            ThemeStyle.Islands -> islandsComponentStyling(isDark, accent)
            ThemeStyle.Classic -> classicComponentStyling(isDark, accent)
        }
    }

    /** Returns true if the Islands style is active. */
    @Composable
    fun isIslandsStyle(): Boolean {
        val mainAppState = LocalAppGraph.current.mainAppState
        return mainAppState.themeStyle.collectAsState().value == ThemeStyle.Islands
    }

    /** GlobalColors for the dark variant of the "Islands Dark" VS Code theme. */
    private fun islandsDarkGlobalColors(accent: Color): GlobalColors =
        GlobalColors.dark(
            borders =
                BorderColors.dark(
                    normal = Color(0xFF3C3F41),
                    focused = accent,
                    disabled = Color(0xFF2B2D30),
                ),
            outlines =
                OutlineColors.dark(
                    focused = accent,
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
            text =
                TextColors.dark(
                    normal = Color(0xFFBCBEC4),
                    selected = Color(0xFFBCBEC4),
                    disabled = Color(0xFF7A7E85),
                    info = Color(0xFF7A7E85),
                    error = Color(0xFFF75464),
                ),
            panelBackground = Color(0xFF1E1F22),
            toolwindowBackground = Color(0xFF181A1D),
        )

    /**
     * GlobalColors for the light variant of Islands:
     * standard light palette overridden with the accent color.
     * Canvas (toolwindowBackground) is slightly darker than panel to show rounded card edges.
     */
    private fun lightIslandsGlobalColors(accent: Color): GlobalColors =
        GlobalColors.light(
            outlines =
                OutlineColors.light(
                    focused = accent,
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
            borders =
                BorderColors.light(
                    focused = accent,
                ),
            toolwindowBackground = Color(0xFFE8E9EB),
        )

    /** GlobalColors for Classic dark with a custom accent override. */
    private fun classicDarkGlobalColors(accent: Color): GlobalColors =
        GlobalColors.dark(
            borders = BorderColors.dark(focused = accent),
            outlines =
                OutlineColors.dark(
                    focused = accent,
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
        )

    /** GlobalColors for Classic light with a custom accent override. */
    private fun classicLightGlobalColors(accent: Color): GlobalColors =
        GlobalColors.light(
            borders = BorderColors.light(focused = accent),
            outlines =
                OutlineColors.light(
                    focused = accent,
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
        )

    /**
     * Builds a [ThemeIconData] that patches checkbox/radio SVG colors
     * to use the given accent color for their selected state.
     */
    private fun accentIconData(
        accent: Color,
        isDark: Boolean,
    ): ThemeIconData {
        val hex = accent.toHexString()
        val base = if (isDark) IntUiDarkTheme.iconData else IntUiLightTheme.iconData
        return ThemeIconData(
            iconOverrides = base.iconOverrides,
            colorPalette =
                base.colorPalette +
                    mapOf(
                        "Checkbox.Background.Selected" to hex,
                        "Checkbox.Border.Selected" to hex,
                        "Checkbox.Focus.Thin.Selected" to hex,
                    ),
            selectionColorPalette = base.selectionColorPalette,
        )
    }

    private fun Color.toHexString(): String {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }
}
