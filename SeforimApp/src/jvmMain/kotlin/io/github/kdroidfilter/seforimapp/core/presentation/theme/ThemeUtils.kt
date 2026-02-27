package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.window.DecoratedWindowDefaults
import io.github.kdroidfilter.nucleus.window.styling.TitleBarMetrics
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.notoserifhebrew

/**
 * Pre-computed Jewel editor text style (JetBrains Mono with explicit line height)
 * so that [ThemeUtils.buildThemeDefinition] can pass it to Jewel's theme definitions
 * and avoid the default createEditorTextStyle call which crashes on GraalVM native image.
 */
val LocalJewelEditorTextStyle = staticCompositionLocalOf { TextStyle.Default }

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

    /** Jewel's primary accent color at 25% opacity, used as the title bar gradient tint. */
    @Composable
    fun titleBarGradientColor(): Color =
        JewelTheme.globalColors.outlines.focused
            .copy(alpha = 0.25f)

    /**
     * Builds the standard custom title bar style used across all app windows:
     * - background matches Jewel's panel background
     * - gradient metrics from the left edge
     */
    @Composable
    fun buildCustomTitleBarStyle(): TitleBarStyle {
        val isDark = isDarkTheme()
        val themeDefinition = buildThemeDefinition()
        val panelBg = themeDefinition.globalColors.panelBackground
        val accent = themeDefinition.globalColors.outlines.focused
        val base = if (isDark) DecoratedWindowDefaults.darkTitleBarStyle() else DecoratedWindowDefaults.lightTitleBarStyle()
        return base.copy(
            colors =
                base.colors.copy(
                    background = panelBg,
                    inactiveBackground = panelBg,
                    // Transparent so the gradient shows through the macOS traffic lights area
                    fullscreenControlButtonsBackground = Color.Transparent,
                    // Icon button states blend with the gradient using the accent color
                    iconButtonHoveredBackground = accent.copy(alpha = 0.12f),
                    iconButtonPressedBackground = accent.copy(alpha = 0.20f),
                ),
            metrics = TitleBarMetrics(height = 40.dp, gradientStartX = 0.dp, gradientEndX = 280.dp),
        )
    }

    /**
     * Builds a Jewel theme definition driven by two independent axes:
     * - theme mode (Light / Dark / System) — controls brightness
     * - theme style (Classic / Islands) — controls the color palette
     */
    @Composable
    fun buildThemeDefinition() =
        run {
            val mainAppState = LocalAppGraph.current.mainAppState
            val isDark = isDarkTheme()
            val themeStyle = mainAppState.themeStyle.collectAsState().value
            val disabledValues = if (isDark) DisabledAppearanceValues.dark() else DisabledAppearanceValues.light()
            val editorTextStyle = LocalJewelEditorTextStyle.current

            when (themeStyle) {
                ThemeStyle.Islands ->
                    if (isDark) {
                        JewelTheme.darkThemeDefinition(
                            colors = islandsDarkGlobalColors(),
                            defaultTextStyle = defaultTextStyle(),
                            editorTextStyle = editorTextStyle,
                            disabledAppearanceValues = disabledValues,
                        )
                    } else {
                        // Light variant of Dark Islands: standard light theme with Islands blue accent
                        JewelTheme.lightThemeDefinition(
                            colors = lightIslandsGlobalColors(),
                            defaultTextStyle = defaultTextStyle(),
                            editorTextStyle = editorTextStyle,
                            disabledAppearanceValues = disabledValues,
                        )
                    }
                ThemeStyle.Classic ->
                    if (isDark) {
                        JewelTheme.darkThemeDefinition(
                            defaultTextStyle = defaultTextStyle(),
                            editorTextStyle = editorTextStyle,
                            disabledAppearanceValues = disabledValues,
                        )
                    } else {
                        JewelTheme.lightThemeDefinition(
                            defaultTextStyle = defaultTextStyle(),
                            editorTextStyle = editorTextStyle,
                            disabledAppearanceValues = disabledValues,
                        )
                    }
            }
        }

    /** Returns true if the Islands style is active. */
    @Composable
    fun isIslandsStyle(): Boolean {
        val mainAppState = LocalAppGraph.current.mainAppState
        return mainAppState.themeStyle.collectAsState().value == ThemeStyle.Islands
    }

    /** GlobalColors for the dark variant of the "Islands Dark" VS Code theme. */
    private fun islandsDarkGlobalColors(): GlobalColors =
        GlobalColors.dark(
            borders =
                BorderColors.dark(
                    normal = Color(0xFF3C3F41),
                    focused = Color(0xFF548AF7),
                    disabled = Color(0xFF2B2D30),
                ),
            outlines =
                OutlineColors.dark(
                    focused = Color(0xFF548AF7),
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
     * standard light palette overridden with the Islands blue accent (#548AF7).
     * Canvas (toolwindowBackground) is slightly darker than panel to show rounded card edges.
     */
    private fun lightIslandsGlobalColors(): GlobalColors =
        GlobalColors.light(
            outlines =
                OutlineColors.light(
                    focused = Color(0xFF548AF7),
                    focusedWarning = Color(0xFFE8A33E),
                    focusedError = Color(0xFFF75464),
                    warning = Color(0xFFE8A33E),
                    error = Color(0xFFF75464),
                ),
            borders =
                BorderColors.light(
                    focused = Color(0xFF548AF7),
                ),
            toolwindowBackground = Color(0xFFE8E9EB),
        )
}
