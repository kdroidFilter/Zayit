package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.kdroidfilter.seforim.navigation.NavigationAnimations
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.features.onboarding.diskspace.AvailableDiskSpaceScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.finish.FinishScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.init.InitScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.licence.LicenceScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.offline.OfflineFileSelectionScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall.TypeOfInstallationScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.version.VersionVerificationScreen

@Composable
fun OnBoardingNavHost(navController: NavHostController) {
    Column(modifier = Modifier.fillMaxSize()) {
        val progressBarState = ProgressBarState
        val progress by progressBarState.progress.collectAsState()
        AnimatedHorizontalProgressBar(progress, Modifier.fillMaxWidth())
        NavHost(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            navController = navController,
            startDestination = OnBoardingDestination.InitScreen,
        ) {
            noAnimatedComposable<OnBoardingDestination.InitScreen> {
                InitScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.LicenceScreen> {
                LicenceScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.AvailableDiskSpaceScreen> {
                AvailableDiskSpaceScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.TypeOfInstallationScreen> {
                TypeOfInstallationScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.DatabaseOnlineInstallerScreen> {
                DownloadScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.OfflineFileSelectionScreen> {
                OfflineFileSelectionScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.ExtractScreen> {
                ExtractScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.VersionVerificationScreen> {
                VersionVerificationScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.FinishScreen> {
                FinishScreen()
            }
            noAnimatedComposable<OnBoardingDestination.UserProfilScreen> {
                UserProfileScreen(navController)
            }
            noAnimatedComposable<OnBoardingDestination.RegionConfigScreen> {
                RegionConfigScreen(navController)
            }
        }
    }
}

inline fun <reified T : OnBoardingDestination> NavGraphBuilder.noAnimatedComposable(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { NavigationAnimations.enterTransition(this) },
        exitTransition = { NavigationAnimations.exitTransition(this) },
        popEnterTransition = { NavigationAnimations.popEnterTransition(this) },
        popExitTransition = { NavigationAnimations.popExitTransition(this) },
    ) {
        content(it)
    }
}
