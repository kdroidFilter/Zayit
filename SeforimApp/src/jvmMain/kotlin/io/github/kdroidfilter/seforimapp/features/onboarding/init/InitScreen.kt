package io.github.kdroidfilter.seforimapp.features.onboarding.init

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.*

@Composable
fun InitScreen(navController: NavController, progressBarState: ProgressBarState = ProgressBarState) {
    LaunchedEffect(Unit) {
        progressBarState.resetProgress()
    }
    InitView(onNext = {
        navController.navigate(OnBoardingDestination.LicenceScreen)
    })
}

@Composable
fun InitView(onNext: () -> Unit) {
    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_init_welcome_title),
        bottomAction = {
            DefaultButton({ onNext() }) { Text(stringResource(Res.string.onboarding_init_start)) }
        }
    ) {
        Text(
            text = stringResource(Res.string.onboarding_init_welcome_subtitle),
            fontSize = JewelTheme.typography.h4TextStyle.fontSize,
            textAlign = TextAlign.Center
        )
        Image(
            painter = painterResource(Res.drawable.zayit_transparent),
            contentDescription = null,
            modifier = Modifier.size(176.dp)
        )
        Text(stringResource(Res.string.onboarding_setup_guide))
    }
}

@Preview
@Composable
fun InitScreenPreview() {
    PreviewContainer { InitView({}) }
}