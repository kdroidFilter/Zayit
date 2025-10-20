package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import androidx.compose.ui.window.application
import com.kdroid.composetray.tray.api.ExperimentalTrayAppApi
import com.kdroid.composetray.utils.SingleInstanceManager
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.components.MainTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsNavHost
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.OnBoardingWindow
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.session.SessionManager
import io.github.vinceglb.filekit.FileKit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.zayit_transparent
import java.awt.Dimension
import java.awt.Window
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalTrayAppApi::class)
fun main() {
    setMacOsAdaptiveTitleBar()
    //    val enableLogs = System.getenv("ENABLE_LOGS")
    //    if (enableLogs == null) {
    //        SilenceLogs.everything(hardMuteStdout = true, hardMuteStderr = true)
    //    }


    val appId = "io.github.kdroidfilter.seforimapp"
    SingleInstanceManager.configuration = SingleInstanceManager.Configuration(
        lockIdentifier = appId
    )

    Locale.setDefault(Locale.Builder().setLanguage("he").build())
    application {
        FileKit.init(appId)

        val windowState = remember { getCenteredWindowState(1280, 720) }
        val onboardingWindowState = remember { getCenteredWindowState(720, 420) }
        var isWindowVisible by remember { mutableStateOf(true) }

        val mainState = MainAppState
        val showOnboarding : Boolean? = mainState.showOnBoarding.collectAsState().value

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

        CompositionLocalProvider(LocalAppGraph provides appGraph) {
            val themeDefinition = ThemeUtils.buildThemeDefinition()

            IntUiTheme(
                theme = themeDefinition,
                styling = ComponentStyling.default().decoratedWindow(
                    titleBarStyle = ThemeUtils.pickTitleBarStyle(),
                )
            ) {
                // Decide whether to show onboarding based on database availability
                LaunchedEffect(Unit) {
                    try {
                        // getDatabasePath() throws if not configured or file missing
                        getDatabasePath()
                        mainState.setShowOnBoarding(false)
                    } catch (_: Exception) {
                        mainState.setShowOnBoarding(true)
                    }
                }

                if (showOnboarding == true) {
                    DecoratedWindow(
                        onCloseRequest = { exitApplication() },
                        title = stringResource(Res.string.app_name),
                        icon = painterResource(Res.drawable.zayit_transparent),
                        state = onboardingWindowState,
                        visible = true,
                    ) {
                        OnBoardingWindow()
                    }

                } else if (showOnboarding == false) {
                    DecoratedWindow(
                        onCloseRequest = {
                            // Persist session if enabled, then exit
                            SessionManager.saveIfEnabled(appGraph)
                            exitApplication()
                        },
                        title = stringResource(Res.string.app_name),
                        icon = painterResource(Res.drawable.zayit_transparent),
                        state = windowState,
                        visible = isWindowVisible,
                        onKeyEvent = { keyEvent ->
                            processKeyShortcuts(
                                keyEvent = keyEvent,
                                onNavigateTo = {
                                    //TODO
                                }
                            )
                        },
                    ) {
                        window.minimumSize = Dimension(350, 600)
                        window.toFront()
                        window.requestFocus()
                        MainTitleBar()
                        // Restore previously saved session once when main window becomes active
                        var sessionRestored by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            if (!sessionRestored) {
                                SessionManager.restoreIfEnabled(appGraph)
                                sessionRestored = true
                            }
                        }
                        TabsNavHost()
                    }
                } // else (null) -> render nothing until decision made
            }
        }
    }
}
