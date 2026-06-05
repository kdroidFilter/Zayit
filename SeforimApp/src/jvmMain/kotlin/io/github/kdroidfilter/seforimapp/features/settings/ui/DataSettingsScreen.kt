package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.data.DataSettingsViewModel
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.InlineErrorBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.data_export_action
import seforimapp.seforimapp.generated.resources.data_export_description
import seforimapp.seforimapp.generated.resources.data_export_error
import seforimapp.seforimapp.generated.resources.data_export_success
import seforimapp.seforimapp.generated.resources.data_export_title
import seforimapp.seforimapp.generated.resources.data_exporting
import seforimapp.seforimapp.generated.resources.data_import_action
import seforimapp.seforimapp.generated.resources.data_import_description
import seforimapp.seforimapp.generated.resources.data_import_error
import seforimapp.seforimapp.generated.resources.data_import_success
import seforimapp.seforimapp.generated.resources.data_import_title
import seforimapp.seforimapp.generated.resources.data_importing
import seforimapp.seforimapp.generated.resources.data_reset_description
import seforimapp.seforimapp.generated.resources.settings_reset_app
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_no
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_yes
import seforimapp.seforimapp.generated.resources.settings_reset_done
import seforimapp.seforimapp.generated.resources.settings_reset_warning
import java.io.File

@Composable
fun DataSettingsScreen() {
    val viewModel: DataSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DataActionCard(
                title = Res.string.data_export_title,
                description = Res.string.data_export_description,
                actionLabel = if (state.isExporting) Res.string.data_exporting else Res.string.data_export_action,
                enabled = !state.isExporting && !state.isImporting,
                onClick = {
                    scope.launch {
                        val directory =
                            withContext(Dispatchers.Main) { FileKit.openDirectoryPicker() }
                        directory?.let { viewModel.exportToFile(File(it.path)) }
                    }
                },
            )

            DataActionCard(
                title = Res.string.data_import_title,
                description = Res.string.data_import_description,
                actionLabel = if (state.isImporting) Res.string.data_importing else Res.string.data_import_action,
                enabled = !state.isExporting && !state.isImporting,
                onClick = {
                    scope.launch {
                        val file =
                            withContext(Dispatchers.Main) {
                                FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("db")))
                            }
                        file?.let { viewModel.importFromFile(File(it.path)) }
                    }
                },
            )

            state.exportedFileName?.let {
                InlineSuccessBanner(
                    text = stringResource(Res.string.data_export_success, it),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.importSucceeded) {
                InlineSuccessBanner(
                    text = stringResource(Res.string.data_import_success),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.exportFailed) {
                InlineErrorBanner(
                    text = stringResource(Res.string.data_export_error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.importFailed) {
                InlineErrorBanner(
                    text = stringResource(Res.string.data_import_error),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ResetCard(
                resetDone = state.resetDone,
                onReset = { /* Will be integrated with viewModel later */ },
            )
        }
    }
}

@Composable
private fun DataActionCard(
    title: StringResource,
    description: StringResource,
    actionLabel: StringResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(title), fontSize = 15.sp)
            Text(
                text = stringResource(description),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
            )
        }
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(text = stringResource(actionLabel))
        }
    }
}

@Composable
private fun ResetCard(
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = stringResource(Res.string.settings_reset_app), fontSize = 15.sp)
                Text(
                    text = stringResource(Res.string.data_reset_description),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            if (!showConfirmation) {
                OutlinedButton(onClick = { showConfirmation = true }) {
                    Text(text = stringResource(Res.string.settings_reset_app))
                }
            }
        }

        if (showConfirmation) {
            InlineErrorBanner(
                text = stringResource(Res.string.settings_reset_warning),
                modifier = Modifier.fillMaxWidth(),
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
        }

        if (resetDone) {
            InlineSuccessBanner(
                text = stringResource(Res.string.settings_reset_done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
