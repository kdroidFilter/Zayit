package io.github.kdroidfilter.seforimapp.features.database.update.navigation

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
import io.github.kdroidfilter.seforimapp.features.database.update.screens.VersionCheckScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.UpdateOptionsScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.OnlineUpdateScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.OfflineUpdateScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.CompletionScreen

@Composable
fun DatabaseUpdateNavHost(
    navController: NavHostController,
    onUpdateCompleted: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        startDestination = DatabaseUpdateDestination.VersionCheckScreen
    ) {
        noAnimatedComposable<DatabaseUpdateDestination.VersionCheckScreen> {
            VersionCheckScreen(navController = navController)
        }
        
        noAnimatedComposable<DatabaseUpdateDestination.UpdateOptionsScreen> {
            UpdateOptionsScreen(navController = navController)
        }
        
        noAnimatedComposable<DatabaseUpdateDestination.OnlineUpdateScreen> {
            OnlineUpdateScreen(
                navController = navController,
                onUpdateCompleted = onUpdateCompleted
            )
        }
        
        noAnimatedComposable<DatabaseUpdateDestination.OfflineUpdateScreen> {
            OfflineUpdateScreen(
                navController = navController,
                onUpdateCompleted = onUpdateCompleted
            )
        }
        
        noAnimatedComposable<DatabaseUpdateDestination.CompletionScreen> {
            CompletionScreen(onUpdateCompleted = onUpdateCompleted)
        }
    }
}

inline fun <reified T : DatabaseUpdateDestination> NavGraphBuilder.noAnimatedComposable(
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