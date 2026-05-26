package io.github.kdroidfilter.seforimapp.features.database.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.BasicTitleBar
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.TitleBarLayoutPolicy
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.core.presentation.utils.rememberWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateNavHost
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.icons.Deployed_code_update
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
import seforimapp.seforimapp.generated.resources.db_update_title_bar

@Composable
fun NucleusApplicationScope.DatabaseUpdateWindow(
    onUpdateComplete: () -> Unit = {},
    isDatabaseMissing: Boolean = false,
) {
    val updateWindowState = remember { getCenteredWindowState(720, 420) }
    JewelDecoratedWindow(
        onCloseRequest = { exitApplication() },
        title = stringResource(Res.string.app_name),
        icon = if (PlatformInfo.isMacOS) null else painterResource(Res.drawable.AppIcon),
        state = updateWindowState,
        visible = true,
        resizable = false,
    ) {
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
                layoutPolicy = TitleBarLayoutPolicy.FillCenter,
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
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Deployed_code_update,
                            contentDescription = null,
                            tint = JewelTheme.globalColors.text.normal,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(Res.string.db_update_title_bar))
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
                DatabaseUpdateNavHost(
                    navController = navController,
                    onUpdateComplete = onUpdateComplete,
                    isDatabaseMissing = isDatabaseMissing,
                )
            }
        }
    }
}
