package io.github.kdroidfilter.seforimapp.core.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.seforimapp.core.MainAppState
import org.jetbrains.compose.resources.Font
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.window.styling.TitleBarStyle
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

    /**
     * Builds a Jewel theme definition using the same logic everywhere (Light/Dark/System + disabled appearance).
     * Reads the current theme directly from ThemeViewModel to avoid passing it around.
     */
    @Composable
    fun buildThemeDefinition() =
        run {
            val theme = MainAppState.theme.collectAsState().value
            val isDarkTheme =
                when (theme) {
                    IntUiThemes.Light -> false
                    IntUiThemes.Dark -> true
                    IntUiThemes.System -> isSystemInDarkMode()
                }
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

    /**
     * Chooses the appropriate TitleBarStyle based on the selected theme and system dark mode.
     * Reads the current theme directly from ThemeViewModel.
     */
    @Composable
    fun pickTitleBarStyle(): TitleBarStyle {
        val theme = MainAppState.theme.collectAsState().value
        return when (theme) {
            IntUiThemes.Light -> TitleBarStyle.lightWithLightHeader()
            IntUiThemes.Dark -> TitleBarStyle.dark()
            IntUiThemes.System -> if (isSystemInDarkMode()) TitleBarStyle.dark() else TitleBarStyle.lightWithLightHeader()
        }
    }
}
