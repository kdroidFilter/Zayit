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
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadErrorKind
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadProgressDetails
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun OnlineUpdateScreen(
    navController: NavController,
    onUpdateComplete: () -> Unit,
) {
    val downloadViewModel: DownloadViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val downloadState by downloadViewModel.state.collectAsState()
    val extractViewModel: ExtractViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val extractState by extractViewModel.state.collectAsState()

    var hasStartedExtraction by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Cleanup of the old database + disk-space gate run inside DownloadViewModel
        // before the transfer starts (see DatabasePreparationUseCase).
        DatabaseUpdateProgressBarState.setDownloadStarted()
        downloadViewModel.onEvent(DownloadEvents.Start)
    }

    LaunchedEffect(downloadState) {
        if (downloadState.inProgress) {
            DatabaseUpdateProgressBarState.setDownloadProgress(downloadState.progress)
        }
        // When download completes, start extraction
        if (downloadState.completed && !hasStartedExtraction) {
            hasStartedExtraction = true
            extractViewModel.onEvent(ExtractEvents.StartIfPending)
        }
    }

    // Propagate extraction progress and navigate once finished
    LaunchedEffect(extractState) {
        if (extractState.inProgress) {
            DatabaseUpdateProgressBarState.setDownloadProgress(extractState.progress)
        }
        if (extractState.completed) {
            DatabaseUpdateProgressBarState.setUpdateComplete()
            navController.navigate(DatabaseUpdateDestination.CompletionScreen) {
                popUpTo<DatabaseUpdateDestination.OnlineUpdateScreen> { inclusive = true }
            }
        }
    }

    OnBoardingScaffold(title = stringResource(Res.string.db_update_downloading_title)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !downloadState.inProgress &&
                    !downloadState.completed &&
                    downloadState.errorMessage == null &&
                    downloadState.errorKind == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text(
                            text = stringResource(Res.string.db_update_preparing_download),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                downloadState.inProgress -> {
                    Icon(
                        Download_for_offline,
                        contentDescription = null,
                        modifier = Modifier.size(192.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_downloading),
                        textAlign = TextAlign.Center,
                    )

                    DownloadProgressDetails(
                        downloadedBytes = downloadState.downloadedBytes,
                        totalBytes = downloadState.totalBytes,
                        speedBytesPerSec = downloadState.speedBytesPerSec,
                    )
                }
                // Download completed: show extraction state
                downloadState.completed && extractState.errorMessage == null && !extractState.completed -> {
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

                    if (extractState.inProgress) {
                        Text(
                            text = "${(extractState.progress * 100).toInt()}%",
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        // Waiting for extraction to start
                        CircularProgressIndicator()
                    }
                }

                // Pre-download gate failed (old DB locked, or not enough disk space)
                downloadState.errorKind != null -> {
                    val kind = downloadState.errorKind
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
                            onClick = { downloadViewModel.onEvent(DownloadEvents.Start) },
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }

                downloadState.errorMessage != null -> {
                    Text(
                        text = stringResource(Res.string.db_update_download_error),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = downloadState.errorMessage ?: stringResource(Res.string.db_update_download_error_unknown),
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
                                downloadViewModel.onEvent(DownloadEvents.Start)
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }

                // Extraction error
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
                                extractViewModel.onEvent(ExtractEvents.StartIfPending)
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }
            }
        }
    }
}
