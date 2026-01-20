package io.github.kdroidfilter.seforimapp.features.database.update.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.data.OnboardingProcessRepository
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.extract.ExtractViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun OfflineUpdateScreen(
    navController: NavController,
    onUpdateCompleted: () -> Unit,
) {
    val extractViewModel: ExtractViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val extractState by extractViewModel.state.collectAsState()
    val processRepository: OnboardingProcessRepository = LocalAppGraph.current.onboardingProcessRepository
    val cleanupUseCase = LocalAppGraph.current.databaseCleanupUseCase

    var part01Path by remember { mutableStateOf<String?>(null) }
    var hasStartedExtraction by remember { mutableStateOf(false) }
    var cleanupCompleted by remember { mutableStateOf(false) }
    var isCleaningUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(extractState) {
        if (extractState.inProgress) {
            DatabaseUpdateProgressBarState.setDownloadProgress(extractState.progress)
        }
        if (extractState.completed && hasStartedExtraction) {
            DatabaseUpdateProgressBarState.setUpdateComplete()
            navController.navigate(DatabaseUpdateDestination.CompletionScreen) {
                popUpTo<DatabaseUpdateDestination.OfflineUpdateScreen> { inclusive = true }
            }
        }
    }

    // File picker for part02 file
    val pickPart02Launcher =
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("part02")),
        ) { file ->
            val p2 = file?.path
            val p1 = part01Path
            if (!p2.isNullOrBlank() && !p1.isNullOrBlank()) {
                scope.launch {
                    // Nettoyer les anciens fichiers avant de commencer l'extraction
                    if (!cleanupCompleted) {
                        cleanupUseCase.cleanupDatabaseFiles()
                        cleanupCompleted = true
                    }
                    // Start extraction with part01 path; ExtractUseCase discovers part02 automatically
                    DatabaseUpdateProgressBarState.setDownloadStarted()
                    processRepository.setPendingZstPath(p1)
                    extractViewModel.onEvent(ExtractEvents.StartIfPending)
                    hasStartedExtraction = true
                }
            }
        }

    // File picker for part01 file
    val pickPart01Launcher =
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("part01")),
        ) { file ->
            part01Path = file?.path
            if (part01Path != null) {
                // Immediately ask for part02
                pickPart02Launcher.launch()
            }
        }

    OnBoardingScaffold(title = stringResource(Res.string.db_update_offline_title)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !hasStartedExtraction -> {
                    // File selection phase
                    Icon(
                        io.github.kdroidfilter.seforimapp.icons.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(192.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_file_selection),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_select_files_message),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    if (part01Path != null) {
                        Text(
                            text = stringResource(Res.string.db_update_part01_selected),
                            textAlign = TextAlign.Center,
                            color = JewelTheme.globalColors.text.normal,
                        )
                    }

                    DefaultButton(
                        onClick = { pickPart01Launcher.launch() },
                    ) {
                        Text(stringResource(Res.string.db_update_choose_files))
                    }
                }

                extractState.inProgress -> {
                    Icon(
                        io.github.kdroidfilter.seforimapp.icons.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(192.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_extracting),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = "${(extractState.progress * 100).toInt()}%",
                        textAlign = TextAlign.Center,
                    )
                }

                extractState.completed -> {
                    Text(
                        text = stringResource(Res.string.db_update_extraction_completed),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = stringResource(Res.string.db_update_download_success_message),
                        textAlign = TextAlign.Center,
                    )
                }

                extractState.errorMessage != null -> {
                    Text(
                        text = stringResource(Res.string.db_update_extraction_error),
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = extractState.errorMessage ?: stringResource(Res.string.db_update_download_error_unknown),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                navController.popBackStack()
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_back))
                        }

                        DefaultButton(
                            onClick = {
                                hasStartedExtraction = false
                                part01Path = null
                            },
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }

                else -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
