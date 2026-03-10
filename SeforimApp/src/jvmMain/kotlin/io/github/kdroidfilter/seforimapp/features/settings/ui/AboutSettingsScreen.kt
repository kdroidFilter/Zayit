package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.kdroidfilter.seforimapp.core.presentation.components.AccentMarkdownView
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer

@Composable
fun AboutSettingsScreen() {
    AccentMarkdownView(resourcePath = "files/ABOUT.md")
}

@Composable
@Preview
private fun AboutSettingsViewPreview() {
    PreviewContainer { AboutSettingsScreen() }
}
