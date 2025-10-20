package io.github.kdroidfilter.seforimapp.features.onboarding.screens.licence

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.components.TextWithLink
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.typography
import seforimapp.seforimapp.generated.resources.*

@Composable
fun LicenceScreen(navController: NavController) {
    LicenceView()
}

@Composable
private fun LicenceView() {
    var isChecked by remember { mutableStateOf(false) }

    OnBoardingScaffold(
        title = stringResource(Res.string.license_screen_title),
        bottomAction = {
            Row {
                Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                Text("J'accepte les conditions de licence")
            }
            DefaultButton({}, enabled = isChecked) {
                Text(text = "Accept")
            }
        }) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                fontSize = JewelTheme.typography.h3TextStyle.fontSize
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                FlowRow {
                    Text(text = stringResource(Res.string.license_intro) + " ")
                    TextWithLink(
                        text = stringResource(Res.string.license_otzaria_name),
                        link = "https://github.com/zevisvei/otzaria-library"
                    )
                    Text(text = " " + stringResource(Res.string.license_converted_from) + " ")
                    TextWithLink(
                        text = stringResource(Res.string.license_seforim_name),
                        link = "https://github.com/kdroidFilter/SeforimLibrary"
                    )
                }
                Text(text = stringResource(Res.string.license_sources_notice))
                Text(text = stringResource(Res.string.license_user_responsibility))
                Text(text = stringResource(Res.string.license_disclaimer))
                Row {
                    Text(text = stringResource(Res.string.license_app_free_notice) + " ")
                    TextWithLink(
                        text = stringResource(Res.string.license_agpl_name),
                        link = "https://raw.githubusercontent.com/kdroidFilter/SeforimApp/refs/heads/master/LICENSE"
                    )
                }
                Text(text = stringResource(Res.string.license_share_mitzvah))
            }
        }
    }
}


@Composable
@Preview
private fun LicenceViewPreview() {
    PreviewContainer { LicenceView() }
}