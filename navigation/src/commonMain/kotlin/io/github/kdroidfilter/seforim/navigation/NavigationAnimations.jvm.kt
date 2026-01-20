package io.github.kdroidfilter.seforim.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry

/**
 * JVM implementation of navigation animations
 * Returns None for all transitions to disable animations completely
 */
object NavigationAnimations {
    fun enterTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        return EnterTransition.None // Pas d'animation d'entrée
    }

    fun exitTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        return ExitTransition.None // Pas d'animation de sortie
    }

    fun popEnterTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
        return EnterTransition.None // Pas d'animation d'entrée lors du retour
    }

    fun popExitTransition(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
        return ExitTransition.None // Pas d'animation de sortie lors du retour
    }
}
