package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.kdroidfilter.nucleus.menu.macos.NativeKeyShortcut
import io.github.kdroidfilter.nucleus.menu.macos.NativeMenuBar
import io.github.kdroidfilter.nucleus.menu.macos.NsMenuItemImage
import io.github.kdroidfilter.nucleus.sfsymbols.SFSymbolGeneral
import io.github.kdroidfilter.nucleus.sfsymbols.SFSymbolStatus
import io.github.kdroidfilter.nucleus.sfsymbols.SFSymbolTextFormatting
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.*

@Composable
fun AppNativeMenuBar(
    mainAppState: MainAppState,
    tabsViewModel: TabsViewModel,
    settingsWindowViewModel: SettingsWindowViewModel,
    onQuit: () -> Unit,
) {
    val theme by mainAppState.theme.collectAsState()
    val themeStyle by mainAppState.themeStyle.collectAsState()
    val accentColor by mainAppState.accentColor.collectAsState()
    val showZmanim by AppSettings.showZmanimWidgetsFlow.collectAsState()
    val compactMode by AppSettings.compactModeFlow.collectAsState()
    val persistSession by AppSettings.persistSessionFlow.collectAsState()
    val closeTreeOnNewBook by AppSettings.closeBookTreeOnNewBookSelectedFlow.collectAsState()
    val tabsState by tabsViewModel.state.collectAsState()

    // Resolve string resources in composable context
    val appName = stringResource(Res.string.app_name)
    val menuFile = stringResource(Res.string.menu_file)
    val menuNewTab = stringResource(Res.string.menu_new_tab)
    val menuCloseTab = stringResource(Res.string.menu_close_tab)
    val menuCloseAllTabs = stringResource(Res.string.close_all_tabs)
    val menuQuit = stringResource(Res.string.menu_quit)
    val menuEdit = stringResource(Res.string.menu_edit)
    val menuFindInPage = stringResource(Res.string.menu_find_in_page)
    val menuView = stringResource(Res.string.menu_view)
    val menuTheme = stringResource(Res.string.menu_theme)
    val lightTheme = stringResource(Res.string.light_theme)
    val darkTheme = stringResource(Res.string.dark_theme)
    val systemTheme = stringResource(Res.string.system_theme)
    val menuThemeStyle = stringResource(Res.string.menu_theme_style)
    val themeClassic = stringResource(Res.string.settings_theme_style_classic)
    val themeIslands = stringResource(Res.string.settings_theme_style_islands)
    val menuAppearance = stringResource(Res.string.menu_appearance)
    val showZmanimLabel = stringResource(Res.string.settings_show_zmanim_widgets)
    val compactModeLabel = stringResource(Res.string.settings_compact_mode)
    val menuZoomIn = stringResource(Res.string.menu_zoom_in)
    val menuZoomOut = stringResource(Res.string.menu_zoom_out)
    val menuGoHome = stringResource(Res.string.menu_go_home)
    val menuPreferences = stringResource(Res.string.settings)
    val menuAbout = stringResource(Res.string.settings_category_about)
    val menuConditions = stringResource(Res.string.settings_category_conditions)
    val accentColorLabel = stringResource(Res.string.settings_accent_color_label)
    val accentSystem = stringResource(Res.string.accent_color_system)
    val accentDefault = stringResource(Res.string.accent_color_default)
    val accentTeal = stringResource(Res.string.accent_color_teal)
    val accentGreen = stringResource(Res.string.accent_color_green)
    val accentGold = stringResource(Res.string.accent_color_gold)
    val persistSessionLabel = stringResource(Res.string.settings_persist_session)
    val closeTreeLabel = stringResource(Res.string.close_book_tree_on_new_book)
    val menuWindow = stringResource(Res.string.menu_window)
    val menuHelp = stringResource(Res.string.menu_help)

    NativeMenuBar {
        // Application menu (first Menu call = app menu with bold app name)
        Menu(appName) {
            Item(
                text = "$menuAbout $appName",
                icon = NsMenuItemImage.SystemSymbol("info.circle"),
            ) {
                settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpenTo(SettingsDestination.About))
            }
            Item(
                text = menuConditions,
                icon = NsMenuItemImage.SystemSymbol("doc.text"),
            ) {
                settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpenTo(SettingsDestination.Conditions))
            }
            Separator()
            Item(
                text = menuPreferences,
                shortcut = NativeKeyShortcut(","),
                icon = NsMenuItemImage.SystemSymbol(SFSymbolGeneral.GEAR),
            ) {
                settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpen)
            }
            Separator()
            Item(
                text = "$menuQuit $appName",
                shortcut = NativeKeyShortcut("q"),
            ) {
                onQuit()
            }
        }

        // File menu
        Menu(menuFile) {
            Item(
                text = menuNewTab,
                icon = NsMenuItemImage.SystemSymbol(SFSymbolStatus.PLUS),
            ) {
                tabsViewModel.onEvent(TabsEvents.OnAdd)
            }
            Item(text = menuCloseTab) {
                tabsViewModel.onEvent(TabsEvents.OnClose(tabsState.selectedTabIndex))
            }
            Item(text = menuCloseAllTabs) {
                tabsViewModel.onEvent(TabsEvents.CloseAll)
            }
        }

        // Edit menu
        Menu(menuEdit) {
            Item(
                text = menuFindInPage,
                icon = NsMenuItemImage.SystemSymbol("magnifyingglass"),
            ) {
                val tabs = tabsViewModel.tabs.value
                val selectedIndex = tabsViewModel.selectedTabIndex.value
                val tabId = tabs.getOrNull(selectedIndex)?.destination?.tabId ?: return@Item
                AppSettings.toggleFindBar(tabId)
            }
        }

        // View menu
        Menu(menuView) {
            // Theme submenu
            Menu(
                text = menuTheme,
                icon = NsMenuItemImage.SystemSymbol(SFSymbolGeneral.PAINTPALETTE),
            ) {
                RadioButtonItem(lightTheme, selected = theme == IntUiThemes.Light, onClick = {
                    mainAppState.setTheme(IntUiThemes.Light)
                })
                RadioButtonItem(darkTheme, selected = theme == IntUiThemes.Dark, onClick = {
                    mainAppState.setTheme(IntUiThemes.Dark)
                })
                RadioButtonItem(systemTheme, selected = theme == IntUiThemes.System, onClick = {
                    mainAppState.setTheme(IntUiThemes.System)
                })
            }

            // Theme style submenu
            Menu(text = menuThemeStyle) {
                RadioButtonItem(themeClassic, selected = themeStyle == ThemeStyle.Classic, onClick = {
                    mainAppState.setThemeStyle(ThemeStyle.Classic)
                })
                RadioButtonItem(themeIslands, selected = themeStyle == ThemeStyle.Islands, onClick = {
                    mainAppState.setThemeStyle(ThemeStyle.Islands)
                })
            }

            // Accent color submenu
            Menu(text = accentColorLabel) {
                RadioButtonItem(accentSystem, selected = accentColor == AccentColor.System, onClick = {
                    mainAppState.setAccentColor(AccentColor.System)
                })
                RadioButtonItem(accentDefault, selected = accentColor == AccentColor.Default, onClick = {
                    mainAppState.setAccentColor(AccentColor.Default)
                })
                RadioButtonItem(accentTeal, selected = accentColor == AccentColor.Teal, onClick = {
                    mainAppState.setAccentColor(AccentColor.Teal)
                })
                RadioButtonItem(accentGreen, selected = accentColor == AccentColor.Green, onClick = {
                    mainAppState.setAccentColor(AccentColor.Green)
                })
                RadioButtonItem(accentGold, selected = accentColor == AccentColor.Gold, onClick = {
                    mainAppState.setAccentColor(AccentColor.Gold)
                })
            }

            Separator()

            // Appearance section
            SectionHeader(menuAppearance)

            CheckboxItem(
                text = showZmanimLabel,
                checked = showZmanim,
                onCheckedChange = { AppSettings.setShowZmanimWidgetsEnabled(it) },
            )

            CheckboxItem(
                text = compactModeLabel,
                checked = compactMode,
                onCheckedChange = { AppSettings.setCompactModeEnabled(it) },
            )

            CheckboxItem(
                text = persistSessionLabel,
                checked = persistSession,
                onCheckedChange = { AppSettings.setPersistSessionEnabled(it) },
            )

            CheckboxItem(
                text = closeTreeLabel,
                checked = closeTreeOnNewBook,
                onCheckedChange = { AppSettings.setCloseBookTreeOnNewBookSelected(it) },
            )

            Separator()

            // Text zoom
            Item(
                text = menuZoomIn,
                icon = NsMenuItemImage.SystemSymbol(SFSymbolTextFormatting.TEXTFORMAT_SIZE_LARGER),
            ) {
                AppSettings.increaseTextSize()
            }
            Item(
                text = menuZoomOut,
                icon = NsMenuItemImage.SystemSymbol(SFSymbolTextFormatting.TEXTFORMAT_SIZE_SMALLER),
            ) {
                AppSettings.decreaseTextSize()
            }

            Separator()

            Item(text = menuGoHome) {
                val tabs = tabsViewModel.tabs.value
                val selectedIndex = tabsViewModel.selectedTabIndex.value
                val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId
                if (currentTabId != null) {
                    tabsViewModel.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
                }
            }
        }

        // Window menu (macOS auto-adds window list)
        MenuWindow(menuWindow) {}

        // Help menu (macOS auto-adds search field)
        MenuHelp(menuHelp) {}
    }
}
