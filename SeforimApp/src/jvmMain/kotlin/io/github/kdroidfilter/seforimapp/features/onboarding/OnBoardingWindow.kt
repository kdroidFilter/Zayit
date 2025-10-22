package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.ApplicationScope
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingNavHost
import io.github.kdroidfilter.seforimapp.icons.Install_desktop
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.app_name
import seforimapp.seforimapp.generated.resources.onboarding_title_bar
import seforimapp.seforimapp.generated.resources.zayit_transparent

@Composable
fun ApplicationScope.OnBoardingWindow() {
    val onboardingWindowState = remember { getCenteredWindowState(720, 420) }
    DecoratedWindow(
        onCloseRequest = { exitApplication() },
        title = stringResource(Res.string.app_name),
        icon = painterResource(Res.drawable.zayit_transparent),
        state = onboardingWindowState,
        visible = true,
        resizable = false,
    ) {

        val navController = rememberNavController()
        var canNavigateBack by remember { mutableStateOf(false) }
        LaunchedEffect(navController) {
            navController.currentBackStackEntryFlow.collect {
                canNavigateBack = navController.previousBackStackEntry != null
            }
        }
        TitleBar(modifier = Modifier.newFullscreenControls()) {
            // Keep the back button pinned to the start and
            // center the title (icon + text) regardless of OS/window controls.
            Box(modifier = Modifier.fillMaxWidth()) {
                if (canNavigateBack) {
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .size(24.dp),
                        onClick = { navController.navigateUp() }
                    ) {
                        Icon(AllIconsKeys.Actions.Back, null, modifier = Modifier.rotate(180f))
                    }
                }

                // Apply an OS-aware horizontal offset so the center is visually centered
                val iconSlot = 40.dp // approximate width of a native control/icon slot
                val osShift = when (getOperatingSystem()) {
                    OperatingSystem.MACOS -> iconSlot * 1.5f  // 3 controls on left → shift right by half cluster
                    OperatingSystem.WINDOWS -> iconSlot * -1.5f // 3 controls on right → shift left by half cluster
                    else -> 40.dp
                }
                // Slight extra nudge when back button is visible on macOS (left-weighted)
                val backShift = if (getOperatingSystem() == OperatingSystem.MACOS && canNavigateBack) 8.dp else 0.dp
                val centerOffset = osShift + backShift

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = centerOffset),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Install_desktop,
                        contentDescription = null,
                        tint = JewelTheme.globalColors.text.normal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(stringResource(Res.string.onboarding_title_bar))
                }
            }
        }
        Column(
            modifier = Modifier
                .trackActivation()
                .fillMaxSize()
                .background(JewelTheme.globalColors.panelBackground),
        ) {
            OnBoardingNavHost(navController = navController)
        }
    }
}
