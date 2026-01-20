package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

@Composable
fun TitleBarActionsButtonsView() {
    val themeViewModel = MainAppState
    val theme = themeViewModel.theme.collectAsState().value

    // Use ViewModel-driven settings window visibility to respect MVVM conventions
    val settingsViewModel: SettingsWindowViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val settingsState = settingsViewModel.state.collectAsState().value

    // Access app graph outside of callbacks to avoid reading CompositionLocals in non-composable contexts
    val appGraph = LocalAppGraph.current
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
    val tabs = tabsViewModel.tabs.collectAsState().value
    val selectedTabIndex = tabsViewModel.selectedTabIndex.collectAsState().value
    val currentTab = tabs.getOrNull(selectedTabIndex)
    val findEnabled =
        when (val dest = currentTab?.destination) {
            is TabsDestination.Search -> true
            is TabsDestination.BookContent -> {
                (
                    appGraph.tabPersistedStateStore
                        .get(dest.tabId)
                        ?.bookContent
                        ?.selectedBookId ?: -1L
                ) > 0L
            }
            else -> false
        }

    val iconDescription =
        when (theme) {
            IntUiThemes.Light -> stringResource(Res.string.light_theme)
            IntUiThemes.Dark -> stringResource(Res.string.dark_theme)
            IntUiThemes.System -> stringResource(Res.string.system_theme)
        }
    val iconToolTipText =
        when (theme) {
            IntUiThemes.Light -> stringResource(Res.string.switch_to_dark_theme)
            IntUiThemes.Dark -> stringResource(Res.string.switch_to_system_theme)
            IntUiThemes.System -> stringResource(Res.string.switch_to_light_theme)
        }

    val homeShortcutHint =
        if (PlatformInfo.isMacOS) {
            stringResource(Res.string.shortcut_home_mac)
        } else {
            stringResource(Res.string.shortcut_home_windows)
        }

    val findShortcutHint =
        if (PlatformInfo.isMacOS) {
            stringResource(Res.string.shortcut_find_mac)
        } else {
            stringResource(Res.string.shortcut_find_windows)
        }

    val settingsShortcutHint =
        if (PlatformInfo.isMacOS) {
            stringResource(Res.string.shortcut_settings_mac)
        } else {
            stringResource(Res.string.shortcut_settings_windows)
        }

    TitleBarActionButton(
        key = AllIconsKeys.Nodes.HomeFolder,
        contentDescription = stringResource(Res.string.home),
        onClick = {
            // Replace current tab destination with Home, preserving tabId
            val tabsViewModel: TabsViewModel = appGraph.tabsViewModel

            val tabs = tabsViewModel.tabs.value
            val selectedIndex = tabsViewModel.selectedTabIndex.value
            val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId

            if (currentTabId != null) {
                // Replace current tab with a fresh tabId, like opening a new tab in place
                tabsViewModel.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
            }
        },
        tooltipText = stringResource(Res.string.home_tooltip),
        shortcutHint = homeShortcutHint,
    )
    TitleBarActionButton(
        key = AllIconsKeys.Actions.Find,
        contentDescription = stringResource(Res.string.find),
        onClick = {
            val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
            val tabs = tabsViewModel.tabs.value
            val selectedIndex = tabsViewModel.selectedTabIndex.value
            val tabId = tabs.getOrNull(selectedIndex)?.destination?.tabId ?: return@TitleBarActionButton
            // Toggle the Find-in-Page bar for the current tab only
            val isOpen = AppSettings.findBarOpenFlow(tabId).value
            if (isOpen) AppSettings.closeFindBar(tabId) else AppSettings.openFindBar(tabId)
        },
        tooltipText =
            if (findEnabled) {
                stringResource(
                    Res.string.search_in_page_tooltip,
                )
            } else {
                stringResource(Res.string.find_disabled_tooltip)
            },
        shortcutHint = findShortcutHint,
        enabled = findEnabled,
    )
    TitleBarActionButton(
        key =
            when (theme) {
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
                },
            )
        },
        tooltipText = iconToolTipText,
    )
    TitleBarActionButton(
        key = AllIconsKeys.General.Settings,
        contentDescription = stringResource(Res.string.settings),
        onClick = {
            settingsViewModel.onEvent(SettingsWindowEvents.OnOpen)
        },
        tooltipText = stringResource(Res.string.settings_tooltip),
        shortcutHint = settingsShortcutHint,
    )

    if (settingsState.isVisible) {
        SettingsWindow(onClose = { settingsViewModel.onEvent(SettingsWindowEvents.OnClose) })
    }
}
