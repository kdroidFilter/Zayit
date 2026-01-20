package io.github.kdroidfilter.seforimapp.features.settings.navigation

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
import io.github.kdroidfilter.seforim.navigation.NavigationAnimations
import io.github.kdroidfilter.seforimapp.features.settings.ui.AboutSettingsScreen
import io.github.kdroidfilter.seforimapp.features.settings.ui.ConditionsSettingsScreen
import io.github.kdroidfilter.seforimapp.features.settings.ui.FontsSettingsScreen
import io.github.kdroidfilter.seforimapp.features.settings.ui.GeneralSettingsScreen
import io.github.kdroidfilter.seforimapp.features.settings.ui.ProfileSettingsScreen

@Composable
fun SettingsNavHost(navController: NavHostController) {
    NavHost(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        navController = navController,
        startDestination = SettingsDestination.General,
    ) {
        noAnimatedComposable<SettingsDestination.General> {
            GeneralSettingsScreen()
        }
        noAnimatedComposable<SettingsDestination.Profile> {
            ProfileSettingsScreen()
        }
        noAnimatedComposable<SettingsDestination.Fonts> {
            FontsSettingsScreen()
        }
        noAnimatedComposable<SettingsDestination.About> {
            AboutSettingsScreen()
        }
        noAnimatedComposable<SettingsDestination.Conditions> {
            ConditionsSettingsScreen()
        }
    }
}

inline fun <reified T : SettingsDestination> NavGraphBuilder.noAnimatedComposable(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { NavigationAnimations.enterTransition(this) },
        exitTransition = { NavigationAnimations.exitTransition(this) },
        popEnterTransition = { NavigationAnimations.popEnterTransition(this) },
        popExitTransition = { NavigationAnimations.popExitTransition(this) },
    ) { entry ->
        content(entry)
    }
}
