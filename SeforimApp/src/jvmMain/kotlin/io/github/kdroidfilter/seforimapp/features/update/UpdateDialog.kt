package io.github.kdroidfilter.seforimapp.features.update

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateService
import io.github.kdroidfilter.seforimapp.framework.update.UpdateUiState
import io.github.kdroidfilter.seforimapp.framework.update.availableVersion
import io.github.kdroidfilter.seforimapp.icons.Download_for_offline
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.modifier.trackActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.update_dialog_db_warning
import seforimapp.seforimapp.generated.resources.update_dialog_message
import seforimapp.seforimapp.generated.resources.update_dialog_title
import seforimapp.seforimapp.generated.resources.update_downloading
import seforimapp.seforimapp.generated.resources.update_install_restart
import seforimapp.seforimapp.generated.resources.update_later

@Composable
fun NucleusApplicationScope.UpdateDialog(
    service: AppUpdateService,
    onClose: () -> Unit,
) {
    IntUiTheme(
        theme = ThemeUtils.buildThemeDefinition(),
        styling = ThemeUtils.buildComponentStyling(),
    ) {
        val dialogState =
            rememberDialogState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(440.dp, 340.dp))
        JewelDecoratedDialog(
            onCloseRequest = onClose,
            title = stringResource(Res.string.update_dialog_title),
            state = dialogState,
            visible = true,
            resizable = false,
        ) {
            JewelDialogTitleBar(
                modifier = Modifier.newFullscreenControls(),
                gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Unspecified,
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        AllIconsKeys.Ide.Notification.IdeUpdate,
                        contentDescription = null,
                        tint = JewelTheme.globalColors.text.normal,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(stringResource(Res.string.update_dialog_title))
                }
            }

            val state by service.state.collectAsState()
            val version = state.availableVersion.orEmpty()
            val needsDbWarning =
                (state as? UpdateUiState.Available)?.needsDbWarning
                    ?: (state as? UpdateUiState.Downloading)?.needsDbWarning
                    ?: (state as? UpdateUiState.ReadyToInstall)?.needsDbWarning
                    ?: false

            Column(
                modifier =
                    Modifier
                        .trackActivation()
                        .fillMaxSize()
                        .background(JewelTheme.globalColors.panelBackground)
                        .padding(16.dp),
            ) {
                // Info bar pinned at the top.
                if (needsDbWarning) {
                    InlineWarningBanner(text = stringResource(Res.string.update_dialog_db_warning))
                    Spacer(Modifier.height(8.dp))
                }

                // Content area (hero icon + message), centered.
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        Download_for_offline,
                        contentDescription = null,
                        modifier = Modifier.size(88.dp),
                        tint = JewelTheme.globalColors.text.normal,
                    )
                    Text(
                        text = stringResource(Res.string.update_dialog_message, version),
                        textAlign = TextAlign.Center,
                        fontSize = JewelTheme.typography.h3TextStyle.fontSize,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Fixed-height progress slot just above the action bar. Reserving the height
                // unconditionally means showing/hiding the bar never shifts the centered hero above.
                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    (state as? UpdateUiState.Downloading)?.let { downloading ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${stringResource(Res.string.update_downloading)} ${downloading.percent}%",
                                color = JewelTheme.globalColors.text.info,
                            )
                            Spacer(Modifier.height(4.dp))
                            AnimatedHorizontalProgressBar(
                                value = downloading.percent / 100f,
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Divider(orientation = Orientation.Horizontal)
                Spacer(Modifier.height(8.dp))

                // Bottom action bar, aligned to the end (bottom-right).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onClose) {
                        Text(stringResource(Res.string.update_later))
                    }
                    DefaultButton(
                        enabled = state !is UpdateUiState.Downloading,
                        onClick = {
                            when (state) {
                                is UpdateUiState.ReadyToInstall -> service.installAndRestart()
                                is UpdateUiState.Available -> service.startDownload()
                                else -> {}
                            }
                        },
                    ) {
                        Text(stringResource(Res.string.update_install_restart))
                    }
                }
            }
        }
    }
}
