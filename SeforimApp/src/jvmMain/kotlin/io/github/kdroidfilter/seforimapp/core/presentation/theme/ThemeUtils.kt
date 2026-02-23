package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.nucleus.window.DecoratedWindowDefaults
import io.github.kdroidfilter.nucleus.window.styling.TitleBarMetrics
import io.github.kdroidfilter.nucleus.window.styling.TitleBarStyle
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
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

    /**
     * Builds the standard custom title bar style used across all app windows:
     * - background matches Jewel's panel background
     * - light golden gradient from the left edge
     */
    @Composable
    fun buildCustomTitleBarStyle(): TitleBarStyle {
        val isDark = isDarkTheme()
        val panelBg = buildThemeDefinition().globalColors.panelBackground
        val base = if (isDark) DecoratedWindowDefaults.darkTitleBarStyle() else DecoratedWindowDefaults.lightTitleBarStyle()
        return base.copy(
            colors = base.colors.copy(background = panelBg, inactiveBackground = panelBg),
            metrics = TitleBarMetrics(height = 40.dp, gradientStartX = 0.dp, gradientEndX = 560.dp),
        )
    }

    /**
     * Builds a Jewel theme definition using the same logic everywhere (Light/Dark/System + disabled appearance).
     * Reads the current theme from MainAppState via DI.
     */
    @Composable
    fun buildThemeDefinition() =
        run {
            val isDarkTheme = isDarkTheme()
            val disabledValues = if (isDarkTheme) DisabledAppearanceValues.dark() else DisabledAppearanceValues.light()

            if (isDarkTheme) {
                JewelTheme.darkThemeDefinition(
                    defaultTextStyle = defaultTextStyle(),
                    disabledAppearanceValues = disabledValues,
                )
            } else {
                JewelTheme.lightThemeDefinition(
                    defaultTextStyle = defaultTextStyle(),
                    disabledAppearanceValues = disabledValues,
                )
            }
        }
}
