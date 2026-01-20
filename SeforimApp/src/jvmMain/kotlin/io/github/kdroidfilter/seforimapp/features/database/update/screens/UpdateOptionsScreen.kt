package io.github.kdroidfilter.seforimapp.features.database.update.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateDestination
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import io.github.kdroidfilter.seforimapp.icons.Unarchive
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.*

@Composable
fun UpdateOptionsScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        DatabaseUpdateProgressBarState.setVersionCheckComplete()
    }

    OnBoardingScaffold(title = stringResource(Res.string.db_update_options_title)) {
        Row(modifier = Modifier.fillMaxSize()) {
            UpdateOptionColumn(
                // Offline
                title = stringResource(Res.string.installation_offline_title),
                icon = Unarchive,
                description = stringResource(Res.string.db_update_local_file_desc),
                buttonAction = {
                    DatabaseUpdateProgressBarState.setOptionsSelected()
                    navController.navigate(DatabaseUpdateDestination.OfflineUpdateScreen)
                },
                buttonText = stringResource(Res.string.db_update_local_file_button),
            )
            Divider(orientation = Orientation.Vertical, modifier = Modifier.fillMaxHeight().width(1.dp))
            UpdateOptionColumn(
                // Online
                title = stringResource(Res.string.installation_online_title),
                icon = Download_for_offline,
                description = stringResource(Res.string.db_update_download_desc),
                buttonAction = {
                    DatabaseUpdateProgressBarState.setOptionsSelected()
                    navController.navigate(DatabaseUpdateDestination.OnlineUpdateScreen)
                },
                buttonText = stringResource(Res.string.db_update_download_button),
            )
        }
    }
}

@Composable
private fun RowScope.UpdateOptionColumn(
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
