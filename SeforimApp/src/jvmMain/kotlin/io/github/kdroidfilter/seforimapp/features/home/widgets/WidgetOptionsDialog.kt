package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.LocalNucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widgets_done
import seforimapp.seforimapp.generated.resources.home_widgets_options
import seforimapp.seforimapp.generated.resources.home_widgets_reset

/**
 * [widget]'s options in a window of their own, as the app's settings: its [HomeWidget.Options], then a bar to go back
 * to its defaults or close. Each change is saved at once, and the card follows it behind.
 */
@Composable
internal fun WidgetOptionsDialog(
    widget: HomeWidget,
    state: HomeWidgetsState,
    onClose: () -> Unit,
) {
    val title = "${stringResource(widget.title)} · ${stringResource(Res.string.home_widgets_options).trimEnd('…')}"
    with(LocalNucleusApplicationScope.current) {
        IntUiTheme(theme = ThemeUtils.buildThemeDefinition(), styling = ThemeUtils.buildComponentStyling()) {
            JewelDecoratedDialog(
                onCloseRequest = onClose,
                title = title,
                state = rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(460.dp, 560.dp)),
                visible = true,
                resizable = true,
            ) {
                JewelDialogTitleBar(
                    modifier = Modifier.newFullscreenControls(),
                    gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Unspecified,
                    controlButtonsDirection = ControlButtonsDirection.SystemNative,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            AllIconsKeys.General.Settings,
                            contentDescription = null,
                            tint = JewelTheme.globalColors.text.normal,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(title)
                    }
                }
                Column(
                    Modifier
                        .trackActivation()
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { widget.Options(state) }
                    Spacer(Modifier.height(8.dp))
                    Divider(orientation = Orientation.Horizontal)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { state.setOptions(widget, null) }) {
                            Text(stringResource(Res.string.home_widgets_reset))
                        }
                        DefaultButton(onClick = onClose) { Text(stringResource(Res.string.home_widgets_done)) }
                    }
                }
            }
        }
    }
}
