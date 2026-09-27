@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.kdroid.gematria.converter.toHebrewNumeral
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import dev.nucleusframework.application.Tab
import dev.nucleusframework.application.TabWindows
import dev.nucleusframework.energymanager.EnergyManager
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.tao.DockLayout
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.JoinSatelliteWorkspace
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.TabWindowGroup
import dev.nucleusframework.window.tao.TabWorkspace
import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.presentation.components.MainTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsContent
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabBookViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalIsTouchMode
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.detectTouchMode
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.bookcontent.handleBookShortcut
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.EndVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components.StartVerticalBar
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.BookBreadcrumb
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.isBookTextShown
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.DockedPaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.LineSideOrder
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.NavigationLayeredSides
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.PaneCard
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.ReaderSplitter
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes.WindowPanes
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopSession
import io.github.kdroidfilter.seforimapp.framework.desktop.DockSizes
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.desktop_default_name
import seforimapp.seforimapp.generated.resources.home_tab_with_app
import java.util.UUID

/**
 * Every window of one open virtual desktop. The desktop's `TabWorkspace` owns the windows: one per
 * group of tabs, opened by a tear-off and closed with its last tab. Each tab is declared once here;
 * each window gets its tab strip in the title bar, and under it the reader's own chrome — activity
 * bars and a dock of pane satellites around the selected tab's text.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopWindows(
    session: DesktopSession,
    settingsWindowViewModel: SettingsWindowViewModel,
    windowViewModelOwner: ViewModelStoreOwner,
) {
    val desktopManager = LocalAppGraph.current.desktopManager
    TabWindows(
        workspace = session.workspace,
        // Composed inside the window wrapper, whose locals they read.
        strip = { if (session.windows[group.id] != null) TabsView() },
        titleBar = { strip -> MainTitleBar(tabs = strip) },
        windowWrapper = { content ->
            MainAppWindow(session, settingsWindowViewModel, windowViewModelOwner, content)
        },
        windowBodyWrapper = { body ->
            val openWindow =
                session.workspace
                    .groupOf(nucleusWindow.unsafe.taoWindow)
                    ?.id
                    ?.let(session.windows::get)
            if (openWindow == null) {
                body()
            } else {
                WindowLocals(openWindow, windowViewModelOwner) { WindowBody(openWindow, body) }
            }
        },
        onLastWindowClosed = { desktopManager.onSessionEmptied(session) },
    )

    val homeLabel = stringResource(Res.string.home_tab_with_app, stringResource(Res.string.app_name))
    for (item in session.tabs) {
        val tabId = item.destination.tabId
        key(tabId) {
            Tab(session.workspace, id = tabId, title = tabLabel(item, homeLabel), group = session.initialGroupOf(tabId)) {}
            LaunchedEffect(tabId) { session.onDeclared(tabId) }
            // Closing a tab is a workspace call (×, window close); a tab still declared once the
            // workspace dropped it would be registered again and hosted nowhere.
            val closed = session.workspace.tab(tabId) == null
            LaunchedEffect(closed) { if (closed) desktopManager.onTabClosed(session, tabId) }
        }
    }

    for (group in rememberTabGroups(session.workspace)) {
        key(group.id) { WindowPanes(session, group) }
    }
}

private fun tabLabel(
    item: TabItem,
    homeLabel: String,
): String = item.title.ifEmpty { homeLabel }

/**
 * The tab windows, mirrored out of the workspace through an effect: the groups are created by
 * `Tab`, declared after this list is read, and Compose drops an invalidation aimed at a scope it
 * has just composed.
 */
@Composable
private fun rememberTabGroups(workspace: TabWorkspace): List<TabWindowGroup> {
    var groups by remember(workspace) { mutableStateOf(workspace.groups.toList()) }
    LaunchedEffect(workspace) {
        snapshotFlow { workspace.groups.toList() }.collect { groups = it }
    }
    return groups
}

/**
 * One main window: window-scoped state ([OpenWindow]), shortcuts, focus tracking, the settings
 * dialog modal to this window, and the window's own close policy (see
 * [io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager.onWindowCloseRequest]).
 */
@Composable
private fun NucleusDecoratedWindowScope.MainAppWindow(
    session: DesktopSession,
    settingsWindowViewModel: SettingsWindowViewModel,
    windowViewModelOwner: ViewModelStoreOwner,
    content: @Composable () -> Unit,
) {
    val desktopManager = LocalAppGraph.current.desktopManager
    val taoWindow = nucleusWindow.unsafe.taoWindow
    // The workspace attaches the native window to its group right after the first frame.
    val group = session.workspace.groupOf(taoWindow) ?: return
    val openWindow = remember(group.id) { desktopManager.attachWindow(session, group.id) }
    val nucleusWin = nucleusWindow
    val icon = if (PlatformInfo.isMacOS) null else painterResource(Res.drawable.AppIcon)

    DisposableEffect(openWindow, nucleusWin) {
        openWindow.nucleusWindow = nucleusWin
        onDispose { desktopManager.detachWindow(openWindow) }
    }
    // Closing is the app's policy, not the workspace's (which would discard the window's tabs):
    // the last window quits with the session intact, a desktop's last window puts it to sleep.
    DisposableEffect(taoWindow, openWindow) {
        taoWindow?.onCloseRequested { desktopManager.onWindowCloseRequest(openWindow) }
        onDispose {}
    }
    LaunchedEffect(nucleusWin) {
        nucleusWin.setMinimumSize(DpSize(600.dp, 300.dp))
        icon?.let(nucleusWin::setIcon)
        if (session.maximizeOnOpen.remove(group.id)) nucleusWin.setMaximized(true)
        nucleusWin.focusFlow.collect { focused ->
            if (focused) desktopManager.onWindowFocused(openWindow.id)
        }
    }
    JoinSatelliteWorkspace(session.panesOf(group.id, navigation = true))
    JoinSatelliteWorkspace(session.panesOf(group.id, navigation = false))

    val isDark = JewelTheme.isDark
    WindowBackground(canvasBackground())
    WindowAppearance(if (isDark) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)

    WindowLocals(openWindow, windowViewModelOwner) {
        // Settings dialog, composed inside the window it was opened from so it picks up this
        // window's modal counter and native transient-for relationship — modal to this window
        // only, the others stay usable.
        val settingsDialogState by settingsWindowViewModel.state.collectAsState()
        if (settingsDialogState.isVisible && settingsDialogState.ownerWindowId == openWindow.id) {
            with(LocalNucleusApplicationScope.current) {
                SettingsWindow(
                    onClose = { settingsWindowViewModel.onEvent(SettingsWindowEvents.OnClose) },
                    initialDestination = settingsDialogState.initialDestination,
                )
            }
        }

        // Keep the screen awake while a book is open in the current tab and this window is
        // focused — opt-out via the General settings (enabled by default).
        val keepAwakeEnabled by AppSettings.keepScreenAwakeOnBookFlow.collectAsState()
        val selectedDestination = group.selectedId?.let(session::item)?.destination
        val shouldKeepScreenAwake = keepAwakeEnabled && state.isActive && selectedDestination is TabsDestination.BookContent
        LaunchedEffect(shouldKeepScreenAwake) {
            if (shouldKeepScreenAwake) EnergyManager.keepScreenAwake() else EnergyManager.releaseScreenAwake()
        }

        val nextDesktopName = nextDesktopName()
        // Track whether the user is interacting by touch so hover-gated controls (e.g. pane close
        // buttons) stay reachable; published app-wide via LocalIsTouchMode.
        var isTouchMode by remember { mutableStateOf(false) }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .detectTouchMode { isTouchMode = it }
                    .onPreviewKeyEvent { keyEvent ->
                        handleWindowShortcut(keyEvent, openWindow, desktopManager, settingsWindowViewModel, nextDesktopName)
                    },
        ) {
            CompositionLocalProvider(LocalIsTouchMode provides isTouchMode) { content() }
        }
    }
}

/** The window-scoped locals; provided again under the tab strip, where the workspace rebinds the app's. */
@Composable
private fun WindowLocals(
    openWindow: OpenWindow,
    windowViewModelOwner: ViewModelStoreOwner,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalOpenWindow provides openWindow,
        LocalWindowViewModelStoreOwner provides windowViewModelOwner,
        LocalViewModelStoreOwner provides windowViewModelOwner,
        content = content,
    )
}

/**
 * Under the tab strip, laid out as the reader's split panes were: the activity bars around the
 * outer dock (book tree, contents, notes — full height on the start side), whose centre is the
 * inner dock (links, commentaries, sources) around the selected tab's text, with the book's
 * breadcrumb under it. Every pane is a satellite drawing the selected tab.
 */
@Composable
private fun WindowBody(
    openWindow: OpenWindow,
    body: @Composable () -> Unit,
) {
    val session = openWindow.session
    val selected = session.group(openWindow.id)?.selectedId?.let(session::item)
    val destination = selected?.destination
    val readerTab =
        destination is TabsDestination.Home || destination is TabsDestination.BookContent || destination is TabsDestination.Search
    val lineWorkspace = session.panesOf(openWindow.id, navigation = false)
    val bottomOpen =
        lineWorkspace.satellites.any { entry ->
            entry.isOpen && (entry.placement as? SatellitePlacement.Docked)?.side == DockSide.Bottom
        }

    val density = LocalDensity.current
    var outerWidth by remember { mutableFloatStateOf(0f) }
    var innerSize by remember { mutableStateOf(IntSize.Zero) }
    // The panes are sized as fractions of these, as the split panes were (see PaneSizeSync).
    LaunchedEffect(outerWidth, innerSize, density) {
        if (outerWidth > 0f && innerSize.width > 0) {
            session.dockSizes[openWindow.id] =
                DockSizes(
                    outerWidth = outerWidth / density.density,
                    innerWidth = innerSize.width / density.density,
                    innerHeight = innerSize.height / density.density,
                )
        }
    }

    Row(Modifier.fillMaxSize().background(canvasBackground())) {
        if (readerTab && destination != null) {
            key(destination.tabId) { ReaderBar(session, destination, start = true) }
        }
        DockLayout(
            workspace = session.panesOf(openWindow.id, navigation = true),
            modifier = Modifier.weight(1f).fillMaxHeight().onSizeChanged { outerWidth = it.width.toFloat() },
            layeredSides = NavigationLayeredSides,
            splitter = { ReaderSplitter() },
            panel = { panel -> PaneCard { panel() } },
        ) {
            Column(Modifier.fillMaxSize()) {
                DockLayout(
                    workspace = lineWorkspace,
                    modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { innerSize = it },
                    sideOrder = LineSideOrder,
                    splitter = { ReaderSplitter() },
                    panel = { panel -> DockedPaneCard(bottomOpen) { panel() } },
                ) {
                    Box(Modifier.fillMaxSize()) {
                        TabsContent()
                        // The workspace's own tab body: empty, the tabs are drawn (and kept alive) above.
                        body()
                    }
                }
                if (readerTab && destination != null) {
                    key(destination.tabId) { ReaderBreadcrumb(session, destination) }
                }
            }
        }
        if (readerTab && destination != null) {
            key(destination.tabId) { ReaderBar(session, destination, start = false) }
        }
    }
}

@Composable
private fun ReaderBreadcrumb(
    session: DesktopSession,
    destination: TabsDestination,
) {
    val viewModel = tabBookViewModel(session.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    if (isBookTextShown(uiState)) BookBreadcrumb(uiState = uiState, onEvent = viewModel::onEvent)
}

/** One of the two activity bars of a reader tab (start: navigation panes; end: text and line panes). */
@Composable
private fun ReaderBar(
    session: DesktopSession,
    destination: TabsDestination,
    start: Boolean,
) {
    val viewModel = tabBookViewModel(session.ownerOf(destination.tabId), destination)
    val uiState by viewModel.uiState.collectAsState()
    val showDiacritics by viewModel.showDiacritics.collectAsState()
    Box(Modifier.fillMaxHeight().onPreviewKeyEvent { handleBookShortcut(it, viewModel::onEvent) }) {
        if (start) {
            StartVerticalBar(uiState = uiState, onEvent = viewModel::onEvent)
        } else if (uiState.navigation.selectedBook != null || destination is TabsDestination.Search) {
            EndVerticalBar(uiState = uiState, onEvent = viewModel::onEvent, showDiacritics = showDiacritics)
        }
    }
}

@Composable
private fun nextDesktopName(): String {
    val desktops by LocalAppGraph.current.desktopManager.desktops
        .collectAsState()
    return stringResource(
        Res.string.desktop_default_name,
        remember(desktops.size) { (desktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳" },
    )
}

@Composable
private fun canvasBackground() =
    if (ThemeUtils.isIslandsStyle()) JewelTheme.globalColors.toolwindowBackground else JewelTheme.globalColors.panelBackground

/** Window-level shortcuts (tabs, desktops, history, favorites, settings, window state). */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun handleWindowShortcut(
    keyEvent: KeyEvent,
    openWindow: OpenWindow,
    desktopManager: DesktopManager,
    settingsWindowViewModel: SettingsWindowViewModel,
    nextDesktopName: String,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    val tabsVm = openWindow.tabsViewModel
    val state = tabsVm.state.value
    val tabs = state.tabs
    val selectedIndex = state.selectedTabIndex
    val window = openWindow.nucleusWindow
    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
    val isMac = PlatformInfo.isMacOS

    fun openSingleton(
        matches: (TabsDestination) -> Boolean,
        create: () -> TabsDestination,
    ) {
        val existing = tabs.indexOfFirst { matches(it.destination) }
        if (existing >= 0) tabsVm.onEvent(TabsEvents.OnSelect(existing)) else tabsVm.openTab(create())
    }

    when {
        isCtrlOrCmd && keyEvent.key == Key.W -> tabsVm.onEvent(TabsEvents.OnClose(selectedIndex))
        isCtrlOrCmd && keyEvent.key == Key.Tab -> {
            val count = tabs.size
            if (count > 0) {
                val direction = if (keyEvent.isShiftPressed) -1 else 1
                tabsVm.onEvent(TabsEvents.OnSelect((selectedIndex + direction + count) % count))
            }
        }
        isCtrlOrCmd && keyEvent.isShiftPressed && keyEvent.key == Key.A ->
            openWindow.tabSearchVisible.value = !openWindow.tabSearchVisible.value
        // Cmd+Y (macOS) / Ctrl+H (others) => history page (Chrome-like)
        (isMac && keyEvent.isMetaPressed && !keyEvent.isShiftPressed && keyEvent.key == Key.Y) ||
            (!isMac && keyEvent.isCtrlPressed && !keyEvent.isShiftPressed && keyEvent.key == Key.H) ->
            openSingleton({ it is TabsDestination.History }) { TabsDestination.History(tabId = UUID.randomUUID().toString()) }
        // Cmd+Alt+B (macOS) / Ctrl+Shift+O (others) => favorites page (Chrome-like)
        (isMac && keyEvent.isMetaPressed && keyEvent.isAltPressed && keyEvent.key == Key.B) ||
            (!isMac && keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key == Key.O) ->
            openSingleton({ it is TabsDestination.Favorites }) { TabsDestination.Favorites(tabId = UUID.randomUUID().toString()) }
        isCtrlOrCmd && keyEvent.key == Key.T -> tabsVm.onEvent(TabsEvents.OnAdd)
        // Alt + Home (Windows) or Cmd + Shift + H (macOS) => go Home on current tab
        (keyEvent.isAltPressed && keyEvent.key == Key.SystemHome) ||
            (keyEvent.isMetaPressed && keyEvent.isShiftPressed && keyEvent.key == Key.H) -> {
            val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId ?: return false
            tabsVm.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
        }
        isCtrlOrCmd && keyEvent.key == Key.Comma -> settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpen)
        isCtrlOrCmd && keyEvent.isAltPressed && keyEvent.key == Key.DirectionRight -> desktopManager.switchToNext(openWindow.id)
        isCtrlOrCmd && keyEvent.isAltPressed && keyEvent.key == Key.DirectionLeft -> desktopManager.switchToPrevious(openWindow.id)
        isCtrlOrCmd && keyEvent.isAltPressed && keyEvent.key == Key.N -> desktopManager.createDesktop(openWindow.id, nextDesktopName)
        isCtrlOrCmd && keyEvent.key == Key.N -> desktopManager.createDesktopInNewWindow(nextDesktopName)
        isMac && keyEvent.isMetaPressed && keyEvent.key == Key.M -> window?.setMinimized(true)
        !isMac && keyEvent.key == Key.F11 -> window?.let { it.setFullscreen(!it.isFullscreen) }
        else ->
            return processKeyShortcuts(
                keyEvent = keyEvent,
                onNavigateTo = { },
                tabId = tabs.getOrNull(selectedIndex)?.destination?.tabId ?: "",
            )
    }
    return true
}
