package io.github.kdroidfilter.seforimapp.features.database.update.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.*

@Composable
fun VersionCheckScreen(
    navController: NavController,
    isDatabaseMissing: Boolean = false,
) {
    val currentVersion = remember { DatabaseVersionManager.getCurrentDatabaseVersion() }
    val minRequiredVersion = remember { DatabaseVersionManager.getMinimumRequiredVersion() }

    LaunchedEffect(Unit) {
        DatabaseUpdateProgressBarState.resetProgress()
    }

    OnBoardingScaffold(
        title =
            stringResource(
                if (isDatabaseMissing) {
                    Res.string.db_update_reinstall_title
                } else {
                    Res.string.db_update_version_check_title
                },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            // Warning icon
            Image(
                AllIconsKeys.General.Warning,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )

            // Version information - only show when not missing database
            if (!isDatabaseMissing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                Res.string.db_update_current_version,
                                currentVersion?.let { DatabaseVersionManager.formatVersionForDisplay(it) }
                                    ?: stringResource(Res.string.db_update_version_unknown),
                            ),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text =
                            stringResource(
                                Res.string.db_update_required_version,
                                DatabaseVersionManager.formatVersionForDisplay(minRequiredVersion),
                            ),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Description
            Text(
                text =
                    if (isDatabaseMissing) {
                        stringResource(Res.string.db_update_database_missing_error)
                    } else {
                        stringResource(Res.string.db_update_need_to_update)
                    },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            // Continue button
            DefaultButton(
                onClick = {
                    navController.navigate(DatabaseUpdateDestination.UpdateOptionsScreen)
                },
            ) {
                Text(stringResource(Res.string.db_update_continue))
            }
        }
    }
}
