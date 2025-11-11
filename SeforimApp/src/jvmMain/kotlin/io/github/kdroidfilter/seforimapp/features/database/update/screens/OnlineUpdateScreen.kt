package io.github.kdroidfilter.seforimapp.features.database.update.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.download.DownloadEvents
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import seforimapp.seforimapp.generated.resources.*

@Composable
fun OnlineUpdateScreen(
    navController: NavController,
    onUpdateCompleted: () -> Unit
) {
    val downloadViewModel: DownloadViewModel = LocalAppGraph.current.downloadViewModel
    val downloadState by downloadViewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        downloadViewModel.onEvent(DownloadEvents.Start)
    }
    
    LaunchedEffect(downloadState) {
        if (downloadState.completed) {
            onUpdateCompleted()
        }
    }
    
    OnBoardingScaffold(title = stringResource(Res.string.db_update_downloading_title)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            when {
                !downloadState.inProgress && !downloadState.completed && downloadState.errorMessage == null -> {
                    Text(
                        text = stringResource(Res.string.db_update_preparing_download),
                        textAlign = TextAlign.Center
                    )
                    CircularProgressIndicator()
                }
                
                downloadState.inProgress -> {
                    Text(
                        text = stringResource(Res.string.db_update_downloading),
                        textAlign = TextAlign.Center
                    )
                    
                    CircularProgressIndicator()
                    
                    Text(
                        text = "${(downloadState.progress * 100).toInt()}%",
                        textAlign = TextAlign.Center
                    )
                    
                    if (downloadState.downloadedBytes > 0 && downloadState.totalBytes != null && downloadState.totalBytes!! > 0) {
                        Text(
                            text = "${downloadState.downloadedBytes / 1024 / 1024} MB / ${downloadState.totalBytes!! / 1024 / 1024} MB",
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                downloadState.completed -> {
                    Text(
                        text = stringResource(Res.string.db_update_download_completed),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = stringResource(Res.string.db_update_download_success_message),
                        textAlign = TextAlign.Center
                    )
                }
                
                downloadState.errorMessage != null -> {
                    Text(
                        text = stringResource(Res.string.db_update_download_error),
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = downloadState.errorMessage ?: stringResource(Res.string.db_update_download_error_unknown),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                navController.popBackStack()
                            }
                        ) {
                            Text(stringResource(Res.string.db_update_back))
                        }
                        
                        DefaultButton(
                            onClick = {
                                downloadViewModel.onEvent(DownloadEvents.Start)
                            }
                        ) {
                            Text(stringResource(Res.string.db_update_retry))
                        }
                    }
                }
            }
        }
    }
}