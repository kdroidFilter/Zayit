package io.github.kdroidfilter.seforimapp.features.onboarding.offline

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.database.update.DatabasePreparationUseCase
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadErrorKind
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun OfflineFileSelectionScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    LaunchedEffect(Unit) {
        progressBarState.setProgress(0.5f)
    }

    val extractViewModel: ExtractViewModel = metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val processRepository: OnboardingProcessRepository = LocalAppGraph.current.onboardingProcessRepository
    val prepUseCase = LocalAppGraph.current.databasePreparationUseCase

    var part01Path by remember { mutableStateOf<String?>(null) }
    var hasStartedExtraction by remember { mutableStateOf(false) }
    var prepErrorKind by remember { mutableStateOf<DownloadErrorKind?>(null) }
    val scope = rememberCoroutineScope()

    // Function to start extraction with part01 path
    fun startExtraction(
        @StructuredScope scope: CoroutineScope,
        p1: String,
    ) {
        scope.launch {
            prepErrorKind = null
            // Remove the old database and verify free space before extracting ~7.5 GB.
            when (prepUseCase.prepareForInstall()) {
                DatabasePreparationUseCase.Result.Ready -> {
                    // Start extraction with part01 path; ExtractUseCase discovers part02 automatically
                    progressBarState.setProgress(0.7f)
                    processRepository.setPendingZstPath(p1)
                    extractViewModel.onEvent(ExtractEvents.StartIfPending)
                    hasStartedExtraction = true

                    // Move forward and clear all previous onboarding steps so back is disabled
                    navController.navigate(OnBoardingDestination.ExtractScreen) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is DatabasePreparationUseCase.Result.CleanupFailed ->
                    prepErrorKind = DownloadErrorKind.CLEANUP_FAILED
                is DatabasePreparationUseCase.Result.InsufficientSpace ->
                    prepErrorKind = DownloadErrorKind.INSUFFICIENT_SPACE
            }
        }
    }

    fun pickFiles(
        @StructuredScope scope: CoroutineScope,
    ) {
        scope.launch {
            val p1 = pickDatabaseParts { part01Path = it }
            if (p1 != null) startExtraction(scope, p1)
        }
    }

    OnBoardingScaffold(title = stringResource(Res.string.onboarding_file_selection)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                io.github.kdroidfilter.seforimapp.icons.Unarchive,
                contentDescription = null,
                modifier = Modifier.size(192.dp),
                tint = JewelTheme.globalColors.text.normal,
            )

            Text(
                text = stringResource(Res.string.onboarding_file_selection),
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(Res.string.onboarding_select_files_message),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            if (part01Path != null) {
                Text(
                    text = stringResource(Res.string.onboarding_part01_selected),
                    textAlign = TextAlign.Center,
                    color = JewelTheme.globalColors.text.normal,
                )
            }

            if (prepErrorKind != null) {
                val kind = prepErrorKind
                Text(
                    text =
                        when (kind) {
                            DownloadErrorKind.CLEANUP_FAILED -> stringResource(Res.string.db_install_cleanup_failed)
                            DownloadErrorKind.INSUFFICIENT_SPACE -> stringResource(Res.string.db_install_insufficient_space)
                            null -> ""
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }

            DefaultButton(
                onClick = { pickFiles(scope) },
            ) {
                Text(stringResource(Res.string.onboarding_choose_files))
            }
        }
    }
}
