package io.github.kdroidfilter.seforimapp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.application
import com.jetbrains.WindowDecorations
import com.kdroid.composetray.tray.api.ExperimentalTrayAppApi
import com.kdroid.composetray.tray.api.TrayApp
import com.kdroid.composetray.utils.SingleInstanceManager
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.darkmodedetector.mac.setMacOsAdaptiveTitleBar
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionsButtonsView
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsNavHost
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.OnBoardingScreen
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Bookmark
import io.github.kdroidfilter.seforimapp.icons.Library
import io.github.vinceglb.filekit.FileKit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
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



    val appId  = "io.github.kdroidfilter.seforimapp"
    SingleInstanceManager.configuration = SingleInstanceManager.Configuration(
        lockIdentifier = appId
    )


    Locale.setDefault(Locale.Builder().setLanguage("he").build())
    application {
        FileKit.init(appId)
        val itemText = stringResource(Res.string.app_name)
        TrayApp(icon = Library, tooltip = "Seforim", menu = {
            Item(itemText, Bookmark) { exitApplication() }
        }) {
        }

        val windowState = remember { getCenteredWindowState(1280, 720) }
        val onboardingWindowState = remember { getCenteredWindowState(720, 420) }
        var isWindowVisible by remember { mutableStateOf(true) }
        // Null = decision pending; true = show onboarding; false = show main app
        var showOnboarding by remember { mutableStateOf<Boolean?>(null) }

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

        // Initialize static delegates that need app-level instances
        LaunchedEffect(Unit) {
            AppSettings.initialize(appGraph.settings)
        }

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
                    showOnboarding = try {
                        // getDatabasePath() throws if not configured or file missing
                        getDatabasePath()
                        false
                    } catch (_: Exception) {
                        true
                    }
                }

                if (showOnboarding == true) {
                    DecoratedWindow(
                        onCloseRequest = {},
                        title = stringResource(Res.string.app_name),
                        icon = painterResource(Res.drawable.zayit_transparent),
                        state = onboardingWindowState,
                        visible = true,
                    ) {
                        Column(
                            modifier = Modifier
                                .trackActivation()
                                .fillMaxSize()
                                .background(JewelTheme.globalColors.panelBackground),
                        ) {
                            OnBoardingScreen(onFinish = {
                                showOnboarding = false
                                isWindowVisible = true
                            })
                        }
                    }
                } else if (showOnboarding == false) {
                    DecoratedWindow(
                        onCloseRequest = { exitApplication() },
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
                        TitleBar(modifier = Modifier.newFullscreenControls()) {
                            BoxWithConstraints {
                                val windowWidth = maxWidth
                                val iconsNumber = 4
                                val iconWidth = 40
                                Row {
                                    Row(
                                        modifier = Modifier
                                            .padding(start = 0.dp)
                                            .align(Alignment.Start)
                                            .width(windowWidth -
                                                    when (getOperatingSystem()) {
                                                        OperatingSystem.MACOS -> iconWidth * (iconsNumber + 2).dp
                                                        OperatingSystem.WINDOWS -> iconWidth * (iconsNumber + 3.5).dp
                                                        else -> (iconWidth * iconsNumber).dp
                                                    }
                                            )
                                    ) {
                                        TabsView()
                                    }
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .fillMaxHeight(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TitleBarActionsButtonsView()
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .trackActivation()
                                .fillMaxSize()
                                .background(JewelTheme.globalColors.panelBackground),
                        ) {
                            TabsNavHost()
                        }
                    }
                } // else (null) -> render nothing until decision made
            }
        }
    }
}
