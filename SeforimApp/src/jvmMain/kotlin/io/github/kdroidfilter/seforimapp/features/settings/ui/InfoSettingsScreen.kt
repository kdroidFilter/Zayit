package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.platformtools.getAppVersion
import io.github.kdroidfilter.seforimapp.core.presentation.components.TextWithLink
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_info_app_version
import seforimapp.seforimapp.generated.resources.settings_info_created_by
import seforimapp.seforimapp.generated.resources.settings_info_open_source
import seforimapp.seforimapp.generated.resources.settings_info_repo_prompt
import seforimapp.seforimapp.generated.resources.settings_info_repo_url_text

@Composable
fun InfoSettingsScreen() {
    val version = getAppVersion()
    InfoSettingsView(version)
}

@Composable
private fun InfoSettingsView(version: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stringResource(Res.string.settings_info_app_version, version))
        Text(text = stringResource(Res.string.settings_info_created_by))
        Text(text = stringResource(Res.string.settings_info_open_source))

        Row {
            Text(text = stringResource(Res.string.settings_info_repo_prompt))
            Spacer(Modifier.width(8.dp))
            TextWithLink(
                text = stringResource(Res.string.settings_info_repo_url_text),
                link = "https://github.com/kdroidFilter/SeforimApp"
            )
        }


    }
}


@Composable
@Preview
private fun InfoSettingsView_Preview() {
    PreviewContainer {
        InfoSettingsView(version = "0.3.0")
    }
}
