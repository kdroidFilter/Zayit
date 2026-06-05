package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.framework.update.showTitleBarIcon
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

// AllIconsKeys.MeetNewUi.SystemTheme was removed in Jewel 0.37 and the SVG
// dropped from IntelliJ Platform icons 262, so the asset is shipped locally.
private object SystemThemeIconAnchor

private val SystemTheme = PathIconKey("icons/system_theme.svg", SystemThemeIconAnchor::class.java)

@Composable
fun TitleBarActionsButtonsView() {
    val appGraph = LocalAppGraph.current
    val mainAppState = appGraph.mainAppState
    val theme = mainAppState.theme.collectAsState().value

    // Update badge: shown only for PROMPT updates (MINOR/MAJOR any OS, or Linux PATCH).
    // Silent Win/Mac PATCH updates apply on close and never surface here.
    val updateState =
        appGraph.appUpdateService.state
            .collectAsState()
            .value

    // Use ViewModel-driven settings window visibility to respect MVVM conventions
    val settingsViewModel: SettingsWindowViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val settingsState = settingsViewModel.state.collectAsState().value

    // Access app graph outside of callbacks to avoid reading CompositionLocals in non-composable contexts
    val tabsViewModel: TabsViewModel = appGraph.tabsViewModel
    val tabsState = tabsViewModel.state.collectAsState().value
    val currentTab = tabsState.tabs.getOrNull(tabsState.selectedTabIndex)
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

    if (updateState.showTitleBarIcon) {
        TitleBarActionButton(
            key = AllIconsKeys.Ide.Notification.IdeUpdate,
            contentDescription = stringResource(Res.string.update_titlebar_tooltip),
            onClick = { appGraph.appUpdateService.openDialog() },
            tooltipText = stringResource(Res.string.update_titlebar_tooltip),
        )
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
    // On macOS, theme toggle and settings are handled by the native menu bar
    if (!PlatformInfo.isMacOS) {
        TitleBarActionButton(
            key =
                when (theme) {
                    IntUiThemes.Light -> AllIconsKeys.MeetNewUi.LightTheme
                    IntUiThemes.Dark -> AllIconsKeys.MeetNewUi.DarkTheme
                    IntUiThemes.System -> SystemTheme
                },
            contentDescription = iconDescription,
            onClick = {
                mainAppState.setTheme(
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
    }

    // SettingsWindow is hoisted to main.kt where NucleusApplicationScope is available
    // (JewelDecoratedDialog is an extension on NucleusApplicationScope in Nucleus 2.0).
}
