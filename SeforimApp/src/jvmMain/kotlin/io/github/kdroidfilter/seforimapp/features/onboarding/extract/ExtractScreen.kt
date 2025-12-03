package io.github.kdroidfilter.seforimapp.features.onboarding.extract

import androidx.compose.ui.tooling.preview.Preview
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
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Unarchive
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultErrorBanner
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.defaultBannerStyle
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
import seforimapp.seforimapp.generated.resources.*

@Composable
fun ExtractScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: ExtractViewModel = LocalAppGraph.current.extractViewModel
    val state by viewModel.state.collectAsState()

    // Anchor main progress at the start of the Extract step
    LaunchedEffect(Unit) { progressBarState.setProgress(0.7f) }

    // While extracting, advance the main progress proportionally from Extract -> User profile anchors
    LaunchedEffect(state.progress) {
        val anchored = 0.7f + (0.85f - 0.7f) * state.progress.coerceIn(0f, 1f)
        progressBarState.setProgress(anchored)
    }

    // Kick off extraction if a pending path exists
    LaunchedEffect(Unit) { viewModel.onEvent(ExtractEvents.StartIfPending) }

    // Navigate to version verification when DB extraction is ready
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(state.completed) {
        if (!navigated && state.completed) {
            navigated = true
            // Continue to version verification step and remove Extract from back stack to disable back navigation
            navController.navigate(OnBoardingDestination.VersionVerificationScreen) {
                popUpTo<OnBoardingDestination.ExtractScreen> { inclusive = true }
            }
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
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    linkActions = { action(retryLabel, onClick = { onEvent(ExtractEvents.StartIfPending) }) }
                )
            }
            Icon(
                Unarchive,
                null,
                modifier = Modifier.size(192.dp),
                tint = JewelTheme.globalColors.text.normal
            )
            
            if (state.inProgress) {
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    textAlign = TextAlign.Center
                )
            }

            InlineInformationBanner(
                style = JewelTheme.inlineBannerStyle.information,
                text = stringResource(Res.string.onboarding_extracting_did_you_know),
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
