import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.application
import com.kdroid.composetray.utils.SingleInstanceManager
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.darkmodedetector.isSystemInDarkMode
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.components.TitleBarActionsButtonsView
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsNavHost
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.processKeyShortcuts
import io.github.kdroidfilter.seforimapp.core.utils.SilenceLogs
import io.github.kdroidfilter.seforimapp.framework.di.desktopModule
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.intui.window.styling.dark
import org.jetbrains.jewel.intui.window.styling.lightWithLightHeader
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.koin.compose.KoinApplication
import seforimapp.composeapp.generated.resources.Res
import seforimapp.composeapp.generated.resources.app_name
import seforimapp.composeapp.generated.resources.notoserifhebrew
import java.awt.Dimension
import java.awt.Window
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
fun main() {
    val enableLogs = System.getenv("ENABLE_LOGS")
    if (enableLogs == null) {
        SilenceLogs.everything(hardMuteStdout = true, hardMuteStderr = true)
    }
    Locale.setDefault(Locale.Builder().setLanguage("he").setRegion("IL").build())
    application {
        val windowState = remember { getCenteredWindowState(1280, 720) }
        var isWindowVisible by remember { mutableStateOf(true) }

        SingleInstanceManager.configuration = SingleInstanceManager.Configuration(
             lockIdentifier =  "io.github.kdroidfilter.seforimapp"
        )

        val isSingleInstance = SingleInstanceManager.isSingleInstance(onRestoreRequest = {
            isWindowVisible = true
            windowState.isMinimized = false
            Window.getWindows().first().toFront()
        })
        if (!isSingleInstance) {
            exitApplication()
            return@application
        }

        val isMacOs = getOperatingSystem() == OperatingSystem.MACOS
        KoinApplication(application = {
            modules(desktopModule)
        }) {
            val themeViewModel = ThemeViewModel
            val theme = themeViewModel.theme.collectAsState().value
            val isSystemInDarkMode = isSystemInDarkMode()

            val themeDefinition = when (theme) {
                IntUiThemes.Light -> JewelTheme.lightThemeDefinition(
                    defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),)
                )
                IntUiThemes.Dark -> JewelTheme.darkThemeDefinition(
                    defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),)
                )
                IntUiThemes.System ->
                    if (isSystemInDarkMode) {
                        JewelTheme.darkThemeDefinition(
                            defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),)
                        )
                    } else {
                        JewelTheme.lightThemeDefinition(
                            defaultTextStyle = TextStyle(fontFamily = FontFamily(Font(resource = Res.font.notoserifhebrew)),)
                        )
                    }
            }

            IntUiTheme(
                theme = themeDefinition,
                styling = ComponentStyling.default()
                    .decoratedWindow(
                        titleBarStyle = when (theme) {
                            IntUiThemes.Light -> TitleBarStyle.lightWithLightHeader()
                            IntUiThemes.Dark -> TitleBarStyle.dark()
                            IntUiThemes.System ->
                                if (isSystemInDarkMode) {
                                    TitleBarStyle.dark()
                                } else {
                                    TitleBarStyle.lightWithLightHeader()
                                }
                        },
                    )
            ) {
                DecoratedWindow(
                    onCloseRequest = { exitApplication() },
                    title = stringResource(Res.string.app_name),
//            icon = painterResource(Res.drawable.icon),
                    state = windowState,
                    visible = isWindowVisible,
                    onKeyEvent = { keyEvent ->
                        processKeyShortcuts(keyEvent = keyEvent, onNavigateTo = {
                            //TODO
                        })
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
                                        .padding(
                                            start =0.dp
                                        )
                                        .align(Alignment.Start)
                                        .width(windowWidth - if (isMacOs) iconWidth*(iconsNumber+2).dp else (iconWidth * iconsNumber).dp)
                                ) {
                                    TabsView()
                                }
                                Row(
                                    modifier = Modifier.align(Alignment.End).fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TitleBarActionsButtonsView()
                                }
                            }
                        }
                    }

                    Column(
                        modifier =
                            Modifier.trackActivation().fillMaxSize()
                                .background(JewelTheme.globalColors.panelBackground),
                    ) {
                        TabsNavHost()
                    }
                }
            }
        }
    }
}