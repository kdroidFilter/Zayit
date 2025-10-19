package io.github.kdroidfilter.seforimapp.features.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils.buildThemeDefinition
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book
import seforimapp.seforimapp.generated.resources.settings

@Composable
fun Settings(onClose: () -> Unit) {
    val viewModel: SettingsViewModel = LocalAppGraph.current.settingsViewModel
    val state by viewModel.state.collectAsState()
    SettingsView(state, onClose, onToggleCloseTree = { value ->
        viewModel.onEvent(SettingsEvents.SetCloseBookTreeOnNewBookSelected(value))
    })
}

@Composable
private fun SettingsView(state: SettingsState, onClose: () -> Unit, onToggleCloseTree: (Boolean) -> Unit) {
    val themeDefinition = buildThemeDefinition()

    IntUiTheme(
        theme = themeDefinition,
        styling = ComponentStyling.default()
            .decoratedWindow(
                titleBarStyle = ThemeUtils.pickTitleBarStyle(),
            )
    ) {
        DialogWindow(
            onCloseRequest = onClose,
            state = rememberDialogState(size = DpSize(800.dp, 600.dp)),
            title = stringResource(Res.string.settings),
        ) {
            window.setWindowsAdaptiveTitleBar()
            Column(
                modifier =
                    Modifier.trackActivation().fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Checkbox(
                        checked = state.closedAutomaticallyBookTreePaneOnNewBookSelected,
                        onCheckedChange = { onToggleCloseTree(it) }
                    )
                    Text(
                        text = stringResource(Res.string.close_book_tree_on_new_book),
                    )
                }
            }
        }
    }
}
