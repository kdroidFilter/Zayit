package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.display.DisplaySettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.display.DisplaySettingsState
import io.github.kdroidfilter.seforimapp.features.settings.display.DisplaySettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.accent_color_default
import seforimapp.seforimapp.generated.resources.accent_color_gold
import seforimapp.seforimapp.generated.resources.accent_color_green
import seforimapp.seforimapp.generated.resources.accent_color_system
import seforimapp.seforimapp.generated.resources.accent_color_teal
import seforimapp.seforimapp.generated.resources.settings_accent_color_label
import seforimapp.seforimapp.generated.resources.settings_compact_mode
import seforimapp.seforimapp.generated.resources.settings_compact_mode_description
import seforimapp.seforimapp.generated.resources.settings_show_zmanim_widgets
import seforimapp.seforimapp.generated.resources.settings_show_zmanim_widgets_description
import seforimapp.seforimapp.generated.resources.settings_theme_style_classic
import seforimapp.seforimapp.generated.resources.settings_theme_style_islands
import seforimapp.seforimapp.generated.resources.settings_theme_style_label
import seforimapp.seforimapp.generated.resources.settings_use_opengl
import seforimapp.seforimapp.generated.resources.settings_use_opengl_description

@Composable
fun DisplaySettingsScreen() {
    val viewModel: DisplaySettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    val mainAppState = LocalAppGraph.current.mainAppState
    val themeStyle by mainAppState.themeStyle.collectAsState()
    val accentColor by mainAppState.accentColor.collectAsState()
    DisplaySettingsView(
        state = state,
        themeStyle = themeStyle,
        accentColor = accentColor,
        onEvent = viewModel::onEvent,
        onThemeStyleChange = { mainAppState.setThemeStyle(it) },
        onAccentColorChange = { mainAppState.setAccentColor(it) },
    )
}

@Composable
private fun DisplaySettingsView(
    state: DisplaySettingsState,
    themeStyle: ThemeStyle,
    onEvent: (DisplaySettingsEvents) -> Unit,
    onThemeStyleChange: (ThemeStyle) -> Unit,
    accentColor: AccentColor = AccentColor.System,
    onAccentColorChange: (AccentColor) -> Unit = {},
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeStyleCard(themeStyle = themeStyle, onStyleChange = onThemeStyleChange)

            AccentColorCard(
                selectedAccent = accentColor,
                onAccentChange = onAccentColorChange,
            )

            SettingCard(
                title = Res.string.settings_show_zmanim_widgets,
                description = Res.string.settings_show_zmanim_widgets_description,
                checked = state.showZmanimWidgets,
                onCheckedChange = { onEvent(DisplaySettingsEvents.SetShowZmanimWidgets(it)) },
            )

            SettingCard(
                title = Res.string.settings_compact_mode,
                description = Res.string.settings_compact_mode_description,
                checked = state.compactMode,
                onCheckedChange = { onEvent(DisplaySettingsEvents.SetCompactMode(it)) },
            )

            // OpenGL setting - Windows only
            if (PlatformInfo.isWindows) {
                SettingCard(
                    title = Res.string.settings_use_opengl,
                    description = Res.string.settings_use_opengl_description,
                    checked = state.useOpenGl,
                    onCheckedChange = { onEvent(DisplaySettingsEvents.SetUseOpenGl(it)) },
                )
            }
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
private fun AccentColorCard(
    selectedAccent: AccentColor,
    onAccentChange: (AccentColor) -> Unit,
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
        Text(text = stringResource(Res.string.settings_accent_color_label))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val swatchSize = 28.dp
            AccentColor.entries.forEach { accent ->
                val isSelected = selectedAccent == accent
                val borderModifier =
                    if (isSelected) {
                        Modifier.border(2.5.dp, JewelTheme.globalColors.text.normal, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                    }
                Column(
                    modifier = Modifier.width(swatchSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (accent == AccentColor.System) {
                        val gradient =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color(0xFFFF6B6B),
                                        Color(0xFFFFD93D),
                                        Color(0xFF6BCB77),
                                        Color(0xFF4D96FF),
                                        Color(0xFFA66CFF),
                                    ),
                                start = Offset.Zero,
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                            )
                        Box(
                            modifier =
                                Modifier
                                    .size(swatchSize)
                                    .clip(CircleShape)
                                    .drawBehind { drawRect(gradient) }
                                    .then(borderModifier)
                                    .clickable { onAccentChange(accent) },
                        )
                    } else {
                        val displayColor = accent.forMode(JewelTheme.isDark)
                        Box(
                            modifier =
                                Modifier
                                    .size(swatchSize)
                                    .clip(CircleShape)
                                    .background(displayColor, CircleShape)
                                    .then(borderModifier)
                                    .clickable { onAccentChange(accent) },
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = accentColorLabel(accent),
                            style =
                                JewelTheme.defaultTextStyle.copy(
                                    color = JewelTheme.globalColors.text.info,
                                    fontSize = 10.sp,
                                ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun accentColorLabel(accent: AccentColor): String =
    when (accent) {
        AccentColor.System -> stringResource(Res.string.accent_color_system)
        AccentColor.Default -> stringResource(Res.string.accent_color_default)
        AccentColor.Teal -> stringResource(Res.string.accent_color_teal)
        AccentColor.Green -> stringResource(Res.string.accent_color_green)
        AccentColor.Gold -> stringResource(Res.string.accent_color_gold)
    }

@Composable
@Preview
private fun DisplaySettingsView_Preview() {
    PreviewContainer {
        DisplaySettingsView(
            state = DisplaySettingsState.preview,
            themeStyle = ThemeStyle.Classic,
            onEvent = {},
            onThemeStyleChange = {},
        )
    }
}
