package io.github.kdroidfilter.seforimapp.features.onboarding.screens.finish

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.OnBoardingEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.OnBoardingViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_open_app
import seforimapp.seforimapp.generated.resources.onboarding_ready

@Composable
fun FinishScreen(progressBarState: ProgressBarState = ProgressBarState) {
    val viewModel: OnBoardingViewModel = LocalAppGraph.current.onBoardingViewModel
    LaunchedEffect(Unit) { progressBarState.setProgress(1f) }
    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_ready),
        bottomAction = {
            DefaultButton(onClick = { viewModel.onEvent(OnBoardingEvents.onFinish) }) {
                Text(stringResource(Res.string.onboarding_open_app))
            }
        }
    ) {}
}
