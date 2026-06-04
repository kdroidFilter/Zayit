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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nucleusframework.updater.UpdateLevel
import dev.nucleusframework.updater.UpdaterConfig
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.presentation.utils.UrlOpener
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.update.AppUpdateService
import io.github.kdroidfilter.seforimapp.framework.update.UpdateMode
import io.github.kdroidfilter.seforimapp.framework.update.UpdateUiState
import io.github.kdroidfilter.seforimapp.framework.update.availableVersion
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineSuccessBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
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
import seforimapp.seforimapp.generated.resources.settings_reset_app
import seforimapp.seforimapp.generated.resources.settings_reset_confirm
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_no
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_yes
import seforimapp.seforimapp.generated.resources.settings_reset_done
import seforimapp.seforimapp.generated.resources.settings_reset_warning
import seforimapp.seforimapp.generated.resources.update_available_banner
import seforimapp.seforimapp.generated.resources.update_check_failed
import seforimapp.seforimapp.generated.resources.update_checking
import seforimapp.seforimapp.generated.resources.update_download_action
import seforimapp.seforimapp.generated.resources.update_up_to_date

@Composable
fun GeneralSettingsScreen() {
    val viewModel: GeneralSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val version = UpdaterConfig().currentVersion
    val updateState by LocalAppGraph.current.appUpdateService.state
        .collectAsState()
    GeneralSettingsView(
        state = state,
        version = version,
        updateState = updateState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun GeneralSettingsView(
    state: GeneralSettingsState,
    version: String,
    updateState: UpdateUiState,
    onEvent: (GeneralSettingsEvents) -> Unit,
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeader(version = version, updateState = updateState)

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

            ResetSection(
                resetDone = state.resetDone,
                onReset = { onEvent(GeneralSettingsEvents.ResetApp) },
            )
        }
    }
}

@Composable
private fun AppHeader(
    version: String,
    updateState: UpdateUiState,
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
        UpdateStatusBanner(updateState)

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
private fun UpdateStatusBanner(updateState: UpdateUiState) {
    when (updateState) {
        is UpdateUiState.Available, is UpdateUiState.Downloading, is UpdateUiState.ReadyToInstall -> {
            val downloadLabel = stringResource(Res.string.update_download_action)
            InlineInformationBanner(
                text = stringResource(Res.string.update_available_banner, updateState.availableVersion.orEmpty()),
                linkActions = {
                    action(downloadLabel, onClick = { UrlOpener.open(AppUpdateService.DOWNLOAD_URL) })
                },
            )
        }

        UpdateUiState.UpToDate ->
            InlineSuccessBanner(text = stringResource(Res.string.update_up_to_date))

        is UpdateUiState.Error ->
            InlineWarningBanner(text = stringResource(Res.string.update_check_failed))

        UpdateUiState.Idle, UpdateUiState.Checking ->
            InlineInformationBanner(text = stringResource(Res.string.update_checking))
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
        if (showConfirmation) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.settings_reset_confirm),
                    color = JewelTheme.globalColors.text.warning,
                )
                DefaultButton(onClick = {
                    showConfirmation = false
                    onReset()
                }) {
                    Text(text = stringResource(Res.string.settings_reset_confirm_yes))
                }
                OutlinedButton(onClick = { showConfirmation = false }) {
                    Text(text = stringResource(Res.string.settings_reset_confirm_no))
                }
            }
        } else {
            DefaultButton(onClick = { showConfirmation = true }) {
                Text(text = stringResource(Res.string.settings_reset_app))
            }
        }

        if (resetDone) {
            InlineWarningBanner(
                style = JewelTheme.inlineBannerStyle.warning,
                text = stringResource(Res.string.settings_reset_done),
            )
        } else {
            InlineWarningBanner(
                style = JewelTheme.inlineBannerStyle.warning,
                text = stringResource(Res.string.settings_reset_warning),
            )
        }
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
