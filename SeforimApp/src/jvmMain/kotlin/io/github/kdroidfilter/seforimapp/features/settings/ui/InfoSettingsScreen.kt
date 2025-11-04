package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_info_app_version

@Composable
fun InfoSettingsScreen() {
    val version = remember { currentAppVersion() }
    InfoSettingsView(version)
}

@Composable
private fun InfoSettingsView(version: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(Res.string.settings_info_app_version, version))
    }
}

private class VersionProbe

private fun currentAppVersion(): String {
    // Prefer reading from JAR manifest when packaged
    val implVersion = VersionProbe::class.java.`package`?.implementationVersion
    if (!implVersion.isNullOrBlank()) return implVersion

    // Fallback: mirror build script tag parsing for CI-based builds
    val ref = System.getenv("GITHUB_REF") ?: ""
    return if (ref.startsWith("refs/tags/")) {
        val tag = ref.removePrefix("refs/tags/")
        if (tag.startsWith("v")) tag.substring(1) else tag
    } else {
        // Default development version; keep in sync with build script default
        "0.3.0"
    }
}

@Composable
@Preview
private fun InfoSettingsView_Preview() {
    PreviewContainer {
        InfoSettingsView(version = "0.3.0")
    }
}
