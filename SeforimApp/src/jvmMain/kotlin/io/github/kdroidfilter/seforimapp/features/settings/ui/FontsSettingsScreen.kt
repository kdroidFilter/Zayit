package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontOption
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_font_book_label
import seforimapp.seforimapp.generated.resources.settings_font_commentary_label
import seforimapp.seforimapp.generated.resources.settings_font_reset_defaults
import seforimapp.seforimapp.generated.resources.settings_font_sources_label
import seforimapp.seforimapp.generated.resources.settings_font_targum_label

@Composable
fun FontsSettingsScreen() {
    val viewModel: FontsSettingsViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    FontsSettingsView(state = state, onEvent = viewModel::onEvent)
}

private const val PREVIEW_TEXT = "דּוֹר הֹלֵךְ וְדוֹר בָּא וְהָאָרֶץ לְעוֹלָם עֹמָדֶת"

@Composable
private fun FontsSettingsView(state: FontsSettingsState, onEvent: (FontsSettingsEvents) -> Unit) {
    val options = remember { FontCatalog.options }
    val optionLabels = options.map { stringResource(it.label) }

    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FontSettingCard(
                label = Res.string.settings_font_book_label,
                fontCode = state.bookFontCode,
                options = options,
                optionLabels = optionLabels,
                onFontChange = { code -> onEvent(FontsSettingsEvents.SetBookFont(code)) }
            )

            FontSettingCard(
                label = Res.string.settings_font_commentary_label,
                fontCode = state.commentaryFontCode,
                options = options,
                optionLabels = optionLabels,
                onFontChange = { code -> onEvent(FontsSettingsEvents.SetCommentaryFont(code)) }
            )

            FontSettingCard(
                label = Res.string.settings_font_targum_label,
                fontCode = state.targumFontCode,
                options = options,
                optionLabels = optionLabels,
                onFontChange = { code -> onEvent(FontsSettingsEvents.SetTargumFont(code)) }
            )

            FontSettingCard(
                label = Res.string.settings_font_sources_label,
                fontCode = state.sourceFontCode,
                options = options,
                optionLabels = optionLabels,
                onFontChange = { code -> onEvent(FontsSettingsEvents.SetSourceFont(code)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onEvent(FontsSettingsEvents.ResetToDefaults) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = stringResource(Res.string.settings_font_reset_defaults))
            }
        }
    }
}

@Composable
private fun FontSettingCard(
    label: StringResource,
    fontCode: String,
    options: List<FontOption>,
    optionLabels: List<String>,
    onFontChange: (String) -> Unit
) {
    val selectedIndex = options.indexOfFirst { it.code == fontCode }.let { if (it >= 0) it else 0 }
    val fontFamily = FontCatalog.familyFor(fontCode)
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
            .background(JewelTheme.globalColors.panelBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(label),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            ListComboBox(
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { idx -> onFontChange(options[idx].code) },
                modifier = Modifier.fillMaxWidth(0.4f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = PREVIEW_TEXT,
                fontFamily = fontFamily,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
@Preview
private fun FontsSettingsView_Preview() {
    PreviewContainer {
        FontsSettingsView(state = FontsSettingsState.preview, onEvent = {})
    }
}
