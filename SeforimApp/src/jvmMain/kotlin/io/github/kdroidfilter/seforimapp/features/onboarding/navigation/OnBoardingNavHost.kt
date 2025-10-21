package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.seforim.navigation.NavigationAnimations
import io.github.kdroidfilter.seforimapp.features.onboarding.screens.init.InitScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.screens.licence.LicenceScreen
import io.github.kdroidfilter.seforimapp.features.onboarding.screens.typeofinstall.TypeOfInstallationScreen

@Composable
fun OnBoardingNavHost(navController: NavHostController){

    NavHost(modifier = Modifier.fillMaxSize().padding(16.dp), navController = navController, startDestination = OnBoardingDestination.InitScreen) {
        noAnimatedComposable<OnBoardingDestination.InitScreen> { InitScreen(navController) }
        noAnimatedComposable<OnBoardingDestination.LicenceScreen> { LicenceScreen(navController) }
        noAnimatedComposable<OnBoardingDestination.TypeOfInstallationScreen> {TypeOfInstallationScreen(navController)}
    }

}

inline fun <reified T : OnBoardingDestination> NavGraphBuilder.noAnimatedComposable(
    noinline content: @Composable (NavBackStackEntry) -> Unit
) {
    composable<T>(
        enterTransition = { NavigationAnimations.enterTransition(this) },
        exitTransition = { NavigationAnimations.exitTransition(this) },
        popEnterTransition = { NavigationAnimations.popEnterTransition(this) },
        popExitTransition = { NavigationAnimations.popExitTransition(this) }
    ) {
        content(it)
    }
}