package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.kdroidfilter.seforimapp.core.presentation.components.AccentMarkdownView
import io.github.kdroidfilter.seforimapp.features.settings.ConditionsText
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer

@Composable
fun ConditionsSettingsScreen() {
    AccentMarkdownView(resourcePath = ConditionsText.PATH, transform = ConditionsText::forThisInstall)
}

@Composable
@Preview
private fun ConditionsSettingsViewPreview() {
    PreviewContainer { ConditionsSettingsScreen() }
}
