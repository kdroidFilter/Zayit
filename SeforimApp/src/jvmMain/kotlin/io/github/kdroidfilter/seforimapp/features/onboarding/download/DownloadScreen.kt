package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatEta
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.DefaultErrorBanner
import org.jetbrains.jewel.foundation.theme.JewelTheme
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
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: DownloadViewModel = LocalAppGraph.current.downloadViewModel
    val state by viewModel.state.collectAsState()

    // Update top progress indicator for this step
    LaunchedEffect(Unit) { progressBarState.setProgress(0.4f) }

    // Trigger download once when entering this screen
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(state.inProgress, state.completed) {
        if (!started && !state.inProgress && !state.completed) {
            started = true
            viewModel.onEvent(DownloadEvents.Start)
        }
    }

    // Navigate to extraction when completed
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(state.completed) {
        if (!navigated && state.completed) {
            navigated = true
            progressBarState.setProgress(0.6f)
            navController.navigate(OnBoardingDestination.ExtractScreen)
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
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Error banner with retry
            if (state.errorMessage != null) {
                val generic = stringResource(Res.string.onboarding_error_occurred)
                val detail = state.errorMessage?.takeIf { it.isNotBlank() }
                val message = detail?.let { stringResource(Res.string.onboarding_error_with_detail, it) } ?: generic
                val retryLabel = stringResource(Res.string.retry_button)
                DefaultErrorBanner(
                    text = message,
                    style = JewelTheme.defaultBannerStyle.error,
                    linkActions = { action(retryLabel, onClick = { onEvent(DownloadEvents.Start) }) }
                )
            }

            HorizontalProgressBar(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            val downloadedText = formatBytes(state.downloadedBytes)
            val totalBytes = state.totalBytes
            val totalText = totalBytes?.let { formatBytes(it) }
            val speedBps = state.speedBytesPerSec
            val speedText = formatBytesPerSec(speedBps)

            totalText?.let {
                Text(stringResource(Res.string.onboarding_download_progress, downloadedText, it))
                Text(speedText)
                val etaSeconds = if (speedBps > 0L) {
                    val remaining = (totalBytes!! - state.downloadedBytes).coerceAtLeast(0)
                    ((remaining + speedBps - 1) / speedBps)
                } else null
                etaSeconds?.let { secs ->
                    Text(formatEta(secs))
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
            state = DownloadState(
                inProgress = true,
                progress = 0.42f,
                downloadedBytes = 800L * 1024 * 1024,
                totalBytes = 2L * 1024 * 1024 * 1024,
                speedBytesPerSec = 8L * 1024 * 1024,
                errorMessage = null,
                completed = false
            )
        )
    }
}

@Composable
@Preview
private fun DownloadView_Completed_Preview() {
    PreviewContainer {
        DownloadView(
            state = DownloadState(
                inProgress = false,
                progress = 1f,
                downloadedBytes = 2L * 1024 * 1024 * 1024,
                totalBytes = 2L * 1024 * 1024 * 1024,
                speedBytesPerSec = 0,
                errorMessage = null,
                completed = true
            )
        )
    }
}

@Composable
@Preview
private fun DownloadView_Error_Preview() {
    PreviewContainer {
        DownloadView(
            state = DownloadState(
                inProgress = false,
                progress = 0.13f,
                downloadedBytes = 100L * 1024 * 1024,
                totalBytes = 2L * 1024 * 1024 * 1024,
                speedBytesPerSec = 0,
                errorMessage = stringResource(Res.string.onboarding_error_occurred),
                completed = false
            )
        )
    }
}
