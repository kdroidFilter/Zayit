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
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatEta
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import io.github.kdroidfilter.seforimapp.icons.FileArrowDown
import io.github.kdroidfilter.seforimapp.icons.Speed
import io.github.kdroidfilter.seforimapp.icons.Timer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun OnlineUpdateScreen(
    navController: NavController,
    onUpdateCompleted: () -> Unit,
) {
    val downloadViewModel: DownloadViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val downloadState by downloadViewModel.state.collectAsState()
    val cleanupUseCase = LocalAppGraph.current.databaseCleanupUseCase
    val extractViewModel: ExtractViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val extractState by extractViewModel.state.collectAsState()

    var cleanupCompleted by remember { mutableStateOf(false) }
    var hasStartedExtraction by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Nettoyer les anciens fichiers de base de données avant de commencer
        cleanupUseCase.cleanupDatabaseFiles()
        cleanupCompleted = true
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
                !downloadState.inProgress && !downloadState.completed && downloadState.errorMessage == null -> {
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

                    val downloadedText = formatBytes(downloadState.downloadedBytes)
                    val totalBytes = downloadState.totalBytes
                    val totalText = totalBytes?.let { formatBytes(it) }
                    val speedBps = downloadState.speedBytesPerSec
                    val speedText = formatBytesPerSec(speedBps)

                    totalText?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                FileArrowDown,
                                contentDescription = null,
                                tint = JewelTheme.globalColors.text.normal,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(Res.string.onboarding_download_progress, downloadedText, it),
                                modifier = Modifier.width(175.dp),
                                textAlign = TextAlign.End,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                Speed,
                                contentDescription = null,
                                tint = JewelTheme.globalColors.text.normal,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = speedText,
                                modifier = Modifier.width(175.dp),
                                textAlign = TextAlign.End,
                            )
                        }

                        val etaSeconds =
                            if (speedBps > 0L) {
                                val remaining = (totalBytes - downloadState.downloadedBytes).coerceAtLeast(0)
                                ((remaining + speedBps - 1) / speedBps)
                            } else {
                                null
                            }

                        etaSeconds?.let { secs ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    Timer,
                                    contentDescription = null,
                                    tint = JewelTheme.globalColors.text.normal,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = formatEta(secs),
                                    modifier = Modifier.width(175.dp),
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
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
