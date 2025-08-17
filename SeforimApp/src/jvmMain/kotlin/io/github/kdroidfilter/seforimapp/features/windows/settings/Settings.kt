package io.github.kdroidfilter.seforimapp.features.windows.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeViewModel
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource


import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.koin.compose.viewmodel.koinViewModel
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book
import seforimapp.seforimapp.generated.resources.notoserifhebrew
import seforimapp.seforimapp.generated.resources.settings

@Composable
fun Settings(onClose: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state = collectSettingsState(viewModel)
    SettingsView(state, onClose, onToggleCloseTree = { value ->
        viewModel.onEvent(SettingsEvents.SetCloseBookTreeOnNewBookSelected(value))
    })
}

@Composable
private fun SettingsView(state: SettingsState, onClose: () -> Unit, onToggleCloseTree: (Boolean) -> Unit) {
    val themeViewModel = ThemeViewModel
    val theme = themeViewModel.theme.collectAsState().value
    val isSystemInDarkMode = isSystemInDarkMode()

    val themeDefinition = when (theme) {
        IntUiThemes.Light -> JewelTheme.lightThemeDefinition(
            defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)))
        )

        IntUiThemes.Dark -> JewelTheme.darkThemeDefinition(
            defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)))
        )

        IntUiThemes.System ->
            if (isSystemInDarkMode) {
                JewelTheme.darkThemeDefinition(
                    defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)))
                )
            } else {
                JewelTheme.lightThemeDefinition(
                    defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)))
                )
            }
    }


    IntUiTheme(
        theme = themeDefinition,
        styling = ComponentStyling.default()
            .decoratedWindow(
                titleBarStyle = when (theme) {
                    IntUiThemes.Light -> TitleBarStyle.lightWithLightHeader()
                    IntUiThemes.Dark -> TitleBarStyle.dark()
                    IntUiThemes.System ->
                        if (isSystemInDarkMode) {
                            TitleBarStyle.dark()
                        } else {
                            TitleBarStyle.lightWithLightHeader()
                        }
                },
            )
    ) {
        DialogWindow(
            onCloseRequest = onClose,
            state = rememberDialogState(size = DpSize(800.dp, 600.dp)),
            title = stringResource(Res.string.settings),
        ) {
            window.setWindowsAdaptiveTitleBar()
            Column(
                modifier =
                    Modifier.trackActivation().fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Checkbox(
                        checked = state.closedAutomaticallyBookTreePaneOnNewBookSelected,
                        onCheckedChange = { onToggleCloseTree(it) }
                    )
                    Text(
                        text = stringResource(Res.string.close_book_tree_on_new_book),
                    )
                }
            }
        }
    }
}
