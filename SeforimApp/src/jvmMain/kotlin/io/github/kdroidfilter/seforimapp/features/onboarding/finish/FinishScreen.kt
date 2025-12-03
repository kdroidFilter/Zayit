package io.github.kdroidfilter.seforimapp.features.onboarding.finish

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.Check2Circle
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.vinceglb.confettikit.compose.ConfettiKit
import io.github.vinceglb.confettikit.core.Party
import io.github.vinceglb.confettikit.core.emitter.Emitter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_finish_ready_message
import seforimapp.seforimapp.generated.resources.onboarding_open_app
import seforimapp.seforimapp.generated.resources.onboarding_ready
import kotlin.time.Duration.Companion.seconds

@Composable
fun FinishScreen(progressBarState: ProgressBarState = ProgressBarState) {
    LaunchedEffect(Unit) { progressBarState.setProgress(1f) }
    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_ready),
        bottomAction = {
            DefaultButton(onClick = {
                // Persist that onboarding was completed and open the app
                io.github.kdroidfilter.seforimapp.core.settings.AppSettings.setOnboardingFinished(true)
                MainAppState.setShowOnBoarding(false)
            }) {
                Text(stringResource(Res.string.onboarding_open_app))
            }
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
        ) {
            ConfettiKit(
                modifier = Modifier.fillMaxSize(),
                parties = listOf(
                    Party(emitter = Emitter(duration = 5.seconds).perSecond(30))
                )
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    stringResource(Res.string.onboarding_finish_ready_message),
                    fontSize = JewelTheme.typography.h1TextStyle.fontSize
                )
                Icon(Check2Circle, null, modifier = Modifier.size(192.dp), tint = JewelTheme.globalColors.text.normal)
            }
        }
    }
}

@Composable
@Preview
fun FinishScreenPreview() {
    PreviewContainer { FinishScreen() }
}
