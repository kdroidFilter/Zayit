package io.github.kdroidfilter.seforimapp.features.onboarding.version

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.icons.CheckCircle
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.icons.AllIconsKeys.General.Error
import seforimapp.seforimapp.generated.resources.*

@Composable
fun VersionVerificationScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    var isVersionCompatible by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        // Anchor progress at the end of extraction
        progressBarState.setProgress(0.85f)
        // Vérifier si la version de la base de données est compatible
        isVersionCompatible = DatabaseVersionManager.isDatabaseVersionCompatible()
    }

    when (isVersionCompatible) {
        true -> {
            // Version compatible - naviguer vers le profil utilisateur
            LaunchedEffect(Unit) {
                navController.navigate(OnBoardingDestination.UserProfilScreen) {
                    popUpTo<OnBoardingDestination.VersionVerificationScreen> { inclusive = true }
                }
            }

            // Afficher un message de succès temporaire
            OnBoardingScaffold(title = stringResource(Res.string.onboarding_extracting_message)) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    Text(
                        text = stringResource(Res.string.onboarding_ready),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        false -> {
            // Version incompatible - afficher écran d'erreur
            OnBoardingScaffold(title = stringResource(Res.string.onboarding_version_error_title)) {
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

                    // Error title
                    Text(
                        text = stringResource(Res.string.onboarding_version_error_title),
                        textAlign = TextAlign.Center,
                    )

                    // Description
                    Text(
                        text = stringResource(Res.string.onboarding_version_error_message),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    // Restart installation button
                    DefaultButton(
                        onClick = {
                            navController.navigate(OnBoardingDestination.TypeOfInstallationScreen) {
                                // Clear the entire back stack and restart from installation type
                                popUpTo(0) {
                                    inclusive = true
                                }
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.onboarding_restart_installation))
                    }
                }
            }
        }

        null -> {
            // Vérification en cours
            OnBoardingScaffold(title = stringResource(Res.string.onboarding_extracting_message)) {
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
                        text = stringResource(Res.string.onboarding_verifying_version),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
