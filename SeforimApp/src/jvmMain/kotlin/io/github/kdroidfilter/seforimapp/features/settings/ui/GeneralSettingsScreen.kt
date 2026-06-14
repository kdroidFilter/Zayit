package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.UpdaterConfig
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.components.AnimatedHorizontalProgressBar
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.update.UpdateMode
import io.github.kdroidfilter.seforimapp.framework.update.UpdateUiState
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book_description
import seforimapp.seforimapp.generated.resources.settings_info_app_version
import seforimapp.seforimapp.generated.resources.settings_info_created_by
import seforimapp.seforimapp.generated.resources.settings_info_license
import seforimapp.seforimapp.generated.resources.settings_info_license_usage
import seforimapp.seforimapp.generated.resources.settings_keep_screen_awake
import seforimapp.seforimapp.generated.resources.settings_keep_screen_awake_description
import seforimapp.seforimapp.generated.resources.settings_persist_session
import seforimapp.seforimapp.generated.resources.settings_persist_session_description
import seforimapp.seforimapp.generated.resources.update_available_banner
import seforimapp.seforimapp.generated.resources.update_check_action
import seforimapp.seforimapp.generated.resources.update_check_failed
import seforimapp.seforimapp.generated.resources.update_checking
import seforimapp.seforimapp.generated.resources.update_download_action
import seforimapp.seforimapp.generated.resources.update_downloading
import seforimapp.seforimapp.generated.resources.update_install_restart
import seforimapp.seforimapp.generated.resources.update_up_to_date

@Composable
fun GeneralSettingsScreen() {
    val viewModel: GeneralSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val version = UpdaterConfig().currentVersion
    val updateService = LocalAppGraph.current.appUpdateService
    val updateState by updateService.state.collectAsState()
    val scope = rememberCoroutineScope()
    GeneralSettingsView(
        state = state,
        version = version,
        updateState = updateState,
        onEvent = viewModel::onEvent,
        onDownloadUpdate = updateService::startDownload,
        onInstallUpdate = updateService::installAndRestart,
        onCheckForUpdate = { scope.launch { updateService.recheck() } },
    )
}

@Composable
private fun GeneralSettingsView(
    state: GeneralSettingsState,
    version: String,
    updateState: UpdateUiState,
    onEvent: (GeneralSettingsEvents) -> Unit,
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeader(
                version = version,
                updateState = updateState,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                onCheckForUpdate = onCheckForUpdate,
            )

            SettingCard(
                title = Res.string.close_book_tree_on_new_book,
                description = Res.string.close_book_tree_on_new_book_description,
                checked = state.closeTreeOnNewBook,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetCloseTreeOnNewBook(it)) },
            )

            SettingCard(
                title = Res.string.settings_persist_session,
                description = Res.string.settings_persist_session_description,
                checked = state.persistSession,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetPersistSession(it)) },
            )

            SettingCard(
                title = Res.string.settings_keep_screen_awake,
                description = Res.string.settings_keep_screen_awake_description,
                checked = state.keepScreenAwakeOnBook,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetKeepScreenAwakeOnBook(it)) },
            )
        }
    }
}

@Composable
private fun AppHeader(
    version: String,
    updateState: UpdateUiState,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Update status banner, reflecting the full check/download state.
        UpdateStatusBanner(
            updateState = updateState,
            onDownloadUpdate = onDownloadUpdate,
            onInstallUpdate = onInstallUpdate,
            onCheckForUpdate = onCheckForUpdate,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.AppIcon),
                contentDescription = "App Icon",
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "זית",
                    fontSize = 22.sp,
                )
                Text(
                    text = stringResource(Res.string.settings_info_app_version, version),
                    fontSize = 13.sp,
                    color = JewelTheme.globalColors.text.info,
                )
                Link(
                    text = stringResource(Res.string.settings_info_created_by),
                    onClick = { UrlOpener.open("https://eliegambache.kdroidfilter.com/") },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_info_license),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
                Text(
                    text = stringResource(Res.string.settings_info_license_usage),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
                )
            }
            IconButton(
                onClick = {
                    UrlOpener.open("https://github.com/kdroidFilter/Zayit")
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    key = AllIconsKeys.Vcs.Vendors.Github,
                    contentDescription = "GitHub",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun UpdateStatusBanner(
    updateState: UpdateUiState,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCheckForUpdate: () -> Unit,
) {
    when (updateState) {
        is UpdateUiState.Available -> {
            val downloadLabel = stringResource(Res.string.update_download_action)
            InlineInformationBanner(
                text = stringResource(Res.string.update_available_banner, updateState.version),
                linkActions = {
                    action(downloadLabel, onClick = onDownloadUpdate)
                },
            )
        }

        is UpdateUiState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InlineInformationBanner(
                    text = "${stringResource(Res.string.update_downloading)} ${updateState.percent}%",
                )
                AnimatedHorizontalProgressBar(
                    value = updateState.percent / 100f,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }

        is UpdateUiState.ReadyToInstall -> {
            val installLabel = stringResource(Res.string.update_install_restart)
            InlineInformationBanner(
                text = stringResource(Res.string.update_available_banner, updateState.version),
                linkActions = {
                    action(installLabel, onClick = onInstallUpdate)
                },
            )
        }

        UpdateUiState.UpToDate -> {
            val checkLabel = stringResource(Res.string.update_check_action)
            InlineSuccessBanner(
                text = stringResource(Res.string.update_up_to_date),
                linkActions = {
                    action(checkLabel, onClick = onCheckForUpdate)
                },
            )
        }

        is UpdateUiState.Error -> {
            val checkLabel = stringResource(Res.string.update_check_action)
            InlineWarningBanner(
                text = stringResource(Res.string.update_check_failed),
                linkActions = {
                    action(checkLabel, onClick = onCheckForUpdate)
                },
            )
        }

        UpdateUiState.Idle, UpdateUiState.Checking ->
            InlineInformationBanner(text = stringResource(Res.string.update_checking))
    }
}

@Composable
@Preview
private fun GeneralSettingsView_Preview() {
    PreviewContainer {
        GeneralSettingsView(
            state = GeneralSettingsState.preview,
            version = "0.3.0",
            updateState = UpdateUiState.Available("0.4.0", UpdateLevel.MINOR, UpdateMode.PROMPT, needsDbWarning = true),
            onEvent = {},
        )
    }
}
