package io.github.kdroidfilter.seforimapp.features.onboarding.extract

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
import seforimapp.seforimapp.generated.resources.onboarding_error_occurred
import seforimapp.seforimapp.generated.resources.onboarding_error_with_detail
import seforimapp.seforimapp.generated.resources.onboarding_extracting_message
import seforimapp.seforimapp.generated.resources.retry_button

@Composable
fun ExtractScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: ExtractViewModel = LocalAppGraph.current.extractViewModel
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { progressBarState.setProgress(0.8f) }

    // Kick off extraction if a pending path exists
    LaunchedEffect(Unit) { viewModel.onEvent(ExtractEvents.StartIfPending) }

    // Navigate to finish when DB is ready
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(state.completed) {
        if (!navigated && state.completed) {
            navigated = true
            progressBarState.setProgress(1f)
            navController.navigate(OnBoardingDestination.FinishScreen)
        }
    }

    ExtractView(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun ExtractView(
    state: ExtractState,
    onEvent: (ExtractEvents) -> Unit = {},
) {
    OnBoardingScaffold(title = stringResource(Res.string.onboarding_extracting_message)) {
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
                    linkActions = { action(retryLabel, onClick = { onEvent(ExtractEvents.StartIfPending) }) }
                )
            }

            HorizontalProgressBar(
                progress = state.progress,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
@Preview
private fun ExtractView_InProgress_Preview() {
    PreviewContainer {
        ExtractView(
            state = ExtractState(
                inProgress = true,
                progress = 0.73f,
                errorMessage = null,
                completed = false
            )
        )
    }
}

@Composable
@Preview
private fun ExtractView_Done_Preview() {
    PreviewContainer {
        ExtractView(
            state = ExtractState(
                inProgress = false,
                progress = 1f,
                errorMessage = null,
                completed = true
            )
        )
    }
}

@Composable
@Preview
private fun ExtractView_Error_Preview() {
    PreviewContainer {
        ExtractView(
            state = ExtractState(
                inProgress = false,
                progress = 0.2f,
                errorMessage = stringResource(Res.string.onboarding_error_occurred),
                completed = false
            )
        )
    }
}
