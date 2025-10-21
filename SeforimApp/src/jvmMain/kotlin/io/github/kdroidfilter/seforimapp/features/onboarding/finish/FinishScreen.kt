package io.github.kdroidfilter.seforimapp.features.onboarding.finish

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_open_app
import seforimapp.seforimapp.generated.resources.onboarding_ready

@Composable
fun FinishScreen(progressBarState: ProgressBarState = ProgressBarState) {
    LaunchedEffect(Unit) { progressBarState.setProgress(1f) }
    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_ready),
        bottomAction = {
            DefaultButton(onClick = { MainAppState.setShowOnBoarding(false) }) {
                Text(stringResource(Res.string.onboarding_open_app))
            }
        }
    ) {}
}
