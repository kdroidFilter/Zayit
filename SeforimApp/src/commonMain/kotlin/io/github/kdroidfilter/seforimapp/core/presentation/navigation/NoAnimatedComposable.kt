package io.github.kdroidfilter.seforimapp.core.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.kdroidfilter.seforim.navigation.NavigationAnimations

/**
 * Registers a type-safe destination with all navigation animations disabled.
 * Shared by the onboarding and database-update navigation graphs.
 */
inline fun <reified T : Any> NavGraphBuilder.noAnimatedComposable(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T>(
        enterTransition = { NavigationAnimations.enterTransition(this) },
        exitTransition = { NavigationAnimations.exitTransition(this) },
        popEnterTransition = { NavigationAnimations.popEnterTransition(this) },
        popExitTransition = { NavigationAnimations.popExitTransition(this) },
    ) {
        content(it)
    }
}
