package io.github.kdroidfilter.seforimapp.features.database.update

import androidx.compose.runtime.Composable
import dev.nucleusframework.application.NucleusApplicationScope
import io.github.kdroidfilter.seforimapp.core.presentation.components.InstallerWindow
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateNavHost
import io.github.kdroidfilter.seforimapp.features.database.update.navigation.DatabaseUpdateProgressBarState
import io.github.kdroidfilter.seforimapp.icons.Deployed_code_update
import org.jetbrains.compose.resources.stringResource
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.db_update_title_bar

@Composable
fun NucleusApplicationScope.DatabaseUpdateWindow(
    onUpdateComplete: () -> Unit = {},
    isDatabaseMissing: Boolean = false,
) {
    InstallerWindow(
        titleBarIcon = Deployed_code_update,
        titleBarText = stringResource(Res.string.db_update_title_bar),
        progress = DatabaseUpdateProgressBarState.progress,
    ) { navController ->
        DatabaseUpdateNavHost(
            navController = navController,
            onUpdateComplete = onUpdateComplete,
            isDatabaseMissing = isDatabaseMissing,
        )
    }
}
