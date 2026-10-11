package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.features.home.widgets.availableTools
import io.github.kdroidfilter.seforimapp.features.siddur.installedSiddur
import io.github.kdroidfilter.seforimapp.features.siddur.openSiddurTab
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.menu_tools
import seforimapp.seforimapp.generated.resources.siddur_title

/**
 * The app's Tools menu in the title bar, where there's no native menu bar (macOS has its own Tools menu): the siddur,
 * then the tool windows.
 */
@Composable
fun ToolsMenuButton() {
    val toolWindows = LocalAppGraph.current.toolWindows
    var visible by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.menu_tools)
    val tools = availableTools.map { it to stringResource(it.title) }
    val siddur = stringResource(Res.string.siddur_title)
    val tabsViewModel = LocalOpenWindow.current.tabsViewModel

    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        TitleBarActionButton(
            key = AllIconsKeys.General.ExternalTools,
            contentDescription = label,
            onClick = { visible = !visible },
            tooltipText = label,
        )
        if (visible) {
            PopupMenu(
                onDismissRequest = {
                    visible = false
                    true
                },
                popupPositionProvider = BelowAnchorEndPositionProvider,
            ) {
                if (installedSiddur != null) {
                    selectableItem(
                        selected = false,
                        onClick = {
                            visible = false
                            openSiddurTab(tabsViewModel)
                        },
                    ) { Text(siddur) }
                }
                tools.forEach { (tool, title) ->
                    selectableItem(
                        selected = false,
                        onClick = {
                            visible = false
                            toolWindows.open(tool)
                        },
                    ) { Text(title) }
                }
            }
        }
    }
}
