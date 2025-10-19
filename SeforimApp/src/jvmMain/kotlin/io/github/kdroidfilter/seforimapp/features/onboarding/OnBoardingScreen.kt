package io.github.kdroidfilter.seforimapp.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytesPerSec
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatEta
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.*

@Composable
fun OnBoardingScreen(onFinish: () -> Unit = {}) {
    val viewModel: OnBoardingViewModel = LocalAppGraph.current.onBoardingViewModel
    val state by viewModel.state.collectAsState()
    OnBoardingView(state, viewModel::onEvent, onFinish)
}

@Composable
private fun OnBoardingView(
    state: OnBoardingState,
    onEvent: (OnBoardingEvents) -> Unit,
    onFinish: () -> Unit
) {
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
                val downloadedText = formatBytes(state.downloadedBytes)
                val totalBytes = state.downloadTotalBytes
                val totalText = totalBytes?.let { formatBytes(it) }
                val speedBps = state.downloadSpeedBytesPerSec
                val speedText = formatBytesPerSec(speedBps)

                totalText?.let {
                    Text("$downloadedText / $it", modifier = Modifier.padding(top = 8.dp))
                    Text("מהירות: $speedText", modifier = Modifier.padding(top = 4.dp))
                    val etaSeconds = if (speedBps > 0L) {
                        val remaining = (totalBytes!! - state.downloadedBytes).coerceAtLeast(0)
                        ((remaining + speedBps - 1) / speedBps)
                    } else null
                    etaSeconds?.let { secs ->
                        Text(
                            "זמן משוער: ${formatEta(secs)}",
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
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
                DefaultButton({
                    onEvent(OnBoardingEvents.onFinish)
                    onFinish()
                }) {
                    Text(stringResource(Res.string.onboarding_open_app))
                }
            }
            else -> {
                // Idle or initial state; show a friendly initializing message instead of an empty screen
                OnboardingText(stringResource(Res.string.onboarding_preparing_download))
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
        OnBoardingView(state = OnBoardingState.previewLoaded, onEvent = {}, onFinish = {})
    }
}

@Preview(name = "Downloading", showBackground = true)
@Composable
private fun Preview_OnBoarding_Downloading() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewDownloading, onEvent = {}, onFinish = {})
    }
}

@Preview(name = "Extracting", showBackground = true)
@Composable
private fun Preview_OnBoarding_Extracting() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewExtracting, onEvent = {}, onFinish = {})
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun Preview_OnBoarding_Error() {
    PreviewContainer {
        OnBoardingView(state = OnBoardingState.previewError, onEvent = {}, onFinish = {})
    }
}
