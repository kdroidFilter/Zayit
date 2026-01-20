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
import io.github.kdroidfilter.seforimapp.icons.CheckCircle
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys.General.Error
import seforimapp.seforimapp.generated.resources.*

@Composable
fun CompletionScreen(
    navController: NavController,
    onUpdateCompleted: () -> Unit,
) {
    var isVersionCompatible by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        DatabaseUpdateProgressBarState.setUpdateComplete()
        // Vérifier si la version de la base de données est maintenant compatible
        isVersionCompatible = DatabaseVersionManager.isDatabaseVersionCompatible()
    }

    when (isVersionCompatible) {
        true -> {
            // Version compatible - afficher écran de succès
            OnBoardingScaffold(title = stringResource(Res.string.db_update_completion_title)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    // Success icon
                    Icon(
                        CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    // Title
                    Text(
                        text = stringResource(Res.string.db_update_success_title),
                        textAlign = TextAlign.Center,
                    )

                    // Description
                    Text(
                        text = stringResource(Res.string.db_update_success_message),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    // Continue button
                    DefaultButton(
                        onClick = onUpdateCompleted,
                    ) {
                        Text(stringResource(Res.string.onboarding_open_app))
                    }
                }
            }
        }

        false -> {
            // Version incompatible - afficher écran d'erreur
            OnBoardingScaffold(title = stringResource(Res.string.db_update_version_error_title)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    // Error icon
                    Image(
                        Error,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )

                    // Description
                    Text(
                        text = stringResource(Res.string.db_update_version_error_message),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    // Retry button
                    DefaultButton(
                        onClick = {
                            navController.navigate(DatabaseUpdateDestination.UpdateOptionsScreen) {
                                // Clear the back stack up to UpdateOptionsScreen
                                popUpTo(DatabaseUpdateDestination.UpdateOptionsScreen) {
                                    inclusive = true
                                }
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.db_update_try_again))
                    }
                }
            }
        }

        null -> {
            // Vérification en cours
            OnBoardingScaffold(title = stringResource(Res.string.db_update_completion_title)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(Res.string.db_update_verifying_version),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
