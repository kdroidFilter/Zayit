package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.animation.AnimatedVisibility
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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.nucleus.updater.UpdaterConfig
import io.github.kdroidfilter.seforimapp.core.presentation.components.ExpandCollapseIcon
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.general.GeneralSettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.InlineWarningBanner
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
import seforimapp.seforimapp.generated.resources.AppIcon
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book
import seforimapp.seforimapp.generated.resources.close_book_tree_on_new_book_description
import seforimapp.seforimapp.generated.resources.settings_compact_mode
import seforimapp.seforimapp.generated.resources.settings_compact_mode_description
import seforimapp.seforimapp.generated.resources.settings_info_app_version
import seforimapp.seforimapp.generated.resources.settings_info_created_by
import seforimapp.seforimapp.generated.resources.settings_info_license
import seforimapp.seforimapp.generated.resources.settings_info_license_usage
import seforimapp.seforimapp.generated.resources.settings_persist_session
import seforimapp.seforimapp.generated.resources.settings_persist_session_description
import seforimapp.seforimapp.generated.resources.settings_reset_app
import seforimapp.seforimapp.generated.resources.settings_reset_confirm
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_no
import seforimapp.seforimapp.generated.resources.settings_reset_confirm_yes
import seforimapp.seforimapp.generated.resources.settings_reset_done
import seforimapp.seforimapp.generated.resources.settings_reset_warning
import seforimapp.seforimapp.generated.resources.settings_show_zmanim_widgets
import seforimapp.seforimapp.generated.resources.settings_show_zmanim_widgets_description
import seforimapp.seforimapp.generated.resources.settings_theme_style_classic
import seforimapp.seforimapp.generated.resources.settings_theme_style_islands
import seforimapp.seforimapp.generated.resources.settings_theme_style_label
import seforimapp.seforimapp.generated.resources.settings_use_opengl
import seforimapp.seforimapp.generated.resources.settings_use_opengl_description
import seforimapp.seforimapp.generated.resources.update_available_banner
import seforimapp.seforimapp.generated.resources.update_download_action
import java.awt.Desktop
import java.net.URI

@Composable
fun GeneralSettingsScreen() {
    val viewModel: GeneralSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val version = UpdaterConfig().currentVersion
    val mainAppState = LocalAppGraph.current.mainAppState
    val updateVersion by mainAppState.updateAvailable.collectAsState()
    val themeStyle by mainAppState.themeStyle.collectAsState()
    GeneralSettingsView(
        state = state,
        version = version,
        updateVersion = updateVersion,
        themeStyle = themeStyle,
        onEvent = viewModel::onEvent,
        onThemeStyleChange = { mainAppState.setThemeStyle(it) },
    )
}

@Composable
private fun GeneralSettingsView(
    state: GeneralSettingsState,
    version: String,
    updateVersion: String?,
    themeStyle: ThemeStyle,
    onEvent: (GeneralSettingsEvents) -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit,
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeader(version = version, updateVersion = updateVersion)

            ThemeStyleCard(themeStyle = themeStyle, onStyleChange = onThemeStyleChange)

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
                title = Res.string.settings_show_zmanim_widgets,
                description = Res.string.settings_show_zmanim_widgets_description,
                checked = state.showZmanimWidgets,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetShowZmanimWidgets(it)) },
            )

            SettingCard(
                title = Res.string.settings_compact_mode,
                description = Res.string.settings_compact_mode_description,
                checked = state.compactMode,
                onCheckedChange = { onEvent(GeneralSettingsEvents.SetCompactMode(it)) },
            )

            // OpenGL setting - Windows only
            if (PlatformInfo.isWindows) {
                SettingCard(
                    title = Res.string.settings_use_opengl,
                    description = Res.string.settings_use_opengl_description,
                    checked = state.useOpenGl,
                    onCheckedChange = { onEvent(GeneralSettingsEvents.SetUseOpenGl(it)) },
                )
            }

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
    updateVersion: String? = null,
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
        // Update available banner
        if (updateVersion != null) {
            val downloadLabel = stringResource(Res.string.update_download_action)
            InlineInformationBanner(
                style = JewelTheme.inlineBannerStyle.information,
                text = stringResource(Res.string.update_available_banner, updateVersion),
                linkActions = {
                    action(
                        downloadLabel,
                        onClick = {
                            Desktop.getDesktop().browse(
                                URI(io.github.kdroidfilter.seforimapp.framework.update.AppUpdateChecker.DOWNLOAD_URL),
                            )
                        },
                    )
                },
            )
        }

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
                Text(
                    text = stringResource(Res.string.settings_info_created_by),
                    fontSize = 12.sp,
                    color = JewelTheme.globalColors.text.info,
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
                    Desktop.getDesktop().browse(URI("https://github.com/kdroidFilter/Zayit"))
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
private fun SettingCard(
    title: StringResource,
    description: StringResource,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var expanded by remember { mutableStateOf(false) }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
                Text(text = stringResource(title))
            }

            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(24.dp),
            ) {
                ExpandCollapseIcon(expanded = expanded, size = 16.dp)
            }
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = stringResource(description),
                fontSize = 12.sp,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.padding(start = 28.dp),
            )
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
private fun ThemeStyleCard(
    themeStyle: ThemeStyle,
    onStyleChange: (ThemeStyle) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(Res.string.settings_theme_style_label))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeStyle.entries.forEach { style ->
                val label =
                    when (style) {
                        ThemeStyle.Classic -> stringResource(Res.string.settings_theme_style_classic)
                        ThemeStyle.Islands -> stringResource(Res.string.settings_theme_style_islands)
                    }
                RadioButtonRow(
                    text = label,
                    selected = themeStyle == style,
                    onClick = { onStyleChange(style) },
                )
            }
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
            updateVersion = "0.4.0",
            themeStyle = ThemeStyle.Classic,
            onEvent = {},
            onThemeStyleChange = {},
        )
    }
}
