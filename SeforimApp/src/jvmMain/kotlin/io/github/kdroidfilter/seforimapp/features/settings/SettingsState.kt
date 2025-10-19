package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

// Represent persisted/preferences settings and UI flags like visibility

data class SettingsState(
    val closedAutomaticallyBookTreePaneOnNewBookSelected: Boolean = false,
    val isVisible: Boolean = false,
)

@Composable
fun collectSettingsState(viewModel: SettingsViewModel): SettingsState {
    return viewModel.state.collectAsState().value
}