package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforim.tabs.TabStateManager
import io.github.kdroidfilter.seforimapp.features.bookcontent.state.StateKeys
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

@Composable
fun TitleBarActionsButtonsView() {
    val themeViewModel = MainAppState
    val theme = themeViewModel.theme.collectAsState().value

    // Use ViewModel-driven settings window visibility to respect MVVM conventions
    val settingsViewModel: SettingsWindowViewModel = LocalAppGraph.current.settingsWindowViewModel
    val settingsState = settingsViewModel.state.collectAsState().value

    // Access app graph outside of callbacks to avoid reading CompositionLocals in non-composable contexts
    val appGraph = LocalAppGraph.current

    val iconDescription = when (theme) {
        IntUiThemes.Light -> stringResource(Res.string.light_theme)
        IntUiThemes.Dark -> stringResource(Res.string.dark_theme)
        IntUiThemes.System -> stringResource(Res.string.system_theme)
    }
    val iconToolTipText = when (theme) {
        IntUiThemes.Light -> stringResource(Res.string.switch_to_dark_theme)
        IntUiThemes.Dark -> stringResource(Res.string.switch_to_system_theme)
        IntUiThemes.System -> stringResource(Res.string.switch_to_light_theme)
    }

    TitleBarActionButton(
        key = AllIconsKeys.Nodes.HomeFolder,
        contentDescription = stringResource(Res.string.home),
        onClick = {
            // Replace current tab destination with Home, preserving tabId
            val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
            val tabStateManager: TabStateManager = appGraph.tabStateManager

            val tabs = tabsViewModel.tabs.value
            val selectedIndex = tabsViewModel.selectedTabIndex.value
            val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId

            if (currentTabId != null) {
                // Replace current tab with a fresh tabId, like opening a new tab in place
                tabsViewModel.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
            }
        },
        tooltipText = stringResource(Res.string.home_tooltip),
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
                when (theme) {
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
            settingsViewModel.onEvent(SettingsWindowEvents.onOpen)
        },
        tooltipText = stringResource(Res.string.settings_tooltip),
    )

    if (settingsState.isVisible) {
        SettingsWindow(onClose = { settingsViewModel.onEvent(SettingsWindowEvents.onClose) })
    }
}
