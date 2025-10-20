package io.github.kdroidfilter.seforimapp.features.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.kdroidfilter.seforim.navigation.NavigationAnimations
import io.github.kdroidfilter.seforimapp.features.onboarding.screens.init.InitScreen

@Composable
fun OnBoardingNavHost(){
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = OnBoardingDestination.InitScreen) {
        noAnimatedComposable<OnBoardingDestination.InitScreen> { InitScreen() }


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