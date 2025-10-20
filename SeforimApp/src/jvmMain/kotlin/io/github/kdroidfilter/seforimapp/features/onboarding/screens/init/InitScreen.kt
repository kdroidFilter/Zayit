package io.github.kdroidfilter.seforimapp.features.onboarding.screens.init

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_init_start
import seforimapp.seforimapp.generated.resources.onboarding_init_welcome_subtitle
import seforimapp.seforimapp.generated.resources.onboarding_init_welcome_title
import seforimapp.seforimapp.generated.resources.onboarding_setup_guide
import seforimapp.seforimapp.generated.resources.zayit_transparent

@Composable
fun InitScreen() {

}

@Composable
fun InitView(onEvent: (InitEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.onboarding_init_welcome_title), fontSize = JewelTheme.typography.h0TextStyle.fontSize)
        Text(stringResource(Res.string.onboarding_init_welcome_subtitle), fontSize = JewelTheme.typography.h4TextStyle.fontSize)
        Image(
            painter = painterResource(Res.drawable.zayit_transparent),
            contentDescription = null,
            modifier = Modifier.size(192.dp)
        )
        Text(stringResource(Res.string.onboarding_setup_guide))
        DefaultButton({ onEvent(InitEvent.OnNext) } ){
            Text(stringResource(Res.string.onboarding_init_start))
        }
    }
}

@Preview
@Composable
fun InitScreenPreview() {
    PreviewContainer { InitView(onEvent = {}) }
}