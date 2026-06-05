package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.taskbarprogress.tao.hideTaskbarProgress
import dev.nucleusframework.taskbarprogress.tao.showTaskbarProgress
import dev.nucleusframework.window.BasicTitleBar
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.TitleBarLayoutPolicy
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name

/**
 * Fixed-size decorated window shared by the onboarding and database-update flows.
 * Provides the window-scoped [androidx.lifecycle.ViewModelStoreOwner], a title bar with
 * a contextual icon/label and an automatic back button, and a NavHost-ready content area.
 *
 * The OS taskbar/dock progress indicator is kept in sync with [progress]: it mirrors
 * the value while in `(0f, 1f]` and is cleared at 0 and on window disposal.
 *
 * @param titleBarIcon icon shown next to the title bar label
 * @param titleBarText title bar label
 * @param progress installation progress in `[0f, 1f]`, mirrored to the taskbar
 * @param content receives the [NavHostController] backing the window's navigation graph
 */
@Composable
fun NucleusApplicationScope.InstallerWindow(
    titleBarIcon: ImageVector,
    titleBarText: String,
    progress: StateFlow<Float>,
    content: @Composable (navController: NavHostController) -> Unit,
) {
    val windowState = remember { getCenteredWindowState(720, 420) }
    JewelDecoratedWindow(
        onCloseRequest = { exitApplication() },
        title = stringResource(Res.string.app_name),
        icon = if (PlatformInfo.isMacOS) null else painterResource(Res.drawable.AppIcon),
        state = windowState,
        visible = true,
        resizable = false,
    ) {
        // Mirror the in-app progress bar onto the OS taskbar/dock indicator.
        val taskbarWindow = nucleusWindow
        val taskbarProgress by progress.collectAsState()
        LaunchedEffect(taskbarWindow, taskbarProgress) {
            if (taskbarProgress > 0f) {
                taskbarWindow.showTaskbarProgress(taskbarProgress.toDouble())
            } else {
                taskbarWindow.hideTaskbarProgress()
            }
        }
        DisposableEffect(taskbarWindow) {
            onDispose { taskbarWindow.hideTaskbarProgress() }
        }

        val windowViewModelOwner = rememberWindowViewModelStoreOwner()
        CompositionLocalProvider(
            LocalWindowViewModelStoreOwner provides windowViewModelOwner,
            LocalViewModelStoreOwner provides windowViewModelOwner,
        ) {
            val navController = rememberNavController()
            var canNavigateBack by remember { mutableStateOf(false) }
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect {
                    canNavigateBack = navController.previousBackStackEntry != null
                }
            }

            val titleBarStyle = LocalTitleBarStyle.current
            BasicTitleBar(
                modifier = Modifier.newFullscreenControls(),
                gradientStartColor = ThemeUtils.titleBarGradientColor(),
                style = titleBarStyle,
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
                layoutPolicy = TitleBarLayoutPolicy.Default,
            ) {
                CompositionLocalProvider(LocalContentColor provides titleBarStyle.colors.content) {
                    if (canNavigateBack) {
                        IconButton(
                            modifier =
                                Modifier
                                    .align(Alignment.Start)
                                    .padding(start = 8.dp)
                                    .size(24.dp),
                            onClick = { navController.navigateUp() },
                        ) {
                            Icon(AllIconsKeys.Actions.Back, null, modifier = Modifier.rotate(180f))
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            titleBarIcon,
                            contentDescription = null,
                            tint = JewelTheme.globalColors.text.normal,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(titleBarText)
                    }
                }
            }
            Column(
                modifier =
                    Modifier
                        .trackActivation()
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground),
            ) {
                content(navController)
            }
        }
    }
}
