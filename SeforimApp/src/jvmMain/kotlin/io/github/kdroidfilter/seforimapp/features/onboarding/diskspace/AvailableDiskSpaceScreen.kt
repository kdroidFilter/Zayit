package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.utils.formatBytes
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.DeviceSsd
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.next_button
import seforimapp.seforimapp.generated.resources.onboarding_disk_space_available
import seforimapp.seforimapp.generated.resources.onboarding_disk_space_insufficient
import seforimapp.seforimapp.generated.resources.onboarding_disk_space_required
import seforimapp.seforimapp.generated.resources.onboarding_disk_space_sufficient
import seforimapp.seforimapp.generated.resources.onboarding_disk_title

@Composable
fun AvailableDiskSpaceScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: AvailableDiskSpaceViewModel = LocalAppGraph.current.availableDiskSpaceViewModel
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { progressBarState.setProgress(0.2f) }

    AvailableDiskSpaceView(
        state = state,
        onEvent = viewModel::onEvent,
        onNext = { navController.navigate(OnBoardingDestination.TypeOfInstallationScreen) }
    )
}

@Composable
fun AvailableDiskSpaceView(
    state: AvailableDiskSpaceState,
    onEvent: (AvailableDiskSpaceEvents) -> Unit,
    onNext: () -> Unit = {}
) {
    val requiredBytes = 15L * 1024 * 1024 * 1024
    OnBoardingScaffold(title = stringResource(Res.string.onboarding_disk_title), bottomAction = {
        DefaultButton(onClick = onNext, enabled = state.hasEnoughSpace) {
            Text(stringResource(Res.string.next_button))
        }
    }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(DeviceSsd, null, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))

            val progress = (state.availableDiskSpace.toFloat() / requiredBytes.toFloat())
            AnimatedHorizontalProgressBar(value = progress)
            Spacer(modifier = Modifier.height(8.dp))

            if (state.hasEnoughSpace) {
                Text(stringResource(Res.string.onboarding_disk_space_sufficient))
            } else {
                Text(stringResource(Res.string.onboarding_disk_space_insufficient))
            }

            Text(stringResource(Res.string.onboarding_disk_space_available, formatBytes(state.availableDiskSpace)))
            Text(stringResource(Res.string.onboarding_disk_space_required, formatBytes(requiredBytes)))
        }
    }
}

@Composable
@Preview
private fun AvailableDiskSpaceScreenEnoughSpacePreview() {
    PreviewContainer {
        AvailableDiskSpaceView(
            AvailableDiskSpaceState(
                hasEnoughSpace = true,
                availableDiskSpace = 20L * 1024 * 1024 * 1024,
                remainingDiskSpaceAfter15Gb = 5L * 1024 * 1024 * 1024
            ),
            {}
        )
    }
}

@Composable
@Preview
private fun AvailableDiskSpaceScreenNoEnoughSpacePreview() {
    PreviewContainer {
        AvailableDiskSpaceView(
            AvailableDiskSpaceState(
                hasEnoughSpace = false,
                availableDiskSpace = 10L * 1024 * 1024 * 1024,
                remainingDiskSpaceAfter15Gb = -5L * 1024 * 1024 * 1024
            ),
            {}
        )
    }
}
