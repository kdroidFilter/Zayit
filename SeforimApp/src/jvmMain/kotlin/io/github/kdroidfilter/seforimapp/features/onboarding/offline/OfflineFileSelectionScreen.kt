package io.github.kdroidfilter.seforimapp.features.onboarding.offline

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.santimattius.structured.annotations.StructuredScope
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*
import java.io.File

@Composable
fun OfflineFileSelectionScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    LaunchedEffect(Unit) {
        progressBarState.setProgress(0.5f)
    }

    val extractViewModel: ExtractViewModel = metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val processRepository: OnboardingProcessRepository = LocalAppGraph.current.onboardingProcessRepository
    val cleanupUseCase = LocalAppGraph.current.databaseCleanupUseCase

    var part01Path by remember { mutableStateOf<String?>(null) }
    var hasStartedExtraction by remember { mutableStateOf(false) }
    var cleanupCompleted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Function to start extraction with part01 path
    fun startExtraction(
        @StructuredScope scope: CoroutineScope,
        p1: String,
    ) {
        scope.launch {
            // Nettoyer les anciens fichiers avant de commencer l'extraction
            if (!cleanupCompleted) {
                cleanupUseCase.cleanupDatabaseFiles()
                cleanupCompleted = true
            }

            // Start extraction with part01 path; ExtractUseCase discovers part02 automatically
            progressBarState.setProgress(0.7f)
            processRepository.setPendingZstPath(p1)
            extractViewModel.onEvent(ExtractEvents.StartIfPending)
            hasStartedExtraction = true

            // Move forward and clear all previous onboarding steps so back is disabled
            navController.navigate(OnBoardingDestination.ExtractScreen) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // File picker for part02 file (only used if part02 not found automatically)
    val pickPart02Launcher =
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("part02")),
        ) { file ->
            val p2 = file?.path
            val p1 = part01Path
            if (!p2.isNullOrBlank() && !p1.isNullOrBlank()) {
                startExtraction(scope, p1)
            }
        }

    // File picker for part01 file
    val pickPart01Launcher =
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("part01")),
        ) { file ->
            val p1 = file?.path
            part01Path = p1
            if (!p1.isNullOrBlank()) {
                // Check if part02 exists in the same directory
                val part01File = File(p1)
                val part02File = File(part01File.parent, part01File.name.replace(".part01", ".part02"))

                if (part02File.exists()) {
                    // Part02 found automatically, start extraction
                    startExtraction(scope, p1)
                } else {
                    // Part02 not found, ask user to select it
                    @Suppress("UNSTRUCTURED_COROUTINE_LAUNCH")
                    pickPart02Launcher.launch()
                }
            }
        }

    OnBoardingScaffold(title = stringResource(Res.string.onboarding_file_selection)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                io.github.kdroidfilter.seforimapp.icons.Unarchive,
                contentDescription = null,
                modifier = Modifier.size(192.dp),
                tint = JewelTheme.globalColors.text.normal,
            )

            Text(
                text = stringResource(Res.string.onboarding_file_selection),
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(Res.string.onboarding_select_files_message),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            if (part01Path != null) {
                Text(
                    text = stringResource(Res.string.onboarding_part01_selected),
                    textAlign = TextAlign.Center,
                    color = JewelTheme.globalColors.text.normal,
                )
            }

            DefaultButton(
                onClick = {
                    @Suppress("UNSTRUCTURED_COROUTINE_LAUNCH")
                    pickPart01Launcher.launch()
                },
            ) {
                Text(stringResource(Res.string.onboarding_choose_files))
            }
        }
    }
}
