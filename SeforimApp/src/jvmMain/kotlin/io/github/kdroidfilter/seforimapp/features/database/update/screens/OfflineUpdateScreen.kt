package io.github.kdroidfilter.seforimapp.features.database.update.screens

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
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadErrorKind
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.offline.pickDatabaseParts
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
fun OfflineUpdateScreen(
    navController: NavController,
    onUpdateComplete: () -> Unit,
) {
    val extractViewModel: ExtractViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val extractState by extractViewModel.state.collectAsState()
    val processRepository: OnboardingProcessRepository = LocalAppGraph.current.onboardingProcessRepository
    val prepUseCase = LocalAppGraph.current.databasePreparationUseCase

    var part01Path by remember { mutableStateOf<String?>(null) }
    var hasStartedExtraction by remember { mutableStateOf(false) }
    var prepErrorKind by remember { mutableStateOf<DownloadErrorKind?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(extractState) {
        if (extractState.inProgress) {
            DatabaseUpdateProgressBarState.setDownloadProgress(extractState.progress)
        }
        if (extractState.completed && hasStartedExtraction) {
            DatabaseUpdateProgressBarState.setUpdateComplete()
            navController.navigate(DatabaseUpdateDestination.CompletionScreen) {
                popUpTo<DatabaseUpdateDestination.OfflineUpdateScreen> { inclusive = true }
            }
        }
    }

    fun startUpdate(
        @StructuredScope scope: CoroutineScope,
        p1: String,
    ) {
        scope.launch {
            prepErrorKind = null
            // Remove the old database and verify free space before extracting ~7.5 GB.
            when (prepUseCase.prepareForInstall()) {
                DatabasePreparationUseCase.Result.Ready -> {
                    // Start extraction with part01 path; ExtractUseCase discovers part02 automatically
                    DatabaseUpdateProgressBarState.setDownloadStarted()
                    processRepository.setPendingZstPath(p1)
                    extractViewModel.onEvent(ExtractEvents.StartIfPending)
                    hasStartedExtraction = true
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
            if (p1 != null) startUpdate(scope, p1)
        }
    }

    OnBoardingScaffold(title = stringResource(Res.string.db_update_offline_title)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // Pre-extraction gate failed (old DB locked, or not enough disk space)
                prepErrorKind != null -> {
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

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                        ) {
                            Text(stringResource(Res.string.db_update_back))
                        }

                        DefaultButton(
                            onClick = {
                                prepErrorKind = null
                                part01Path = null
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }

                !hasStartedExtraction -> {
                    // File selection phase
                    Icon(
                        io.github.kdroidfilter.seforimapp.icons.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(192.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_file_selection),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_select_files_message),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    if (part01Path != null) {
                        Text(
                            text = stringResource(Res.string.db_update_part01_selected),
                            textAlign = TextAlign.Center,
                            color = JewelTheme.globalColors.text.normal,
                        )
                    }

                    DefaultButton(
                        onClick = { pickFiles(scope) },
                    ) {
                        Text(stringResource(Res.string.db_update_choose_files))
                    }
                }

                extractState.inProgress -> {
                    Icon(
                        io.github.kdroidfilter.seforimapp.icons.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(192.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_extracting),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "${(extractState.progress * 100).toInt()}%",
                        textAlign = TextAlign.Center,
                    )
                }

                extractState.completed -> {
                    Text(
                        text = stringResource(Res.string.db_update_extraction_completed),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_download_success_message),
                        textAlign = TextAlign.Center,
                    )
                }

                extractState.errorMessage != null -> {
                    Text(
                        text = stringResource(Res.string.db_update_extraction_error),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = extractState.errorMessage ?: stringResource(Res.string.db_update_download_error_unknown),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                navController.popBackStack()
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_back))
                        }

                        DefaultButton(
                            onClick = {
                                hasStartedExtraction = false
                                part01Path = null
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
