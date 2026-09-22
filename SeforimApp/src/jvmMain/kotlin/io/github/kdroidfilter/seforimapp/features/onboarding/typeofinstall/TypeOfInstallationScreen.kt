package io.github.kdroidfilter.seforimapp.features.onboarding.typeofinstall

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.onboarding.data.DatabaseInstallLocation
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import io.github.kdroidfilter.seforimapp.icons.Unarchive
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.*
import java.io.File

@Composable
fun TypeOfInstallationScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    val processRepository = LocalAppGraph.current.onboardingProcessRepository
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(DatabaseInstallLocation.defaultDirectory()) }
    LaunchedEffect(Unit) {
        progressBarState.setProgress(0.3f)
    }

    fun goOnline() {
        processRepository.setInstallDirectory(destination)
        // Old-database cleanup + disk-space gate run inside the download step
        // (DownloadViewModel / DatabasePreparationUseCase).
        // Move forward and clear all previous onboarding steps so back is disabled.
        navController.navigate(OnBoardingDestination.DatabaseOnlineInstallerScreen) {
            popUpTo(0) { inclusive = true }
        }
    }

    fun goOffline() {
        processRepository.setInstallDirectory(destination)
        // Cleanup + disk-space gate run in OfflineFileSelectionScreen before extraction.
        navController.navigate(OnBoardingDestination.OfflineFileSelectionScreen) {
            popUpTo(0) { inclusive = true }
        }
    }

    TypeOfInstallationView(
        onOnlineInstallation = { goOnline() },
        onOfflineInstallation = { goOffline() },
        destination = destination.absolutePath,
        onChooseDirectory = {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { FileKit.openDirectoryPicker() }
                if (picked != null) destination = DatabaseInstallLocation.customDirectory(File(picked.path))
            }
        },
        onUseDefaultDirectory = { destination = DatabaseInstallLocation.defaultDirectory() },
    )
}

@Composable
private fun TypeOfInstallationView(
    onOnlineInstallation: () -> Unit = {},
    onOfflineInstallation: () -> Unit = {},
    destination: String = "",
    onChooseDirectory: () -> Unit = {},
    onUseDefaultDirectory: () -> Unit = {},
) {
    OnBoardingScaffold(title = stringResource(Res.string.installation_title)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(stringResource(Res.string.installation_destination, destination))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(onChooseDirectory) { Text(stringResource(Res.string.installation_choose_directory)) }
                DefaultButton(onUseDefaultDirectory) { Text(stringResource(Res.string.installation_default_directory)) }
            }
            Row(modifier = Modifier.weight(1f)) {
                InstallationTypeColumn(
                    // Offline
                    title = stringResource(Res.string.installation_offline_title),
                    icon = Unarchive,
                    description = stringResource(Res.string.installation_offline_desc),
                    buttonAction = { onOfflineInstallation() },
                    buttonText = stringResource(Res.string.installation_offline_button),
                )
                Divider(orientation = Orientation.Vertical, modifier = Modifier.fillMaxHeight().width(1.dp))
                InstallationTypeColumn(
                    // Online
                    title = stringResource(Res.string.installation_online_title),
                    icon = Download_for_offline,
                    description = stringResource(Res.string.installation_online_desc),
                    buttonAction = { onOnlineInstallation() },
                    buttonText = stringResource(Res.string.installation_online_button),
                )
            }
        }
    }
}

@Composable
private fun RowScope.InstallationTypeColumn(
    title: String,
    icon: ImageVector,
    description: String,
    buttonText: String,
    buttonAction: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = JewelTheme.typography.h1TextStyle.fontSize)
        Icon(icon, title, modifier = Modifier.size(72.dp), tint = JewelTheme.globalColors.text.normal)
        Text(
            description,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        DefaultButton(buttonAction) {
            Text(buttonText)
        }
    }
}

@Composable
@Preview
private fun TypeOfInstallationScreenPreview() {
    PreviewContainer {
        TypeOfInstallationView()
    }
}
