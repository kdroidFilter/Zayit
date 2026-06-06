package io.github.kdroidfilter.seforimapp.features.onboarding.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultErrorBanner
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.theme.defaultBannerStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.db_install_cleanup_failed
import seforimapp.seforimapp.generated.resources.db_install_insufficient_space
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
            // Error banner with retry (pre-download gate failure or download error)
            if (state.errorKind != null || state.errorMessage != null) {
                val message =
                    when (state.errorKind) {
                        DownloadErrorKind.CLEANUP_FAILED -> stringResource(Res.string.db_install_cleanup_failed)
                        DownloadErrorKind.INSUFFICIENT_SPACE -> stringResource(Res.string.db_install_insufficient_space)
                        null -> {
                            val detail = state.errorMessage?.takeIf { it.isNotBlank() }
                            detail?.let { stringResource(Res.string.onboarding_error_with_detail, it) }
                                ?: stringResource(Res.string.onboarding_error_occurred)
                        }
                    }
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

            DownloadProgressDetails(
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                speedBytesPerSec = state.speedBytesPerSec,
            )
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
