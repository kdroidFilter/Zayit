package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_downloading_message
import seforimapp.seforimapp.generated.resources.onboarding_extracting_message
import seforimapp.seforimapp.generated.resources.onboarding_open_app
import seforimapp.seforimapp.generated.resources.onboarding_ready
import seforimapp.seforimapp.generated.resources.onboarding_error_occurred

@Composable
fun OnBoardingScreen() {
    val viewModel: OnBoardingViewModel = LocalAppGraph.current.onBoardingViewModel
    val state by viewModel.state.collectAsState()
    OnBoardingView(state, viewModel::onEvent)
}

@Composable
private fun OnBoardingView(state: OnBoardingState, onEvent: (OnBoardingEvents) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        when {
            state.errorMessage != null -> {
                val generic = stringResource(Res.string.onboarding_error_occurred)
                val detail = state.errorMessage.takeIf { it.isNotBlank() }
                val message = detail?.let { "$generic: $it" } ?: generic
                Text(message, color = JewelTheme.globalColors.text.error)
            }
            state.downloadingInProgress -> {
                OnboardingText(stringResource(Res.string.onboarding_downloading_message))
                HorizontalProgressBar(
                    progress = state.downloadProgress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            state.extractingInProgress -> {
                OnboardingText(stringResource(Res.string.onboarding_extracting_message))
                HorizontalProgressBar(
                    progress = state.extractProgress,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            state.isDatabaseLoaded -> {
                OnboardingText(stringResource(Res.string.onboarding_ready))
                DefaultButton({}) {
                    Text(stringResource(Res.string.onboarding_open_app))
                }
            }
            else -> {
                // Idle or initial state; no UI content
            }
        }

    }

}

@Composable
private fun OnboardingText(text: String, color : Color = Color.Unspecified) {
    Text(text, modifier = Modifier.padding(bottom = 16.dp), color = color, fontSize = JewelTheme.typography.h1TextStyle.fontSize)
}

// Previews
@Preview(name = "Loaded", showBackground = true)
@Composable
private fun Preview_OnBoarding_Loaded() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewLoaded) { }
    }
}

@Preview(name = "Downloading", showBackground = true)
@Composable
private fun Preview_OnBoarding_Downloading() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewDownloading) { }
    }
}

@Preview(name = "Extracting", showBackground = true)
@Composable
private fun Preview_OnBoarding_Extracting() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewExtracting) { }
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun Preview_OnBoarding_Error() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewError) { }
    }
}