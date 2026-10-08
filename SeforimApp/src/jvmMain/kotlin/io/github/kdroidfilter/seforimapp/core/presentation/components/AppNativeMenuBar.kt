package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.nucleusframework.menu.macos.NativeKeyShortcut
import dev.nucleusframework.menu.macos.NativeMenuBar
import dev.nucleusframework.menu.macos.NsMenuItemImage
import dev.nucleusframework.sfsymbols.SFSymbolGeneral
import dev.nucleusframework.sfsymbols.SFSymbolStatus
import dev.nucleusframework.sfsymbols.SFSymbolTextFormatting
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.annotations.BookNote
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteEntry
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteFolder
import io.github.kdroidfilter.seforimapp.core.history.VisitEntry
import io.github.kdroidfilter.seforimapp.core.history.VisitKind
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.features.home.widgets.availableTools
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.features.siddur.installedSiddur
import io.github.kdroidfilter.seforimapp.features.siddur.openSiddurTab
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.*
import seforimapp.seforimapp.generated.resources.siddur_title
import java.util.UUID

@Composable
fun AppNativeMenuBar(
    mainAppState: MainAppState,
    tabsViewModel: TabsViewModel,
    settingsWindowViewModel: SettingsWindowViewModel,
    onQuit: () -> Unit,
) {
    val appSettings = LocalAppGraph.current.appSettings
    val theme by mainAppState.theme.collectAsState()
    val accentColor by mainAppState.accentColor.collectAsState()
    val compactMode by appSettings.compactModeFlow.collectAsState()
    val persistSession by appSettings.persistSessionFlow.collectAsState()
    val closeTreeOnNewBook by appSettings.closeBookTreeOnNewBookSelectedFlow.collectAsState()
    val tabsState by tabsViewModel.state.collectAsState()

    // Chrome-like History menu data: recent visits, refreshed on every history write
    val historyStore = LocalAppGraph.current.historyStore
    val historyRevision by historyStore.revision.collectAsState()
    var recentVisits by remember { mutableStateOf<List<VisitEntry>>(emptyList()) }
    LaunchedEffect(historyRevision) {
        recentVisits = historyStore.query("", RECENT_VISITS_IN_MENU)
    }

    // Chrome-like Favorites menu data: all favorites + folders, refreshed on every write
    val favoritesStore = LocalAppGraph.current.favoritesStore
    val favoritesRevision by favoritesStore.revision.collectAsState()
    var favoriteEntries by remember { mutableStateOf<List<FavoriteEntry>>(emptyList()) }
    var favoriteFolders by remember { mutableStateOf<List<FavoriteFolder>>(emptyList()) }
    LaunchedEffect(favoritesRevision) {
        favoriteEntries = favoritesStore.query()
        favoriteFolders = favoritesStore.folders()
    }

    fun openFullHistory() {
        val tabs = tabsViewModel.state.value.tabs
        val existing = tabs.indexOfFirst { it.destination is TabsDestination.History }
        if (existing >= 0) {
            tabsViewModel.onEvent(TabsEvents.OnSelect(existing))
        } else {
            tabsViewModel.openTab(TabsDestination.History(tabId = UUID.randomUUID().toString()))
        }
    }

    fun openFavoritesTab() {
        val tabs = tabsViewModel.state.value.tabs
        val existing = tabs.indexOfFirst { it.destination is TabsDestination.Favorites }
        if (existing >= 0) {
            tabsViewModel.onEvent(TabsEvents.OnSelect(existing))
        } else {
            tabsViewModel.openTab(TabsDestination.Favorites(tabId = UUID.randomUUID().toString()))
        }
    }

    val noteStore = LocalAppGraph.current.noteStore
    val notesWritten by noteStore.notesByBook.collectAsState()
    var recentNotes by remember { mutableStateOf<List<BookNote>>(emptyList()) }
    LaunchedEffect(notesWritten) { recentNotes = noteStore.recent(MENU_RECENT_NOTES) }

    fun openNotesTab() {
        val tabs = tabsViewModel.state.value.tabs
        val existing = tabs.indexOfFirst { it.destination is TabsDestination.Notes }
        if (existing >= 0) {
            tabsViewModel.onEvent(TabsEvents.OnSelect(existing))
        } else {
            tabsViewModel.openTab(TabsDestination.Notes(tabId = UUID.randomUUID().toString()))
        }
    }

    fun openNote(bookNote: BookNote) {
        tabsViewModel.openTab(
            TabsDestination.BookContent(
                bookId = bookNote.bookId,
                tabId = UUID.randomUUID().toString(),
                lineId = bookNote.note.lineId,
                openNotes = true,
            ),
        )
    }

    fun openFavorite(entry: FavoriteEntry) {
        tabsViewModel.openTab(
            TabsDestination.BookContent(bookId = entry.bookId, tabId = UUID.randomUUID().toString(), lineId = entry.lineId),
        )
    }

    fun openVisit(entry: VisitEntry) {
        val destination =
            when (entry.kind) {
                VisitKind.BOOK ->
                    entry.bookId?.let {
                        TabsDestination.BookContent(bookId = it, tabId = UUID.randomUUID().toString(), lineId = entry.lineId)
                    }
                VisitKind.SEARCH ->
                    entry.searchQuery?.let {
                        TabsDestination.Search(
                            searchQuery = it,
                            tabId = UUID.randomUUID().toString(),
                        )
                    }
            } ?: return
        tabsViewModel.openTab(destination)
    }

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
    val menuAppearance = stringResource(Res.string.menu_appearance)
    val compactModeLabel = stringResource(Res.string.settings_compact_mode)
    val menuZoomIn = stringResource(Res.string.menu_zoom_in)
    val menuZoomOut = stringResource(Res.string.menu_zoom_out)
    val menuGoHome = stringResource(Res.string.menu_go_home)
    val menuSiddur = stringResource(Res.string.siddur_title)
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
    val menuHistory = stringResource(Res.string.history_title)
    val menuSearchPlaceholder = stringResource(Res.string.tab_search_placeholder)
    val menuShowFullHistory = stringResource(Res.string.tab_search_show_all_history)
    val menuRecentlyVisited = stringResource(Res.string.menu_recently_visited)
    val menuFavorites = stringResource(Res.string.favorites_title)
    val menuShowAllFavorites = stringResource(Res.string.favorites_show_all)
    val menuNotes = stringResource(Res.string.notes_title)
    val menuShowAllNotes = stringResource(Res.string.notes_show_all)
    val menuWindow = stringResource(Res.string.menu_window)
    val menuTools = stringResource(Res.string.menu_tools)
    val toolWindows = LocalAppGraph.current.toolWindows
    val tools = availableTools.map { it to stringResource(it.title) }
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
                text = "$menuQuit$appName",
                shortcut = NativeKeyShortcut("q"),
                icon = NsMenuItemImage.SystemSymbol("rectangle.portrait.and.arrow.right"),
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
                tabsViewModel.closeSelectedTab()
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
                appSettings.toggleFindBar(tabId)
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
                text = compactModeLabel,
                checked = compactMode,
                onCheckedChange = { appSettings.setCompactModeEnabled(it) },
            )

            CheckboxItem(
                text = persistSessionLabel,
                checked = persistSession,
                onCheckedChange = { appSettings.setPersistSessionEnabled(it) },
            )

            CheckboxItem(
                text = closeTreeLabel,
                checked = closeTreeOnNewBook,
                onCheckedChange = { appSettings.setCloseBookTreeOnNewBookSelected(it) },
            )

            Separator()

            // Text zoom
            Item(
                text = menuZoomIn,
                icon = NsMenuItemImage.SystemSymbol(SFSymbolTextFormatting.TEXTFORMAT_SIZE_LARGER),
            ) {
                appSettings.increaseTextSize()
            }
            Item(
                text = menuZoomOut,
                icon = NsMenuItemImage.SystemSymbol(SFSymbolTextFormatting.TEXTFORMAT_SIZE_SMALLER),
            ) {
                appSettings.decreaseTextSize()
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

        // History menu (Chrome-like): native search field filtering the entries, full
        // history page, and recent visits
        Menu(menuHistory) {
            SearchField(placeholder = menuSearchPlaceholder)
            Item(
                text = menuShowFullHistory,
                shortcut = NativeKeyShortcut("y"),
                icon = NsMenuItemImage.SystemSymbol("clock.arrow.circlepath"),
            ) {
                openFullHistory()
            }
            if (recentVisits.isNotEmpty()) {
                Separator()
                SectionHeader(menuRecentlyVisited)
                recentVisits.forEach { entry ->
                    Item(
                        text = entry.title.take(MENU_TITLE_MAX_LENGTH),
                        icon =
                            NsMenuItemImage.SystemSymbol(
                                if (entry.kind == VisitKind.BOOK) "book.closed" else "magnifyingglass",
                            ),
                    ) {
                        openVisit(entry)
                    }
                }
            }
        }

        // Favorites menu (Chrome bookmarks-like): native search field, full favorites page,
        // then root favorites and folders as submenus
        Menu(menuFavorites) {
            SearchField(placeholder = menuSearchPlaceholder)
            Item(
                text = menuShowAllFavorites,
                shortcut = NativeKeyShortcut("b", option = true),
                icon = NsMenuItemImage.SystemSymbol("star"),
            ) {
                openFavoritesTab()
            }
            if (favoriteEntries.isNotEmpty()) {
                Separator()
                favoriteEntries.filter { it.folderId == null }.forEach { entry ->
                    Item(
                        text = entry.title.take(MENU_TITLE_MAX_LENGTH),
                        icon = NsMenuItemImage.SystemSymbol("book.closed"),
                    ) {
                        openFavorite(entry)
                    }
                }
                favoriteFolders.forEach { folder ->
                    val folderEntries = favoriteEntries.filter { it.folderId == folder.id }
                    Menu(
                        text = folder.name.take(MENU_TITLE_MAX_LENGTH),
                        icon = NsMenuItemImage.SystemSymbol("folder"),
                    ) {
                        folderEntries.forEach { entry ->
                            Item(
                                text = entry.title.take(MENU_TITLE_MAX_LENGTH),
                                icon = NsMenuItemImage.SystemSymbol("book.closed"),
                            ) {
                                openFavorite(entry)
                            }
                        }
                    }
                }
            }
        }

        Menu(menuNotes) {
            Item(
                text = menuShowAllNotes,
                icon = NsMenuItemImage.SystemSymbol("note.text"),
            ) {
                openNotesTab()
            }
            if (recentNotes.isNotEmpty()) {
                Separator()
                recentNotes.forEach { bookNote ->
                    Item(
                        text = bookNote.note.note.take(MENU_TITLE_MAX_LENGTH),
                        icon = NsMenuItemImage.SystemSymbol("square.and.pencil"),
                    ) {
                        openNote(bookNote)
                    }
                }
            }
        }

        Menu(menuTools) {
            if (installedSiddur != null) {
                Item(text = menuSiddur, icon = NsMenuItemImage.SystemSymbol("text.book.closed")) {
                    openSiddurTab(tabsViewModel)
                }
            }
            tools.forEach { (tool, title) ->
                Item(text = title, icon = tool.toolSymbol?.let(NsMenuItemImage::SystemSymbol)) { toolWindows.open(tool) }
            }
        }

        // Window menu (macOS auto-adds window list)
        MenuWindow(menuWindow) {}

        // Help menu (macOS auto-adds search field)
        MenuHelp(menuHelp) {}
    }
}

private const val RECENT_VISITS_IN_MENU = 40
private const val MENU_TITLE_MAX_LENGTH = 50
private const val MENU_RECENT_NOTES = 10
