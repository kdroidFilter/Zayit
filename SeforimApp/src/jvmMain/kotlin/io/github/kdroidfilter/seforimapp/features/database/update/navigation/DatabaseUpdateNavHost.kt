package io.github.kdroidfilter.seforimapp.features.database.update.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.navigation.noAnimatedComposable
import io.github.kdroidfilter.seforimapp.features.database.update.screens.CompletionScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.OfflineUpdateScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.OnlineUpdateScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.UpdateOptionsScreen
import io.github.kdroidfilter.seforimapp.features.database.update.screens.VersionCheckScreen

@Composable
fun DatabaseUpdateNavHost(
    navController: NavHostController,
    onUpdateComplete: () -> Unit = {},
    isDatabaseMissing: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val progressBarState = DatabaseUpdateProgressBarState
        val progress by progressBarState.progress.collectAsState()
        AnimatedHorizontalProgressBar(progress, Modifier.fillMaxWidth())

        NavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize().padding(16.dp),
            startDestination = DatabaseUpdateDestination.VersionCheckScreen,
        ) {
            noAnimatedComposable<DatabaseUpdateDestination.VersionCheckScreen> {
                VersionCheckScreen(
                    navController = navController,
                    isDatabaseMissing = isDatabaseMissing,
                )
            }

            noAnimatedComposable<DatabaseUpdateDestination.UpdateOptionsScreen> {
                UpdateOptionsScreen(navController = navController)
            }

            noAnimatedComposable<DatabaseUpdateDestination.OnlineUpdateScreen> {
                OnlineUpdateScreen(
                    navController = navController,
                    onUpdateComplete = onUpdateComplete,
                )
            }

            noAnimatedComposable<DatabaseUpdateDestination.OfflineUpdateScreen> {
                OfflineUpdateScreen(
                    navController = navController,
                    onUpdateComplete = onUpdateComplete,
                )
            }

            noAnimatedComposable<DatabaseUpdateDestination.CompletionScreen> {
                CompletionScreen(
                    navController = navController,
                    onUpdateComplete = onUpdateComplete,
                )
            }
        }
    }
}
