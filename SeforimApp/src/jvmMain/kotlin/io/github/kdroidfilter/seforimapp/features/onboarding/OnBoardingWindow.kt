package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.window.ApplicationScope
import androidx.navigation.compose.rememberNavController
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
            Row(
                modifier = Modifier.fillMaxWidth(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (canNavigateBack) {
                    IconButton(
                        modifier = Modifier.padding(start = 8.dp).size(24.dp),
                        onClick = {
                            navController.navigateUp()
                        }
                    ) {
                        Icon(AllIconsKeys.Actions.Back, null, modifier = Modifier.rotate(180f))
                    }
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(0.58f)
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