package io.github.kdroidfilter.seforimapp.features.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.platformtools.OperatingSystem
import io.github.kdroidfilter.platformtools.getOperatingSystem
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils.buildThemeDefinition
import io.github.kdroidfilter.seforimapp.core.presentation.utils.getCenteredWindowState
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book
import seforimapp.seforimapp.generated.resources.settings
import seforimapp.seforimapp.generated.resources.settings_reset_app
import seforimapp.seforimapp.generated.resources.settings_reset_done
import seforimapp.seforimapp.generated.resources.settings_db_path_label
import seforimapp.seforimapp.generated.resources.settings_db_path_not_set
import seforimapp.seforimapp.generated.resources.settings_persist_session
import seforimapp.seforimapp.generated.resources.settings_font_book_label
import seforimapp.seforimapp.generated.resources.settings_font_commentary_label
import seforimapp.seforimapp.generated.resources.settings_font_targum_label
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog

@Composable
fun Settings(onClose: () -> Unit) {
    val viewModel: SettingsViewModel = LocalAppGraph.current.settingsViewModel
    val state by viewModel.state.collectAsState()
    SettingsView(
        state,
        onClose,
        onToggleCloseTree = { value ->
            viewModel.onEvent(SettingsEvents.SetCloseBookTreeOnNewBookSelected(value))
        },
        onTogglePersistSession = { value ->
            viewModel.onEvent(SettingsEvents.SetPersistSession(value))
        },
        onSelectBookFont = { code -> viewModel.onEvent(SettingsEvents.SetBookFont(code)) },
        onSelectCommentaryFont = { code -> viewModel.onEvent(SettingsEvents.SetCommentaryFont(code)) },
        onSelectTargumFont = { code -> viewModel.onEvent(SettingsEvents.SetTargumFont(code)) },
        onReset = { viewModel.onEvent(SettingsEvents.ResetApp) })
}

@Composable
private fun SettingsView(
    state: SettingsState,
    onClose: () -> Unit,
    onToggleCloseTree: (Boolean) -> Unit,
    onTogglePersistSession: (Boolean) -> Unit,
    onSelectBookFont: (String) -> Unit,
    onSelectCommentaryFont: (String) -> Unit,
    onSelectTargumFont: (String) -> Unit,
    onReset: () -> Unit
) {
    val themeDefinition = buildThemeDefinition()

    IntUiTheme(
        theme = themeDefinition, styling = ComponentStyling.default().decoratedWindow(
                titleBarStyle = ThemeUtils.pickTitleBarStyle(),
            )
    ) {
        val settingsWindowState = remember { getCenteredWindowState(800, 600) }
        DecoratedWindow(
            onCloseRequest = onClose,
            title = stringResource(Res.string.settings),
            icon = painterResource(Res.drawable.AppIcon),
            state = settingsWindowState,
            visible = true,
            resizable = false,
        ) {
            val background = JewelTheme.globalColors.panelBackground
            LaunchedEffect(window, background) {
                window.background = java.awt.Color(background.toArgb())
            }

            val isMac = getOperatingSystem() == OperatingSystem.MACOS
            val isWindows = getOperatingSystem() == OperatingSystem.WINDOWS
            TitleBar(modifier = Modifier.newFullscreenControls()) {
                Box(
                    modifier = Modifier.fillMaxWidth(if (isMac) 0.9f else 1f)
                        .padding(start = if (isWindows) 70.dp else 0.dp)
                ) {
                    val centerOffset = 40.dp
                    Row(
                        modifier = Modifier.align(Alignment.Center).offset(x = centerOffset),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            AllIconsKeys.General.Settings,
                            contentDescription = null,
                            tint = JewelTheme.globalColors.text.normal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(stringResource(Res.string.settings))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .trackActivation()
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(16.dp),
            ) {
                // Font selectors
                val options = remember { FontCatalog.options }
                val optionLabels = options.map { stringResource(it.label) }

                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(Res.string.settings_font_book_label))
                    val selectedIndex =
                        options.indexOfFirst { it.code == state.bookFontCode }.let { if (it >= 0) it else 0 }
                    ListComboBox(
                        items = optionLabels,
                        selectedIndex = selectedIndex,
                        onSelectedItemChange = { idx -> onSelectBookFont(options[idx].code) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(Res.string.settings_font_commentary_label))
                    val selectedIndex =
                        options.indexOfFirst { it.code == state.commentaryFontCode }.let { if (it >= 0) it else 0 }
                    ListComboBox(
                        items = optionLabels,
                        selectedIndex = selectedIndex,
                        onSelectedItemChange = { idx -> onSelectCommentaryFont(options[idx].code) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(Res.string.settings_font_targum_label))
                    val selectedIndex =
                        options.indexOfFirst { it.code == state.targumFontCode }.let { if (it >= 0) it else 0 }
                    ListComboBox(
                        items = optionLabels,
                        selectedIndex = selectedIndex,
                        onSelectedItemChange = { idx -> onSelectTargumFont(options[idx].code) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Divider(modifier = Modifier, orientation = Orientation.Horizontal)

                // Database path display
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = stringResource(Res.string.settings_db_path_label))
                    Text(
                        text = state.databasePath ?: stringResource(Res.string.settings_db_path_not_set)
                    )
                }

                Divider(modifier = Modifier, orientation = Orientation.Horizontal)

                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Checkbox(
                        checked = state.closedAutomaticallyBookTreePaneOnNewBookSelected,
                        onCheckedChange = { onToggleCloseTree(it) })
                    Text(
                        text = stringResource(Res.string.close_book_tree_on_new_book),
                    )
                }

                Divider(modifier = Modifier, orientation = Orientation.Horizontal)

                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Checkbox(
                        checked = state.persistSession, onCheckedChange = { onTogglePersistSession(it) })
                    Text(
                        text = stringResource(Res.string.settings_persist_session),
                    )
                }

                Divider(modifier = Modifier, orientation = Orientation.Horizontal)

                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefaultButton(onClick = onReset) {
                        Text(text = stringResource(Res.string.settings_reset_app))
                    }
                    if (state.resetDone) {
                        Text(text = stringResource(Res.string.settings_reset_done))
                    }
                }
            }
        }
    }
}
