package io.github.kdroidfilter.seforimapp.features.onboarding.screens.extract

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
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.OnBoardingViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_error_occurred
import seforimapp.seforimapp.generated.resources.onboarding_extracting_message

@Composable
fun ExtractScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: OnBoardingViewModel = LocalAppGraph.current.onBoardingViewModel
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { progressBarState.setProgress(0.8f) }

    // Navigate to finish when DB is ready
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(state.isDatabaseLoaded) {
        if (!navigated && state.isDatabaseLoaded) {
            navigated = true
            progressBarState.setProgress(1f)
            navController.navigate(OnBoardingDestination.FinishScreen)
        }
    }

    OnBoardingScaffold(title = stringResource(Res.string.onboarding_extracting_message)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.errorMessage != null) {
                val generic = stringResource(Res.string.onboarding_error_occurred)
                val detail = state.errorMessage?.takeIf { it.isNotBlank() }
                val message = detail?.let { "$generic: $it" } ?: generic
                Text(message)
            }

            HorizontalProgressBar(
                progress = state.extractProgress,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}
