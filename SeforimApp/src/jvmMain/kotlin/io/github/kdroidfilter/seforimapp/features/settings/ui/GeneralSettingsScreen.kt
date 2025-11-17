package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*

@Composable
fun GeneralSettingsScreen() {
    val viewModel: GeneralSettingsViewModel = LocalAppGraph.current.generalSettingsViewModel
    val state by viewModel.state.collectAsState()
    GeneralSettingsView(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun GeneralSettingsView(
    state: GeneralSettingsState,
    onEvent: (GeneralSettingsEvents) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = state.closeTreeOnNewBook,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetCloseTreeOnNewBook(it)) }
            )
            Text(text = stringResource(Res.string.close_book_tree_on_new_book))
        }

        Divider(orientation = Orientation.Horizontal)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = state.persistSession,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetPersistSession(it)) }
            )
            Text(text = stringResource(Res.string.settings_persist_session))
        }

        Divider(orientation = Orientation.Horizontal)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = state.ramSaver,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetRamSaver(it)) }
            )
            Text(text = stringResource(Res.string.settings_ram_saver))
        }

        Divider(orientation = Orientation.Horizontal)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(onClick = { onEvent(GeneralSettingsEvents.ResetApp) }) {
                Text(text = stringResource(Res.string.settings_reset_app))
            }
            if (state.resetDone) {
                Text(text = stringResource(Res.string.settings_reset_done))
            }
        }
    }
}

@Composable
@Preview
private fun GeneralSettingsView_Preview() {
    PreviewContainer {
        GeneralSettingsView(state = GeneralSettingsState.preview, onEvent = {})
    }
}
