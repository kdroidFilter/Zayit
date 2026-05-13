package io.github.kdroidfilter.seforimapp.features.settings.dbupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * Drop-in Compose section for the "Database delta update" capability.
 *
 * Wires up a [DbDeltaUpdateViewModel] from the per-window store, renders:
 *   - the current status text (idle, downloading, applying, done, error)
 *   - a busy LinearProgressIndicator while a phase is in flight
 *   - a "Check for updates" primary button (disabled while busy)
 *   - a "Dismiss" outlined button when there's a transient message
 *
 * Designed to be embedded into the existing GeneralSettingsScreen (or its
 * own pane) by the navigation graph. No external dependencies beyond
 * Jewel + Material3 LinearProgressIndicator.
 */
@Composable
fun DbDeltaUpdateSection(
    modifier: Modifier = Modifier,
) {
    val viewModel = metroViewModel<DbDeltaUpdateViewModel>(
        viewModelStoreOwner = LocalWindowViewModelStoreOwner.current,
    )
    val state by viewModel.state.collectAsState()
    val isBusy = state.phase != null
    val hasMessage = state.message.isNotEmpty() || state.errorMessage != null

    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text("Database delta updates")
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                state.errorMessage != null -> state.errorMessage!!
                state.needsFullBundle -> state.message
                state.lastAppliedCount != null -> state.message
                state.phase != null -> state.message.ifEmpty { state.phase.toString() }
                state.message.isNotEmpty() -> state.message
                else -> "Idle. Press the button below to check the release server for a new database delta."
            },
        )
        if (isBusy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DefaultButton(
                enabled = !isBusy,
                onClick = { viewModel.onEvent(DbDeltaUpdateEvents.CheckAndApplyClicked) },
            ) {
                Text(if (isBusy) "Working…" else "Check for updates")
            }
            if (hasMessage && !isBusy) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(DbDeltaUpdateEvents.ClearMessage) },
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
