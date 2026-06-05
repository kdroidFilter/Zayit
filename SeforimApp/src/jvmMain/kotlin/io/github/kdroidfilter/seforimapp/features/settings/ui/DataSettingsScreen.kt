package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.data.DataSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.data.DataSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.data_export_button
import seforimapp.seforimapp.generated.resources.data_import_button
import seforimapp.seforimapp.generated.resources.settings_reset_app
import seforimapp.seforimapp.generated.resources.settings_reset_confirm
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_no
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_yes
import seforimapp.seforimapp.generated.resources.settings_reset_done
import seforimapp.seforimapp.generated.resources.settings_reset_warning
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DataSettingsScreen() {
    val viewModel: DataSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()

    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Export/Import section
            DataTransferSection(state, viewModel)

            // Reset app section
            ResetSection(
                resetDone = state.resetDone,
                onReset = { /* Will be integrated with viewModel later */ },
            )
        }
    }
}

@Composable
private fun DataTransferSection(
    state: io.github.kdroidfilter.seforimapp.features.settings.data.DataSettingsState,
    viewModel: DataSettingsViewModel,
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.data_export_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultButton(
                onClick = { viewModel.onEvent(DataSettingsEvents.StartExport) },
                enabled = !state.isExporting && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.data_export_button))
            }
            if (state.isExporting) {
                Text("Exporting...", color = JewelTheme.globalColors.text.info)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.data_import_button),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultButton(
                onClick = {
                    showFileChooser { file ->
                        viewModel.importFromFile(file)
                    }
                },
                enabled = !state.isExporting && !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.data_import_button))
            }
            if (state.isImporting) {
                Text("Importing...", color = JewelTheme.globalColors.text.info)
            }
        }

        // Messages
        state.exportError?.let {
            InlineErrorBanner(text = "Export failed: $it", modifier = Modifier.fillMaxWidth())
        }
        state.importError?.let {
            InlineErrorBanner(text = "Import failed: $it", modifier = Modifier.fillMaxWidth())
        }
        state.successMessage?.let {
            InlineSuccessBanner(text = it, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResetSection(
    resetDone: Boolean,
    onReset: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var showConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(Res.string.settings_reset_app))

        if (showConfirmation) {
            Text(
                text = stringResource(Res.string.settings_reset_warning),
                color = JewelTheme.globalColors.text.warning,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DefaultButton(
                    onClick = {
                        showConfirmation = false
                        onReset()
                    },
                ) {
                    Text(text = stringResource(Res.string.settings_reset_confirm_yes))
                }
                OutlinedButton(onClick = { showConfirmation = false }) {
                    Text(text = stringResource(Res.string.settings_reset_confirm_no))
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DefaultButton(onClick = { showConfirmation = true }) {
                    Text(text = stringResource(Res.string.settings_reset_app))
                }
                Text(
                    text = stringResource(Res.string.settings_reset_confirm),
                    color = JewelTheme.globalColors.text.info,
                )
            }
        }

        if (resetDone) {
            InlineSuccessBanner(
                text = stringResource(Res.string.settings_reset_done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun showFileChooser(onFileSelected: (File) -> Unit) {
    val fileChooser = JFileChooser()
    fileChooser.fileFilter = FileNameExtensionFilter("Database files", "db")
    val result = fileChooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        onFileSelected(fileChooser.selectedFile)
    }
}
