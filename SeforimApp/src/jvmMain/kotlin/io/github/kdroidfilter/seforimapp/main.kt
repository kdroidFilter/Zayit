@file:Suppress("ktlint:standard:filename")

package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.nucleusframework.application.aotTraining
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.energymanager.EnergyManager
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.buildCopyWithSourcePayload
import io.github.kdroidfilter.seforimapp.core.deeplink.ContentDeepLinkHandler
import io.github.kdroidfilter.seforimapp.core.presentation.components.AppDockMenu
import io.github.kdroidfilter.seforimapp.core.presentation.components.AppJumpList
import io.github.kdroidfilter.seforimapp.core.presentation.components.AppLinuxQuicklist
import io.github.kdroidfilter.seforimapp.core.presentation.components.AppNativeMenuBar
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.window.MainAppWindow
import io.github.kdroidfilter.seforimapp.core.presentation.window.TabDragGhostWindow
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.database.update.DatabaseUpdateWindow
import io.github.kdroidfilter.seforimapp.features.onboarding.OnBoardingWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindow
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowEvents
import io.github.kdroidfilter.seforimapp.features.settings.SettingsWindowViewModel
import io.github.kdroidfilter.seforimapp.features.update.UpdateDialog
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.PendingDbCleanup
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.kdroidfilter.seforimapp.logger.infoln
import io.github.kdroidfilter.seforimapp.logger.isDevEnv
import io.github.kdroidfilter.seforimlibrary.cli.runCli
import io.github.kdroidfilter.seforimlibrary.core.text.HebrewTextUtils
import io.github.vinceglb.filekit.FileKit
import io.sentry.Sentry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import seforimapp.seforimapp.generated.resources.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.util.*
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
private val AOT_TRAINING_DURATION = 45.seconds

private data class StartupState(
    val showOnboarding: Boolean,
    val showDatabaseUpdate: Boolean,
    val isDatabaseMissing: Boolean,
)

/**
 * Determines the initial routing state synchronously. All operations are fast local I/O (read settings, check file existence, read version
 * file).
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

private fun initializeSentry() {
    val sentryEnvironment =
        System
            .getenv("SENTRY_ENVIRONMENT")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "development"

    Sentry.init { options ->
        options.dsn = "https://09cbadaf522c567b431dd4384c8f080b@o4510855773093888.ingest.de.sentry.io/4510857007726672"
        options.environment = sentryEnvironment
        options.release = NucleusApp.version
        options.isDebug = isDevEnv
    }
    infoln { "Sentry initialized for environment '$sentryEnvironment'." }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun main(args: Array<String>) {
    // Headless CLI mode: when the binary is launched as `zayit cli <args...>` (e.g. from the
    // in-app "open CLI in terminal" action), delegate to the SeforimLibrary search CLI and exit
    // BEFORE any Sentry/Nucleus/GUI initialization. This keeps the normal GUI launch path
    // completely untouched — the branch is only taken when "cli" is the first argument.
    if (args.firstOrNull() == "cli") {
        exitProcess(runCli(args.copyOfRange(1, args.size)))
    }

    val loggingEnv = System.getenv("SEFORIMAPP_LOGGING")?.lowercase()
    isDevEnv = loggingEnv == "true" || loggingEnv == "1" || loggingEnv == "yes"

    initializeSentry()

    // Roll back any half-applied seforim.db delta update from a previous
    // launch BEFORE the SQLDelight repository opens the DB. Cheap stat()
    // when nothing is in flight; never throws (failures are logged).
//    DbDeltaRecoveryBootstrap.runOnce()

    val appId = "io.github.kdroidfilter.seforimapp"

    nucleusApplication(
        args,
        defaultLocale = Locale.Builder().setLanguage("he").build(),
    ) {
        aotTraining(duration = AOT_TRAINING_DURATION)

        FileKit.init(appId)

        // Retry any database cleanup a previous run could not finish (e.g. a file locked
        // by antivirus/Windows Search). Runs once, before the SQLDelight repository opens
        // the DB, so a fresh install no longer needs the user to delete the old DB by hand.
        remember { PendingDbCleanup.runOnce() }

        val pendingDeepLink = remember { MutableStateFlow<String?>(null) }

        // Pick up the deep link CLI arg (cold-start) and any URI relayed by a second instance
        // through the automatic single-instance bridge. Register once: onDeepLink re-parses the
        // CLI args on every call, so invoking it on each recomposition would re-deliver the URI
        // and open duplicate tabs (Windows cold-start, where the link arrives via args; macOS is
        // unaffected because it delivers via Apple Events with empty args).
        LaunchedEffect(Unit) {
            onDeepLink { uri -> pendingDeepLink.value = uri.toString() }
        }

        // Create the application graph via Metro and expose via CompositionLocal
        val appGraph = remember { createGraph<AppGraph>() }
        // Ensure AppSettings uses the DI-provided Settings immediately
        AppSettings.initialize(appGraph.settings)

        // Register the AWT-level keyboard shortcuts here (instead of in main()) so they can read
        // from the DI-provided SelectionContext. The DisposableEffect re-runs only if the graph
        // identity changes (effectively never), and removes the dispatchers on app teardown.
        val selectionContext = appGraph.selectionContext
        DisposableEffect(selectionContext) {
            val km = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val copyWithoutNikud =
                KeyEventDispatcher { event ->
                    if (event.id == KeyEvent.KEY_PRESSED &&
                        event.keyCode == KeyEvent.VK_C &&
                        event.isShiftDown &&
                        (event.isMetaDown || event.isControlDown)
                    ) {
                        val selectedText = selectionContext.selectedText.value
                        if (selectedText.isNotBlank()) {
                            val stripped = HebrewTextUtils.removeAllDiacritics(selectedText)
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(stripped), null)
                        }
                        true
                    } else {
                        false
                    }
                }
            // Skip when AltGraph is down to avoid clobbering character composition on
            // Linux/Windows layouts where Ctrl+Alt is interpreted as AltGr.
            val copyWithSource =
                KeyEventDispatcher { event ->
                    if (event.id == KeyEvent.KEY_PRESSED &&
                        event.keyCode == KeyEvent.VK_C &&
                        event.isAltDown &&
                        !event.isAltGraphDown &&
                        !event.isShiftDown &&
                        (event.isMetaDown || event.isControlDown)
                    ) {
                        val selectedText = selectionContext.selectedText.value
                        val active = selectionContext.activeBook.value
                        if (selectedText.isNotBlank() && active != null) {
                            val payload =
                                buildCopyWithSourcePayload(
                                    selectedText,
                                    active.book,
                                    active.rootTitle,
                                    selectionContext.visibleLines.value.lines,
                                )
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(payload), null)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            km.addKeyEventDispatcher(copyWithoutNikud)
            km.addKeyEventDispatcher(copyWithSource)
            onDispose {
                km.removeKeyEventDispatcher(copyWithoutNikud)
                km.removeKeyEventDispatcher(copyWithSource)
            }
        }

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
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            val themeDefinition = ThemeUtils.buildThemeDefinition()
            val componentStyling = ThemeUtils.buildComponentStyling()

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
                    val desktopManager = appGraph.desktopManager
                    val windows by desktopManager.windows.collectAsState()
                    val focusedWindowId by desktopManager.focusedWindowId.collectAsState()
                    val focusedWindow = windows.find { it.id == focusedWindowId } ?: windows.firstOrNull()

                    // One ViewModelStore shared by every main window, so window-agnostic
                    // ViewModels (settings dialog state) resolve to a single instance app-wide.
                    val windowViewModelOwner = rememberWindowViewModelStoreOwner()
                    val settingsWindowViewModel: SettingsWindowViewModel =
                        metroViewModel(viewModelStoreOwner = windowViewModelOwner)

                    val onQuit = {
                        // Persist session if enabled, apply any pending silent update, then exit.
                        // installPendingOnClose() launches the installer and exits the process
                        // itself when a silent (Win/Mac PATCH) update is ready.
                        SessionManager.saveIfEnabled(appGraph)
                        appGraph.appUpdateService.installPendingOnClose()
                        exitApplication()
                    }
                    // Chrome-like: closing the last tab of the last window quits the app
                    SideEffect { desktopManager.onQuitRequest = onQuit }

                    // App-level launcher integrations follow the focused window's tabs. key() forces
                    // their internal effects (dock/jumplist listeners) to re-register on the new
                    // window's TabsViewModel when focus moves — they capture it in closures.
                    if (focusedWindow != null) {
                        key(focusedWindow.id) {
                            if (PlatformInfo.isMacOS) {
                                // Native macOS menu bar (no-op on other platforms)
                                AppNativeMenuBar(
                                    mainAppState = mainAppState,
                                    tabsViewModel = focusedWindow.tabsViewModel,
                                    settingsWindowViewModel = settingsWindowViewModel,
                                    onQuit = onQuit,
                                )

                                // Native macOS dock menu with desktops and tabs
                                AppDockMenu(
                                    desktopManager = desktopManager,
                                    tabsViewModel = focusedWindow.tabsViewModel,
                                )
                            }

                            // Windows taskbar jump list with tabs and desktops
                            AppJumpList(
                                desktopManager = desktopManager,
                                tabsViewModel = focusedWindow.tabsViewModel,
                                pendingDeepLink = pendingDeepLink,
                                onClearDeepLink = { pendingDeepLink.value = null },
                            )

                            // Linux taskbar quicklist with tabs and desktops
                            AppLinuxQuicklist(
                                desktopManager = desktopManager,
                                tabsViewModel = focusedWindow.tabsViewModel,
                            )
                        }
                    }

                    // Resolve shareable zayit:// content deep links (cross-platform); opens in
                    // the window focused at the time the link arrives.
                    ContentDeepLinkHandler(
                        desktopManager = desktopManager,
                        repository = appGraph.repository,
                        pendingDeepLink = pendingDeepLink,
                        onClearDeepLink = { pendingDeepLink.value = null },
                    )

                    // Restore previously saved session (open desktops, windows, geometry) once.
                    var sessionRestored by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (!sessionRestored) {
                            SessionManager.restoreIfEnabled(appGraph)
                            sessionRestored = true
                        }
                    }

                    // Check for updates once at startup. PATCH updates are pre-downloaded
                    // here; MINOR/MAJOR surface the title-bar icon + UpdateDialog.
                    LaunchedEffect(Unit) {
                        appGraph.appUpdateService.checkOnStartup()
                    }

                    // Debounced session autosave: any tab/window change persists ~2s later, so a
                    // crash no longer loses the whole session (previously saved only on quit).
                    LaunchedEffect(Unit) {
                        desktopManager.windows
                            .flatMapLatest { ws ->
                                if (ws.isEmpty()) {
                                    emptyFlow()
                                } else {
                                    combine(ws.map { w -> w.tabsViewModel.state }) { }
                                }
                            }.drop(1)
                            .debounce(2.seconds)
                            .collect {
                                if (!SessionManager.isRestoringSession.value) {
                                    SessionManager.saveIfEnabled(appGraph)
                                }
                            }
                    }

                    // Efficiency mode only when EVERY window is minimized
                    val allMinimized = windows.isNotEmpty() && windows.all { it.windowState.isMinimized }
                    LaunchedEffect(allMinimized) {
                        if (allMinimized) {
                            EnergyManager.enableEfficiencyMode()
                        } else {
                            EnergyManager.disableEfficiencyMode()
                        }
                    }

                    // App-level dialogs: single instance shared by all windows. They inherit the
                    // Rtl layout direction and theme from the providers above.
                    // The settings dialog is normally composed inside its owner window
                    // (see MainAppWindow) so it is modal to that window only; this
                    // app-scope fallback only covers an owner window that disappeared,
                    // making the dialog app-modal instead of silently dropping it.
                    val settingsWindowState by settingsWindowViewModel.state.collectAsState()
                    if (settingsWindowState.isVisible && windows.none { it.id == settingsWindowState.ownerWindowId }) {
                        SettingsWindow(
                            onClose = { settingsWindowViewModel.onEvent(SettingsWindowEvents.OnClose) },
                            initialDestination = settingsWindowState.initialDestination,
                        )
                    }
                    val updateDialogVisible by appGraph.appUpdateService.dialogVisible.collectAsState()
                    if (updateDialogVisible) {
                        UpdateDialog(
                            service = appGraph.appUpdateService,
                            onClose = { appGraph.appUpdateService.closeDialog() },
                        )
                    }

                    // The windows themselves — one per open desktop window.
                    windows.forEach { w ->
                        key(w.id) {
                            MainAppWindow(
                                openWindow = w,
                                settingsWindowViewModel = settingsWindowViewModel,
                                windowViewModelOwner = windowViewModelOwner,
                                onQuit = onQuit,
                            )
                        }
                    }

                    // Floating preview while a tab is dragged outside its strip
                    TabDragGhostWindow(appGraph.tabDockManager)
                }
            }
        }
    }
}
