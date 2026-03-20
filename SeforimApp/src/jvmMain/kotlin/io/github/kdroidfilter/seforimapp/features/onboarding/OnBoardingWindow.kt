package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.nucleus.window.ControlButtonsDirection
import io.github.kdroidfilter.nucleus.window.jewel.JewelDecoratedWindow
import io.github.kdroidfilter.nucleus.window.jewel.JewelTitleBar
import io.github.kdroidfilter.nucleus.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingNavHost
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.Install_desktop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.onboarding_title_bar

@Composable
fun ApplicationScope.OnBoardingWindow() {
    val onboardingWindowState = remember { getCenteredWindowState(720, 420) }
    JewelDecoratedWindow(
        onCloseRequest = { exitApplication() },
        title = stringResource(Res.string.app_name),
        icon = if (PlatformInfo.isMacOS) null else painterResource(Res.drawable.AppIcon),
        state = onboardingWindowState,
        visible = true,
        resizable = false,
    ) {
        /**
         * A hack to work around the window flashing its background color when closed
         * (https://youtrack.jetbrains.com/issue/CMP-5651).
         */
        val background = JewelTheme.globalColors.panelBackground
        LaunchedEffect(window, background) {
            window.background = java.awt.Color(background.toArgb())
        }

        val windowViewModelOwner = rememberWindowViewModelStoreOwner()
        CompositionLocalProvider(
            LocalWindowViewModelStoreOwner provides windowViewModelOwner,
            LocalViewModelStoreOwner provides windowViewModelOwner,
        ) {
            val isMac = PlatformInfo.isMacOS
            val isWindows = PlatformInfo.isWindows
            val navController = rememberNavController()
            var canNavigateBack by remember { mutableStateOf(false) }
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collect {
                    canNavigateBack = navController.previousBackStackEntry != null
                }
            }
            JewelTitleBar(modifier = Modifier.newFullscreenControls(), gradientStartColor = ThemeUtils.titleBarGradientColor(), controlButtonsDirection = ControlButtonsDirection.SystemNative) {
                // Keep the back button pinned to the start and
                // center the title (icon + text) regardless of OS/window controls.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(if (isMac) 0.9f else 1f)
                            .padding(start = if (isWindows) 70.dp else 0.dp),
                ) {
                    if (canNavigateBack) {
                        IconButton(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                                    .size(24.dp),
                            onClick = { navController.navigateUp() },
                        ) {
                            Icon(AllIconsKeys.Actions.Back, null, modifier = Modifier.rotate(180f))
                        }
                    }

                    val centerOffset = 40.dp

                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .offset(x = centerOffset),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Install_desktop,
                            contentDescription = null,
                            tint = JewelTheme.globalColors.text.normal,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(stringResource(Res.string.onboarding_title_bar))
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
                OnBoardingNavHost(navController = navController)
            }
        }
    }
}
