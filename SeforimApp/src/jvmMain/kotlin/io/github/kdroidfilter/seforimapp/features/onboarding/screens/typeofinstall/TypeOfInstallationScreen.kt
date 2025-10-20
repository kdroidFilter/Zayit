package io.github.kdroidfilter.seforimapp.features.onboarding.screens.typeofinstall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.next_button
import seforimapp.seforimapp.generated.resources.previous_button

@Composable
fun TypeOfInstallationScreen(navController: NavController) {
    TypeOfInstallationView(
        onNext = { navController.navigate(OnBoardingDestination.DatabaseOnlineInstallerScreen) },
        onPrevious = { navController.navigateUp() })

}

@Composable
private fun TypeOfInstallationView(onNext: () -> Unit = {}, onPrevious: () -> Unit = {}) {
    OnBoardingScaffold(title = "Type of installation", bottomAction = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = { onPrevious() }) {
                Text(stringResource(Res.string.previous_button))
            }
            DefaultButton(onClick = { onNext() }) {
                Text(text = stringResource(Res.string.next_button))
            }
        }
    }) {}

}

@Composable
@Preview
fun TypeOfInstallationScreenPreview() {
    PreviewContainer {
        TypeOfInstallationView()
    }
}