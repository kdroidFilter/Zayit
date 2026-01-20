package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatEta
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import io.github.kdroidfilter.seforimapp.icons.FileArrowDown
import io.github.kdroidfilter.seforimapp.icons.Speed
import io.github.kdroidfilter.seforimapp.icons.Timer
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultErrorBanner
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.defaultBannerStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_download_progress
import seforimapp.seforimapp.generated.resources.onboarding_downloading_message
import seforimapp.seforimapp.generated.resources.onboarding_error_occurred
import seforimapp.seforimapp.generated.resources.onboarding_error_with_detail
import seforimapp.seforimapp.generated.resources.retry_button

@Composable
fun DownloadScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    val viewModel: DownloadViewModel = metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()

    // Update top progress indicator baseline for this step
    LaunchedEffect(Unit) { progressBarState.setProgress(0.3f) }

    // While downloading, advance the main progress proportionally from Download -> Extract anchors
    LaunchedEffect(state.progress) {
        val anchored = 0.3f + (0.7f - 0.3f) * state.progress.coerceIn(0f, 1f)
        progressBarState.setProgress(anchored)
    }

    // Trigger download once when entering this screen
    LaunchedEffect(Unit) {
        if (!state.inProgress && !state.completed) {
            viewModel.onEvent(DownloadEvents.Start)
        }
    }

    // Clear back stack once download starts to prevent going back
    var backStackCleared by remember { mutableStateOf(false) }
    LaunchedEffect(state.inProgress) {
        if (!backStackCleared && state.inProgress) {
            backStackCleared = true
            // Clear back stack to prevent returning to installation type selection
            navController.navigate(OnBoardingDestination.DatabaseOnlineInstallerScreen) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Navigate to extraction when completed
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(state.completed) {
        if (!navigated && state.completed) {
            navigated = true
            // Snap to the start of the Extract step before navigating
            progressBarState.setProgress(0.7f)
            // Move forward and remove Download from back stack so back is disabled
            navController.navigate(OnBoardingDestination.ExtractScreen) {
                popUpTo<OnBoardingDestination.DatabaseOnlineInstallerScreen> { inclusive = true }
            }
        }
    }

    DownloadView(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun DownloadView(
    state: DownloadState,
    onEvent: (DownloadEvents) -> Unit = {},
) {
    OnBoardingScaffold(title = stringResource(Res.string.onboarding_downloading_message)) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Error banner with retry
            if (state.errorMessage != null) {
                val generic = stringResource(Res.string.onboarding_error_occurred)
                val detail = state.errorMessage.takeIf { it.isNotBlank() }
                val message = detail?.let { stringResource(Res.string.onboarding_error_with_detail, it) } ?: generic
                val retryLabel = stringResource(Res.string.retry_button)
                DefaultErrorBanner(
                    text = message,
                    style = JewelTheme.defaultBannerStyle.error,
                    linkActions = { action(retryLabel, onClick = { onEvent(DownloadEvents.Start) }) },
                )
            }

            Icon(
                Download_for_offline,
                null,
                modifier = Modifier.size(192.dp),
                tint = JewelTheme.globalColors.text.normal,
            )

            val downloadedText = formatBytes(state.downloadedBytes)
            val totalBytes = state.totalBytes
            val totalText = totalBytes?.let { formatBytes(it) }
            val speedBps = state.speedBytesPerSec
            val speedText = formatBytesPerSec(speedBps)

            totalText?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        FileArrowDown,
                        null,
                        tint = JewelTheme.globalColors.text.normal,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(Res.string.onboarding_download_progress, downloadedText, it),
                        modifier = Modifier.width(175.dp),
                        textAlign = TextAlign.End,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(Speed, null, tint = JewelTheme.globalColors.text.normal, modifier = Modifier.size(16.dp))
                    Text(
                        speedText,
                        modifier = Modifier.width(175.dp),
                        textAlign = TextAlign.End,
                    )
                }
                val etaSeconds =
                    if (speedBps > 0L) {
                        val remaining = (totalBytes - state.downloadedBytes).coerceAtLeast(0)
                        ((remaining + speedBps - 1) / speedBps)
                    } else {
                        null
                    }
                etaSeconds?.let { secs ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(Timer, null, tint = JewelTheme.globalColors.text.normal, modifier = Modifier.size(15.dp))
                        Text(
                            formatEta(secs),
                            modifier = Modifier.width(175.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun DownloadView_InProgress_Preview() {
    PreviewContainer {
        DownloadView(
            state =
                DownloadState(
                    inProgress = true,
                    progress = 0.42f,
                    downloadedBytes = 800L * 1024 * 1024,
                    totalBytes = 2L * 1024 * 1024 * 1024,
                    speedBytesPerSec = 8L * 1024 * 1024,
                    errorMessage = null,
                    completed = false,
                ),
        )
    }
}

@Composable
@Preview
private fun DownloadView_Completed_Preview() {
    PreviewContainer {
        DownloadView(
            state =
                DownloadState(
                    inProgress = false,
                    progress = 1f,
                    downloadedBytes = 2L * 1024 * 1024 * 1024,
                    totalBytes = 2L * 1024 * 1024 * 1024,
                    speedBytesPerSec = 0,
                    errorMessage = null,
                    completed = true,
                ),
        )
    }
}

@Composable
@Preview
private fun DownloadView_Error_Preview() {
    PreviewContainer {
        DownloadView(
            state =
                DownloadState(
                    inProgress = false,
                    progress = 0.13f,
                    downloadedBytes = 100L * 1024 * 1024,
                    totalBytes = 2L * 1024 * 1024 * 1024,
                    speedBytesPerSec = 0,
                    errorMessage = stringResource(Res.string.onboarding_error_occurred),
                    completed = false,
                ),
        )
    }
}
