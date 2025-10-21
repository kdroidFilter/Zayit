package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.DeviceSsd
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.next_button

@Composable
fun AvailableDiskSpaceScreen(navController: NavHostController){

}

@Composable
fun AvailableDiskSpaceView(
    state: AvailableDiskSpaceState,
    onEvent: (AvailableDiskSpaceEvents) -> Unit,
    onNext: () -> Unit = {}
){
    OnBoardingScaffold("Verification de l'espace disque nécessaire", {
        DefaultButton({}, enabled = false){
            Text(stringResource(Res.string.next_button))
        }
    }){

        Icon(DeviceSsd, null, modifier = Modifier.size(72.dp))

    }

}

@Composable
@Preview
private fun AvailableDiskSpaceScreenEnoughSpacePreview(){
    PreviewContainer { AvailableDiskSpaceView(AvailableDiskSpaceState.hasEnoughSpace, {}) }
}

@Composable
@Preview
private fun AvailableDiskSpaceScreenNoEnoughSpacePreview(){
    PreviewContainer { AvailableDiskSpaceView(AvailableDiskSpaceState.noEnoughSpace, {}) }
}
