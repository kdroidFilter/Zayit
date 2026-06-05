package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.runtime.Composable
import dev.nucleusframework.application.NucleusApplicationScope
import io.github.kdroidfilter.seforimapp.core.presentation.components.InstallerWindow
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingNavHost
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.icons.Install_desktop
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_title_bar

@Composable
fun NucleusApplicationScope.OnBoardingWindow() {
    InstallerWindow(
        titleBarIcon = Install_desktop,
        titleBarText = stringResource(Res.string.onboarding_title_bar),
        progress = ProgressBarState.progress,
    ) { navController ->
        OnBoardingNavHost(navController = navController)
    }
}
