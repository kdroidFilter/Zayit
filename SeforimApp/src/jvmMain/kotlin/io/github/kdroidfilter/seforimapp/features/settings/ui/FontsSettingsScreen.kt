package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.typography.FontCatalog
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsEvents
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsState
import io.github.kdroidfilter.seforimapp.features.settings.fonts.FontsSettingsViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_font_book_label
import seforimapp.seforimapp.generated.resources.settings_font_commentary_label
import seforimapp.seforimapp.generated.resources.settings_font_targum_label

@Composable
fun FontsSettingsScreen() {
    val viewModel: FontsSettingsViewModel = LocalAppGraph.current.fontsSettingsViewModel
    val state by viewModel.state.collectAsState()
    FontsSettingsView(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun FontsSettingsView(state: FontsSettingsState, onEvent: (FontsSettingsEvents) -> Unit) {
    val options = remember { FontCatalog.options }
    val optionLabels = options.map { stringResource(it.label) }

    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.settings_font_book_label))
            val selectedIndex = options.indexOfFirst { it.code == state.bookFontCode }.let { if (it >= 0) it else 0 }
            ListComboBox(
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { idx -> onEvent(FontsSettingsEvents.SetBookFont(options[idx].code)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.settings_font_commentary_label))
            val selectedIndex = options.indexOfFirst { it.code == state.commentaryFontCode }.let { if (it >= 0) it else 0 }
            ListComboBox(
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { idx -> onEvent(FontsSettingsEvents.SetCommentaryFont(options[idx].code)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.settings_font_targum_label))
            val selectedIndex = options.indexOfFirst { it.code == state.targumFontCode }.let { if (it >= 0) it else 0 }
            ListComboBox(
                items = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedItemChange = { idx -> onEvent(FontsSettingsEvents.SetTargumFont(options[idx].code)) },
                modifier = Modifier.weight(1f)
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
