@file:Suppress("ktlint:standard:filename")

package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.knotify.compose.builder.notification
import io.github.kdroidfilter.nucleus.aot.runtime.AotRuntime
import io.github.kdroidfilter.nucleus.core.runtime.ExecutableRuntime
import io.github.kdroidfilter.nucleus.core.runtime.SingleInstanceManager
import io.github.kdroidfilter.nucleus.window.DecoratedWindow
import io.github.kdroidfilter.nucleus.window.NucleusDecoratedWindowTheme
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.core.TextSelectionStore
import io.github.kdroidfilter.seforimapp.core.presentation.components.MainTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsContent
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.theme.islandsComponentStyling
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseUpdateWindow
import io.github.kdroidfilter.seforimapp.features.onboarding.OnBoardingWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateChecker
import io.github.kdroidfilter.seforimapp.logger.isDevEnv
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.ui.ComponentStyling
import seforimapp.seforimapp.generated.resources.*
import java.awt.Desktop
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.net.URI
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
private const val AOT_TRAINING_DURATION_MS = 45_000L

private data class StartupState(
    val showOnboarding: Boolean,
    val showDatabaseUpdate: Boolean,
    val isDatabaseMissing: Boolean,
)

/**
 * Determines the initial routing state synchronously.
 * All operations are fast local I/O (read settings, check file existence, read version file).
 */
private fun computeStartupState(): StartupState =
    try {
        getDatabasePath()
        val onboardingFinished = AppSettings.isOnboardingFinished()
        if (!onboardingFinished) {
            StartupState(showOnboarding = true, showDatabaseUpdate = false, isDatabaseMissing = false)
        } else {
            val isVersionCompatible = DatabaseVersionManager.isDatabaseVersionCompatible()
            if (!isVersionCompatible) {
                StartupState(showOnboarding = false, showDatabaseUpdate = true, isDatabaseMissing = false)
            } else {
                StartupState(showOnboarding = false, showDatabaseUpdate = false, isDatabaseMissing = false)
            }
        }
    } catch (_: Exception) {
        val onboardingFinished = AppSettings.isOnboardingFinished()
        if (!onboardingFinished) {
            StartupState(showOnboarding = true, showDatabaseUpdate = false, isDatabaseMissing = false)
        } else {
            StartupState(showOnboarding = false, showDatabaseUpdate = true, isDatabaseMissing = true)
        }
    }

fun main() {
    if (AotRuntime.isTraining()) {
        Thread({
            Thread.sleep(AOT_TRAINING_DURATION_MS)
            kotlin.system.exitProcess(0)
        }, "aot-timer").apply {
            isDaemon = false
            start()
        }
    }

    // Force OpenGL rendering backend on Windows if enabled (must be set before Skia initialization)
    if (PlatformInfo.isWindows && AppSettings.isUseOpenGlEnabled()) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }

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
    isDevEnv = loggingEnv == "true" || loggingEnv == "1" || loggingEnv == "yes"

    val appId = "io.github.kdroidfilter.seforimapp"
    SingleInstanceManager.configuration =
        SingleInstanceManager.Configuration(
            lockIdentifier = appId,
        )

    Locale.setDefault(Locale.Builder().setLanguage("he").build())
    application {
        FileKit.init(appId)

        val screen = Toolkit.getDefaultToolkit().screenSize
        val windowState =
            if (!PlatformInfo.isWindows) {
                rememberWindowState(
                    position = WindowPosition.Aligned(Alignment.Center),
                    placement = WindowPlacement.Maximized,
                    size = DpSize(screen.width.dp, screen.height.dp),
                )
            } else {
                rememberWindowState(
                    position = WindowPosition.Aligned(Alignment.Center),
                    placement = WindowPlacement.Maximized,
                )
            }

        var isWindowVisible by remember { mutableStateOf(true) }

        val isSingleInstance =
            SingleInstanceManager.isSingleInstance(onRestoreRequest = {
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

        // Get MainAppState from DI graph
        val mainAppState = appGraph.mainAppState

        // Compute startup routing synchronously — all operations (read settings, check file
        // existence, read version file) are fast local I/O with no network involved.
        // Using remember { } instead of LaunchedEffect avoids a blank first frame while
        // waiting for the coroutine scheduler to run the routing logic.
        val startupState = remember { computeStartupState() }
        val showOnboardingFromState by mainAppState.showOnBoarding.collectAsState()
        val showOnboarding = showOnboardingFromState ?: startupState.showOnboarding
        var showDatabaseUpdate by remember { mutableStateOf(startupState.showDatabaseUpdate) }
        var isDatabaseMissing by remember { mutableStateOf(startupState.isDatabaseMissing) }

        // Sync pre-computed state to mainAppState for any other observers of the flow
        LaunchedEffect(Unit) {
            mainAppState.setShowOnBoarding(startupState.showOnboarding)
        }

        val initialTheme = remember { AppSettings.getThemeMode() }
        LaunchedEffect(initialTheme) {
            if (mainAppState.theme.value != initialTheme) {
                mainAppState.setTheme(initialTheme)
            }
        }

        // themeStyle is already initialized from AppSettings in MainAppState, no separate LaunchedEffect needed

        CompositionLocalProvider(
            LocalAppGraph provides appGraph,
            LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
        ) {
            val isDark = ThemeUtils.isDarkTheme()
            val themeDefinition = ThemeUtils.buildThemeDefinition()
            val themeStyle by mainAppState.themeStyle.collectAsState()

            val customTitleBarStyle = ThemeUtils.buildCustomTitleBarStyle()

            val componentStyling =
                when (themeStyle) {
                    ThemeStyle.Islands -> islandsComponentStyling(isDark)
                    ThemeStyle.Classic -> if (isDark) ComponentStyling.dark() else ComponentStyling.light()
                }

            NucleusDecoratedWindowTheme(
                isDark = isDark,
                titleBarStyle = customTitleBarStyle,
            ) {
                IntUiTheme(
                    theme = themeDefinition,
                    styling = componentStyling,
                ) {
                    if (showOnboarding) {
                        OnBoardingWindow()
                    } else if (showDatabaseUpdate) {
                        DatabaseUpdateWindow(
                            onUpdateComplete = {
                                // After database update, refresh the version check and show main app
                                showDatabaseUpdate = false
                            },
                            isDatabaseMissing = isDatabaseMissing,
                        )
                    } else {
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
                        val formattedTabTitle =
                            when {
                                rawTitle.isEmpty() -> stringResource(Res.string.home)
                                tabType == TabType.SEARCH -> stringResource(Res.string.search_results_tab_title, rawTitle)
                                else -> rawTitle
                            }
                        val windowTitle =
                            if (formattedTabTitle.isNotBlank()) {
                                stringResource(Res.string.window_title_with_tab, appTitle, formattedTabTitle)
                            } else {
                                appTitle
                            }

                        DecoratedWindow(
                            onCloseRequest = {
                                // Persist session if enabled, then exit
                                SessionManager.saveIfEnabled(appGraph)
                                exitApplication()
                            },
                            title = windowTitle,
                            icon = if (PlatformInfo.isMacOS) null else painterResource(Res.drawable.AppIcon),
                            state = windowState,
                            visible = isWindowVisible,
                            onKeyEvent = { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                                    if (isCtrlOrCmd && keyEvent.key == Key.T) {
                                        tabsVm.onEvent(TabsEvents.OnAdd)
                                        true
                                    } else if (isCtrlOrCmd && keyEvent.key == Key.W) {
                                        // Close current tab with Ctrl/Cmd + W
                                        tabsVm.onEvent(TabsEvents.OnClose(selectedIndex))
                                        true
                                    } else if (isCtrlOrCmd && keyEvent.key == Key.Tab) {
                                        val count = tabs.size
                                        if (count > 0) {
                                            val direction = if (keyEvent.isShiftPressed) -1 else 1
                                            val newIndex = (selectedIndex + direction + count) % count
                                            tabsVm.onEvent(TabsEvents.OnSelect(newIndex))
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
                                        settingsWindowViewModel.onEvent(SettingsWindowEvents.OnOpen)
                                        true
                                    } else if (PlatformInfo.isMacOS && keyEvent.isMetaPressed && keyEvent.key == Key.M) {
                                        // Minimize window with Cmd+M on macOS
                                        windowState.isMinimized = true
                                        true
                                    } else {
                                        processKeyShortcuts(
                                            keyEvent = keyEvent,
                                            onNavigateTo = { /* no-op: legacy shortcuts not used here */ },
                                            tabId = tabs.getOrNull(selectedIndex)?.destination?.tabId ?: "",
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
                                    if (PlatformInfo.isWindows) {
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
                                // Check for updates once at startup
                                val updateNotificationTitle = stringResource(Res.string.update_available_toast)
                                val updateNotificationMessage = stringResource(Res.string.update_notification_message)
                                val updateNotificationButton = stringResource(Res.string.update_download_action)
                                LaunchedEffect(Unit) {
                                    if (!mainAppState.updateCheckDone.value) {
                                        when (val result = AppUpdateChecker.checkForUpdate()) {
                                            is AppUpdateChecker.UpdateCheckResult.UpdateAvailable -> {
                                                if (isDevEnv) return@LaunchedEffect
                                                mainAppState.setUpdateAvailable(result.latestVersion)

                                                if (!ExecutableRuntime.isDev()) {
                                                    // Send system notification
                                                    notification(
                                                        title = updateNotificationTitle,
                                                        message = updateNotificationMessage,
                                                        largeIcon = {
                                                            Image(
                                                                painter = painterResource(Res.drawable.AppIcon),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        },
                                                        onActivated = {
                                                            Desktop.getDesktop().browse(URI(AppUpdateChecker.DOWNLOAD_URL))
                                                        },
                                                    ) {
                                                        button(title = updateNotificationButton) {
                                                            Desktop.getDesktop().browse(URI(AppUpdateChecker.DOWNLOAD_URL))
                                                        }
                                                    }.send()
                                                }
                                            }
                                            is AppUpdateChecker.UpdateCheckResult.UpToDate -> {
                                                mainAppState.markUpdateCheckDone()
                                            }
                                            is AppUpdateChecker.UpdateCheckResult.Error -> {
                                                mainAppState.markUpdateCheckDone()
                                            }
                                        }
                                    }
                                }

                                // Intercept key combos early to avoid focus traversal consuming Tab
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
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
                                                            (keyEvent.isMetaPressed && keyEvent.isShiftPressed && keyEvent.key == Key.H) -> {
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
                                                        // Cmd + M => minimize window (macOS only)
                                                        PlatformInfo.isMacOS && keyEvent.isMetaPressed && keyEvent.key == Key.M -> {
                                                            windowState.isMinimized = true
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else {
                                                    false
                                                }
                                            },
                                ) { TabsContent() }
                            }
                        }
                    }
                }
            } // NucleusDecoratedWindowTheme
        }
    }
}
