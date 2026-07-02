package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.kdroid.gematria.converter.toHebrewNumeral
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.energymanager.EnergyManager
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.presentation.components.MainTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsContent
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalIsTouchMode
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.detectTouchMode
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.desktop_default_name
import seforimapp.seforimapp.generated.resources.home
import seforimapp.seforimapp.generated.resources.search_results_tab_title

/**
 * One main application window. The window displays one virtual desktop's tabs; several windows can
 * be open at once (each on its own desktop, or several windows of the same desktop). Everything
 * window-scoped comes from [openWindow]; app-wide dialogs (settings, updates) live in main.kt.
 */
@Composable
fun NucleusApplicationScope.MainAppWindow(
    openWindow: OpenWindow,
    settingsWindowViewModel: SettingsWindowViewModel,
    windowViewModelOwner: ViewModelStoreOwner,
    onQuit: () -> Unit,
) {
    val appGraph = LocalAppGraph.current
    val desktopMgr = appGraph.desktopManager
    val tabsVm = openWindow.tabsViewModel
    val windowState = openWindow.windowState

    val tabsState by tabsVm.state.collectAsState()
    val tabs = tabsState.tabs
    val selectedIndex = tabsState.selectedTabIndex
    val allDesktops by desktopMgr.desktops.collectAsState()
    val currentDesktopId by openWindow.desktopId.collectAsState()
    val currentDesktopName = allDesktops.find { it.id == currentDesktopId }?.name
    val nextDesktopName =
        stringResource(
            Res.string.desktop_default_name,
            remember(allDesktops.size) {
                (allDesktops.size + 1).toHebrewNumeral(includeGeresh = false) + "׳"
            },
        )
    val appTitle = stringResource(Res.string.app_name)
    val selectedTab = tabs.getOrNull(selectedIndex)
    val rawTitle = selectedTab?.title.orEmpty()
    val tabType = selectedTab?.tabType
    val formattedTabTitle =
        when {
            rawTitle.isEmpty() -> stringResource(Res.string.home)
            tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, rawTitle)
            else -> rawTitle
        }
    val windowTitle =
        buildString {
            append(appTitle)
            if (allDesktops.size > 1 && currentDesktopName != null) {
                append(" - [$currentDesktopName]")
            }
            if (formattedTabTitle.isNotBlank()) {
                append(" - $formattedTabTitle")
            }
        }

    JewelDecoratedWindow(
        onCloseRequest = {
            if (desktopMgr.windows.value.size <= 1) {
                // Last window: quit path (persist session, apply pending updates, exit).
                onQuit()
            } else {
                desktopMgr.closeWindow(openWindow.id)
                SessionManager.saveIfEnabled(appGraph)
            }
        },
        title = windowTitle,
        icon = if (PlatformInfo.isMacOS) null else painterResource(Res.drawable.AppIcon),
        state = windowState,
        minimumSize = DpSize(600.dp, 300.dp),
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                // Read fresh state to avoid stale captures in cached lambda
                val currentState = tabsVm.state.value
                val currentTabs = currentState.tabs
                val currentIndex = currentState.selectedTabIndex
                val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                if (isCtrlOrCmd && keyEvent.key == Key.T) {
                    tabsVm.onEvent(TabsEvents.OnAdd)
                    true
                } else if (isCtrlOrCmd && keyEvent.key == Key.W) {
                    tabsVm.onEvent(TabsEvents.OnClose(currentIndex))
                    true
                } else if (isCtrlOrCmd && keyEvent.key == Key.Tab) {
                    val count = currentTabs.size
                    if (count > 0) {
                        val direction = if (keyEvent.isShiftPressed) -1 else 1
                        val newIndex = (currentIndex + direction + count) % count
                        tabsVm.onEvent(TabsEvents.OnSelect(newIndex))
                    }
                    true
                } else if ((keyEvent.isAltPressed && keyEvent.key == Key.Home) ||
                    (keyEvent.isMetaPressed && keyEvent.isShiftPressed && keyEvent.key == Key.H)
                ) {
                    val currentTabId = currentTabs.getOrNull(currentIndex)?.destination?.tabId
                    if (currentTabId != null) {
                        tabsVm.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
                        true
                    } else {
                        false
                    }
                } else if (isCtrlOrCmd && keyEvent.key == Key.Comma) {
                    settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpen)
                    true
                } else if (isCtrlOrCmd && !keyEvent.isAltPressed && keyEvent.key == Key.N) {
                    desktopMgr.createDesktopInNewWindow(nextDesktopName)
                    true
                } else if (PlatformInfo.isMacOS && keyEvent.isMetaPressed && keyEvent.key == Key.M) {
                    windowState.isMinimized = true
                    true
                } else if (!PlatformInfo.isMacOS && keyEvent.key == Key.F11) {
                    windowState.placement =
                        if (windowState.placement == WindowPlacement.Fullscreen) {
                            WindowPlacement.Maximized
                        } else {
                            WindowPlacement.Fullscreen
                        }
                    true
                } else {
                    processKeyShortcuts(
                        keyEvent = keyEvent,
                        onNavigateTo = { /* no-op: legacy shortcuts not used here */ },
                        tabId = currentTabs.getOrNull(currentIndex)?.destination?.tabId ?: "",
                    )
                }
            } else {
                false
            }
        },
    ) {
        // Hook up the native window: focus tracking feeds DesktopManager (dock menu, deep links
        // and desktop actions target the focused window), and toFront() needs the handle.
        val nucleusWin = nucleusWindow
        DisposableEffect(nucleusWin) {
            openWindow.nucleusWindow = nucleusWin
            onDispose {
                if (openWindow.nucleusWindow === nucleusWin) openWindow.nucleusWindow = null
            }
        }
        LaunchedEffect(nucleusWin) {
            nucleusWin.focusFlow.collect { focused ->
                if (focused) desktopMgr.onWindowFocused(openWindow.id)
            }
        }

        CompositionLocalProvider(
            LocalOpenWindow provides openWindow,
            LocalWindowViewModelStoreOwner provides windowViewModelOwner,
            LocalViewModelStoreOwner provides windowViewModelOwner,
        ) {
            MainTitleBar()

            // Keep the screen awake while a book is open in the current tab and this window is
            // focused — opt-out via the General settings (enabled by default).
            val keepAwakeEnabled by AppSettings.keepScreenAwakeOnBookFlow.collectAsState()
            val shouldKeepScreenAwake =
                keepAwakeEnabled &&
                    state.isActive &&
                    selectedTab?.destination is TabsDestination.BookContent
            LaunchedEffect(shouldKeepScreenAwake) {
                if (shouldKeepScreenAwake) {
                    EnergyManager.keepScreenAwake()
                } else {
                    EnergyManager.releaseScreenAwake()
                }
            }

            // Track whether the user is interacting by touch so hover-gated
            // controls (e.g. pane close buttons) stay reachable; published
            // app-wide via LocalIsTouchMode.
            var isTouchMode by remember { mutableStateOf(false) }

            // Intercept key combos early to avoid focus traversal consuming Tab
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .detectTouchMode { isTouchMode = it }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                when {
                                    // Ctrl/Cmd + W => close current tab
                                    isCtrlOrCmd && keyEvent.key == Key.W -> {
                                        tabsVm.onEvent(TabsEvents.OnClose(selectedIndex))
                                        true
                                    }
                                    // Ctrl/Cmd + Shift + Tab => previous tab
                                    isCtrlOrCmd && keyEvent.key == Key.Tab && keyEvent.isShiftPressed -> {
                                        val count = tabs.size
                                        if (count > 0) {
                                            val newIndex = (selectedIndex - 1 + count) % count
                                            tabsVm.onEvent(TabsEvents.OnSelect(newIndex))
                                        }
                                        true
                                    }
                                    // Ctrl/Cmd + Tab => next tab
                                    isCtrlOrCmd && keyEvent.key == Key.Tab -> {
                                        val count = tabs.size
                                        if (count > 0) {
                                            val newIndex = (selectedIndex + 1) % count
                                            tabsVm.onEvent(TabsEvents.OnSelect(newIndex))
                                        }
                                        true
                                    }
                                    // Ctrl/Cmd + T => new tab
                                    isCtrlOrCmd && keyEvent.key == Key.T -> {
                                        tabsVm.onEvent(TabsEvents.OnAdd)
                                        true
                                    }
                                    // Alt + Home (Windows) or Cmd + Shift + H (macOS) => go Home on current tab
                                    (keyEvent.isAltPressed && keyEvent.key == Key.Home) ||
                                        (
                                            keyEvent.isMetaPressed &&
                                                keyEvent.isShiftPressed &&
                                                keyEvent.key == Key.H
                                        ) -> {
                                        val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId
                                        if (currentTabId != null) {
                                            tabsVm.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    // Ctrl/Cmd + Comma => open settings
                                    isCtrlOrCmd && keyEvent.key == Key.Comma -> {
                                        settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpen)
                                        true
                                    }
                                    // Ctrl/Cmd + Alt + Right => next desktop (in this window)
                                    isCtrlOrCmd && keyEvent.isAltPressed && keyEvent.key == Key.DirectionRight -> {
                                        desktopMgr.switchToNext(openWindow.id)
                                        true
                                    }
                                    // Ctrl/Cmd + Alt + Left => previous desktop (in this window)
                                    isCtrlOrCmd && keyEvent.isAltPressed && keyEvent.key == Key.DirectionLeft -> {
                                        desktopMgr.switchToPrevious(openWindow.id)
                                        true
                                    }
                                    // Ctrl/Cmd + Alt + N => new desktop in this window
                                    isCtrlOrCmd && keyEvent.isAltPressed && keyEvent.key == Key.N -> {
                                        desktopMgr.createDesktop(openWindow.id, nextDesktopName)
                                        true
                                    }
                                    // Ctrl/Cmd + N => new desktop in a new window
                                    isCtrlOrCmd && keyEvent.key == Key.N -> {
                                        desktopMgr.createDesktopInNewWindow(nextDesktopName)
                                        true
                                    }
                                    // Cmd + M => minimize window (macOS only)
                                    PlatformInfo.isMacOS && keyEvent.isMetaPressed && keyEvent.key == Key.M -> {
                                        windowState.isMinimized = true
                                        true
                                    }
                                    // F11 => toggle fullscreen (Windows/Linux only)
                                    !PlatformInfo.isMacOS && keyEvent.key == Key.F11 -> {
                                        windowState.placement =
                                            if (windowState.placement == WindowPlacement.Fullscreen) {
                                                WindowPlacement.Maximized
                                            } else {
                                                WindowPlacement.Fullscreen
                                            }
                                        true
                                    }

                                    else -> false
                                }
                            } else {
                                false
                            }
                        },
            ) {
                CompositionLocalProvider(LocalIsTouchMode provides isTouchMode) {
                    TabsContent()
                }
            }
        }
    }
}
