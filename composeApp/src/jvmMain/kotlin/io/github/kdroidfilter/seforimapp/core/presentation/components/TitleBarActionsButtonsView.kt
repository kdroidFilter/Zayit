package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.dark_theme
import seforimapp.composeapp.generated.resources.find
import seforimapp.composeapp.generated.resources.find_tooltip
import seforimapp.composeapp.generated.resources.info
import seforimapp.composeapp.generated.resources.info_tooltip
import seforimapp.composeapp.generated.resources.light_theme
import seforimapp.composeapp.generated.resources.settings
import seforimapp.composeapp.generated.resources.settings_tooltip
import seforimapp.composeapp.generated.resources.switch_to_dark_theme
import seforimapp.composeapp.generated.resources.switch_to_light_theme
import seforimapp.composeapp.generated.resources.switch_to_system_theme
import seforimapp.composeapp.generated.resources.system_theme

@Composable
fun TitleBarActionsButtonsView() {
    val themeViewModel = ThemeViewModel
    val theme = themeViewModel.theme.collectAsState().value

    val iconDescription = when(theme) {
        IntUiThemes.Light -> stringResource(Res.string.light_theme)
        IntUiThemes.Dark -> stringResource(Res.string.dark_theme)
        IntUiThemes.System -> stringResource(Res.string.system_theme)
    }
    val iconToolTipText = when(theme) {
        IntUiThemes.Light -> stringResource(Res.string.switch_to_dark_theme)
        IntUiThemes.Dark -> stringResource(Res.string.switch_to_system_theme)
        IntUiThemes.System -> stringResource(Res.string.switch_to_light_theme)
    }
    TitleBarActionButton(
        key = AllIconsKeys.Actions.Find,
        contentDescription = stringResource(Res.string.find),
        onClick = {
            //TODO
        },
        tooltipText = stringResource(Res.string.find_tooltip),
    )
    TitleBarActionButton(
        key = when (theme) {
            IntUiThemes.Light -> AllIconsKeys.MeetNewUi.LightTheme
            IntUiThemes.Dark -> AllIconsKeys.MeetNewUi.DarkTheme
            IntUiThemes.System -> AllIconsKeys.MeetNewUi.SystemTheme
        },
        contentDescription = iconDescription,
        onClick = {
            themeViewModel.setTheme(
                when(theme) {
                    IntUiThemes.Light -> IntUiThemes.Dark
                    IntUiThemes.Dark -> IntUiThemes.System
                    IntUiThemes.System -> IntUiThemes.Light
                }
            )
        },
        tooltipText = iconToolTipText,
    )
    TitleBarActionButton(
        key = AllIconsKeys.General.ShowInfos,
        contentDescription = stringResource(Res.string.info),
        onClick = {
            //TODO
        },
        tooltipText = stringResource(Res.string.info_tooltip),
    )
    TitleBarActionButton(
        key = AllIconsKeys.General.Settings,
        contentDescription = stringResource(Res.string.settings),
        onClick = {
            //TODO
        },
        tooltipText = stringResource(Res.string.settings_tooltip),
    )
}