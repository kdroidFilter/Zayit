package io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.components.Download_for_offline
import io.github.kdroidfilter.seforimapp.core.presentation.components.HardDriveUpload
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.installation_offline_button
import seforimapp.seforimapp.generated.resources.installation_offline_desc
import seforimapp.seforimapp.generated.resources.installation_offline_title
import seforimapp.seforimapp.generated.resources.installation_online_button
import seforimapp.seforimapp.generated.resources.installation_online_desc
import seforimapp.seforimapp.generated.resources.installation_online_title
import seforimapp.seforimapp.generated.resources.installation_title

@Composable
fun TypeOfInstallationScreen(navController: NavController, progressBarState: ProgressBarState = ProgressBarState) {
    LaunchedEffect(Unit) {
        progressBarState.setProgress(0.3f)
    }
    val viewModel: TypeOfInstallationViewModel = LocalAppGraph.current.typeOfInstallationViewModel
    val pickZstLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("zst"))
    ) { file ->
        val path = file?.path
        if (path != null) {
            viewModel.onEvent(TypeOfInstallationEvents.OfflineFileChosen(path))
            progressBarState.setProgress(0.6f)
            navController.navigate(OnBoardingDestination.ExtractScreen)
        }
    }
    TypeOfInstallationView(
        onOnlineInstallation = { navController.navigate(OnBoardingDestination.DatabaseOnlineInstallerScreen) },
        onOfflineInstallation = { pickZstLauncher.launch() }
    )
}

@Composable
private fun TypeOfInstallationView(
    onOnlineInstallation: () -> Unit = {},
    onOfflineInstallation: () -> Unit = {}
) {
    OnBoardingScaffold(title = stringResource(Res.string.installation_title)) {
        Row(modifier = Modifier.fillMaxSize()) {
            InstallationTypeColumn(
                // Offline
                title = stringResource(Res.string.installation_offline_title),
                icon = HardDriveUpload,
                description = stringResource(Res.string.installation_offline_desc),
                buttonAction = { onOfflineInstallation() },
                buttonText = stringResource(Res.string.installation_offline_button)
            )
            Divider(orientation = Orientation.Vertical, modifier = Modifier.fillMaxHeight().width(1.dp))
            InstallationTypeColumn(
                // Online
                title = stringResource(Res.string.installation_online_title),
                icon = Download_for_offline,
                description = stringResource(Res.string.installation_online_desc),
                buttonAction = { onOnlineInstallation() },
                buttonText = stringResource(Res.string.installation_online_button)
            )
        }
    }
}


@Composable
private fun RowScope.InstallationTypeColumn(
    title: String,
    icon: ImageVector,
    description: String,
    buttonText: String,
    buttonAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = JewelTheme.typography.h1TextStyle.fontSize)
        Icon(icon, title, modifier = Modifier.size(72.dp), tint = JewelTheme.globalColors.text.normal)
        Text(
            description,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        DefaultButton(buttonAction) {
            Text(buttonText)
        }
    }
}

@Composable
@Preview
fun TypeOfInstallationScreenPreview() {
    PreviewContainer {
        TypeOfInstallationView()
    }
}
