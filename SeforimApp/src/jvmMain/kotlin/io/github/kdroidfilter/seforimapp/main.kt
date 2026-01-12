package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kdroid.composetray.tray.api.ExperimentalTrayAppApi
import com.kdroid.composetray.utils.SingleInstanceManager
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.presentation.components.MainTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsNavHost
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.OnBoardingWindow
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseUpdateWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import seforimapp.seforimapp.generated.resources.*
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Window
import java.util.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.logger.allowLogging
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.kdroidfilter.seforimapp.core.TextSelectionStore
import java.awt.datatransfer.StringSelection
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

@OptIn(ExperimentalFoundationApi::class, ExperimentalTrayAppApi::class)
fun main() {
    setMacOsAdaptiveTitleBar()

    // Register global AWT key event dispatcher for Cmd+Shift+C (copy without nikud)
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_PRESSED &&
            event.keyCode == KeyEvent.VK_C &&
            event.isShiftDown &&
            (event.isMetaDown || event.isControlDown)
        ) {
            val selectedText = TextSelectionStore.selectedText.value
            if (selectedText.isNotBlank()) {
                val textWithoutDiacritics = HebrewTextUtils.removeAllDiacritics(selectedText)
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(textWithoutDiacritics), null)
            }
            true // consume the event
        } else {
            false
        }
    }
    val loggingEnv = System.getenv("SEFORIMAPP_LOGGING")?.lowercase()
    allowLogging = loggingEnv == "true" || loggingEnv == "1" || loggingEnv == "yes"

    val appId = "io.github.kdroidfilter.seforimapp"
    SingleInstanceManager.configuration = SingleInstanceManager.Configuration(
        lockIdentifier = appId
    )

    Locale.setDefault(Locale.Builder().setLanguage("he").build())
    application {
        FileKit.init(appId)

        val screen = Toolkit.getDefaultToolkit().screenSize
        val windowState = if (getOperatingSystem() != OperatingSystem.WINDOWS) rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            placement = WindowPlacement.Maximized,
            size = DpSize(screen.width.dp, screen.height.dp)
        ) else rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            placement = WindowPlacement.Maximized,
        )

        var isWindowVisible by remember { mutableStateOf(true) }

        val mainState = MainAppState
        val showOnboarding: Boolean? = mainState.showOnBoarding.collectAsState().value
        var showDatabaseUpdate by remember { mutableStateOf<Boolean?>(null) }
        var isDatabaseMissing by remember { mutableStateOf(false) }

        val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
            isWindowVisible = true
            windowState.isMinimized = false
            Window.getWindows().first().toFront()
        })
        if (!isSingleInstance) {
            exitApplication()
            return@application
        }

        // Create the application graph via Metro and expose via CompositionLocal
        val appGraph = remember { createGraph<AppGraph>() }
        // Ensure AppSettings uses the DI-provided Settings immediately
        AppSettings.initialize(appGraph.settings)
        val initialTheme = remember { AppSettings.getThemeMode() }
        LaunchedEffect(initialTheme) {
            if (MainAppState.theme.value != initialTheme) {
                MainAppState.setTheme(initialTheme)
            }
        }

        CompositionLocalProvider(
            LocalAppGraph provides appGraph,
            LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        ) {
            val themeDefinition = ThemeUtils.buildThemeDefinition()

            IntUiTheme(
                theme = themeDefinition, styling = ComponentStyling.default().decoratedWindow(
                    titleBarStyle = ThemeUtils.pickTitleBarStyle(),
                )
            ) {
                // Decide whether to show onboarding, database update, or main app
                LaunchedEffect(Unit) {
                    try {
                        // getDatabasePath() throws if not configured or file missing
                        getDatabasePath()
                        
                        // Check if onboarding is finished
                        val onboardingFinished = AppSettings.isOnboardingFinished()
                        
                        if (!onboardingFinished) {
                            // Show onboarding if not finished
                            mainState.setShowOnBoarding(true)
                            showDatabaseUpdate = false
                        } else {
                            // Onboarding is finished, check database version
                            val isVersionCompatible = DatabaseVersionManager.isDatabaseVersionCompatible()
                            
                            if (!isVersionCompatible) {
                                // Database needs update
                                mainState.setShowOnBoarding(false)
                                showDatabaseUpdate = true
                                isDatabaseMissing = false
                            } else {
                                // Everything is ready, show main app
                                mainState.setShowOnBoarding(false)
                                showDatabaseUpdate = false
                            }
                        }
                    } catch (_: Exception) {
                        // If DB is missing but app is configured, show database update with error message
                        val onboardingFinished = AppSettings.isOnboardingFinished()
                        
                        if (!onboardingFinished) {
                            // App not configured, show onboarding
                            mainState.setShowOnBoarding(true)
                            showDatabaseUpdate = false
                            isDatabaseMissing = false
                        } else {
                            // App configured but DB missing, show database update with error
                            mainState.setShowOnBoarding(false)
                            showDatabaseUpdate = true
                            isDatabaseMissing = true
                        }
                    }
                }

                if (showOnboarding == true) {
                    OnBoardingWindow()
                } else if (showDatabaseUpdate == true) {
                    DatabaseUpdateWindow(
                        onUpdateCompleted = {
                            // After database update, refresh the version check and show main app
                            showDatabaseUpdate = false
                        },
                        isDatabaseMissing = isDatabaseMissing
                    )
                } else if (showOnboarding == false && showDatabaseUpdate == false) {
                    val windowViewModelOwner = rememberWindowViewModelStoreOwner()
                    val settingsWindowViewModel: SettingsWindowViewModel =
                        metroViewModel(viewModelStoreOwner = windowViewModelOwner)

                    // Build dynamic window title: "AppName - CurrentTab"
                    val tabsVm = appGraph.tabsViewModel
                    val tabs by tabsVm.tabs.collectAsState()
                    val selectedIndex by tabsVm.selectedTabIndex.collectAsState()
                    val appTitle = stringResource(Res.string.app_name)
                    val selectedTab = tabs.getOrNull(selectedIndex)
                    val rawTitle = selectedTab?.title.orEmpty()
                    val tabType = selectedTab?.tabType
                    val formattedTabTitle = when {
                        rawTitle.isEmpty() -> stringResource(Res.string.home)
                        tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, rawTitle)
                        else -> rawTitle
                    }
                    val windowTitle = if (formattedTabTitle.isNotBlank())
                        stringResource(Res.string.window_title_with_tab, appTitle, formattedTabTitle)
                    else appTitle

                    DecoratedWindow(
                        onCloseRequest = {
                            // Persist session if enabled, then exit
                            SessionManager.saveIfEnabled(appGraph)
                            exitApplication()
                        },
                        title = windowTitle,
                        icon = if (getOperatingSystem() == OperatingSystem.MACOS) null else painterResource(  Res.drawable.AppIcon),
                        state = windowState,
                        visible = isWindowVisible,
                        onKeyEvent = { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                if (isCtrlOrCmd && keyEvent.key == Key.T) {
                                    tabsVm.onEvent(TabsEvents.onAdd)
                                    true
                                } else if (isCtrlOrCmd && keyEvent.key == Key.W) {
                                    // Close current tab with Ctrl/Cmd + W
                                    tabsVm.onEvent(TabsEvents.onClose(selectedIndex))
                                    true
                                } else if (isCtrlOrCmd && keyEvent.key == Key.Tab) {
                                    val count = tabs.size
                                    if (count > 0) {
                                        val direction = if (keyEvent.isShiftPressed) -1 else 1
                                        val newIndex = (selectedIndex + direction + count) % count
                                        tabsVm.onEvent(TabsEvents.onSelected(newIndex))
                                    }
                                    true
                                } else if ((keyEvent.isAltPressed && keyEvent.key == Key.Home) ||
                                    (keyEvent.isMetaPressed && keyEvent.isShiftPressed && keyEvent.key == Key.H)
                                ) {
                                    val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId
                                    if (currentTabId != null) {
                                        // Navigate current tab back to Home (preserve tab slot, refresh state)
                                        tabsVm.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
                                        true
                                    } else {
                                        false
                                    }
                                } else if (isCtrlOrCmd && keyEvent.key == Key.Comma) {
                                    // Open settings with Cmd+, or Ctrl+,
                                    settingsWindowViewModel.onEvent(SettingsWindowEvents.onOpen)
                                    true
                                } else if (getOperatingSystem() == OperatingSystem.MACOS && keyEvent.isMetaPressed && keyEvent.key == Key.M) {
                                    // Minimize window with Cmd+M on macOS
                                    windowState.isMinimized = true
                                    true
                                } else {
                                    processKeyShortcuts(
                                        keyEvent = keyEvent,
                                        onNavigateTo = { /* no-op: legacy shortcuts not used here */ },
                                        tabId = tabs.getOrNull(selectedIndex)?.destination?.tabId ?: ""
                                    )
                                }
                            } else {
                                false
                            }
                        },
                    ) {
                        CompositionLocalProvider(
                            LocalWindowViewModelStoreOwner provides windowViewModelOwner,
                            LocalViewModelStoreOwner provides windowViewModelOwner,
                        ) {
                            /**
                             * A hack to work around the window flashing its background color when closed
                             * (https://youtrack.jetbrains.com/issue/CMP-5651).
                             */
                            val background = JewelTheme.globalColors.panelBackground
                            LaunchedEffect(window, background) {
                                window.background = java.awt.Color(background.toArgb())
                            }

                            LaunchedEffect(Unit) {
                                window.minimumSize = Dimension(600, 300)
                                if (getOperatingSystem() == OperatingSystem.WINDOWS) {
                                    delay(10)
                                    windowState.placement = WindowPlacement.Maximized
                                }
                            }
                            MainTitleBar()

                            // Restore previously saved session once when main window becomes active
                            var sessionRestored by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                if (!sessionRestored) {
                                    SessionManager.restoreIfEnabled(appGraph)
                                    sessionRestored = true
                                }
                            }
                            // Intercept key combos early to avoid focus traversal consuming Tab
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                            when {
                                                // Ctrl/Cmd + W => close current tab
                                                isCtrlOrCmd && keyEvent.key == Key.W -> {
                                                    tabsVm.onEvent(TabsEvents.onClose(selectedIndex))
                                                    true
                                                }
                                                // Ctrl/Cmd + Shift + Tab => previous tab
                                                isCtrlOrCmd && keyEvent.key == Key.Tab && keyEvent.isShiftPressed -> {
                                                    val count = tabs.size
                                                    if (count > 0) {
                                                        val newIndex = (selectedIndex - 1 + count) % count
                                                        tabsVm.onEvent(TabsEvents.onSelected(newIndex))
                                                    }
                                                    true
                                                }
                                                // Ctrl/Cmd + Tab => next tab
                                                isCtrlOrCmd && keyEvent.key == Key.Tab -> {
                                                    val count = tabs.size
                                                    if (count > 0) {
                                                        val newIndex = (selectedIndex + 1) % count
                                                        tabsVm.onEvent(TabsEvents.onSelected(newIndex))
                                                    }
                                                    true
                                                }
                                                // Ctrl/Cmd + T => new tab
                                                isCtrlOrCmd && keyEvent.key == Key.T -> {
                                                    tabsVm.onEvent(TabsEvents.onAdd)
                                                    true
                                                }
                                                // Alt + Home (Windows) or Cmd + Shift + H (macOS) => go Home on current tab
                                                (keyEvent.isAltPressed && keyEvent.key == Key.Home) ||
                                                        (keyEvent.isMetaPressed && keyEvent.isShiftPressed && keyEvent.key == Key.H) -> {
                                                    val currentTabId = tabs.getOrNull(selectedIndex)?.destination?.tabId
                                                    if (currentTabId != null) {
                                                        tabsVm.replaceCurrentTabWithNewTabId(TabsDestination.Home(currentTabId))
                                                        true
                                                    } else false
                                                }
                                                // Ctrl/Cmd + Comma => open settings
                                                isCtrlOrCmd && keyEvent.key == Key.Comma -> {
                                                    settingsWindowViewModel.onEvent(SettingsWindowEvents.onOpen)
                                                    true
                                                }
                                                // Cmd + M => minimize window (macOS only)
                                                getOperatingSystem() == OperatingSystem.MACOS && keyEvent.isMetaPressed && keyEvent.key == Key.M -> {
                                                    windowState.isMinimized = true
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            ) { TabsNavHost() }
                        }
                    }
                } // else (null) -> render nothing until decision made
            }
        }
    }
}
