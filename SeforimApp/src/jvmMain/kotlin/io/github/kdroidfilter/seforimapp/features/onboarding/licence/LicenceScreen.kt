package io.github.kdroidfilter.seforimapp.features.onboarding.licence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.components.AccentMarkdownView
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.database.DatabaseVersionManager
import io.github.kdroidfilter.seforimapp.framework.database.getDatabasePath
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.license_accept_checkbox
import seforimapp.seforimapp.generated.resources.license_screen_title
import seforimapp.seforimapp.generated.resources.next_button

@Composable
fun LicenceScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState,
) {
    LaunchedEffect(Unit) {
        progressBarState.setProgress(0.1f)
    }
    LicenceView(
        onNext = {
            // Check if DB exists and has compatible version
            val dbExists = runCatching { getDatabasePath() }.isSuccess
            val dbVersionCompatible =
                if (dbExists) {
                    DatabaseVersionManager.isDatabaseVersionCompatible()
                } else {
                    false
                }

            if (dbExists && dbVersionCompatible) {
                // DB exists and version is compatible - skip install flow and go to user info
                navController.navigate(OnBoardingDestination.UserProfilScreen)
            } else {
                // DB doesn't exist or version is incompatible - continue with installation flow
                navController.navigate(OnBoardingDestination.AvailableDiskSpaceScreen)
            }
        },
        onPrevious = { navController.navigateUp() },
    )
}

@Composable
private fun LicenceView(
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
) {
    var isChecked by remember { mutableStateOf(false) }

    OnBoardingScaffold(
        title = stringResource(Res.string.license_screen_title),
        bottomAction = {
            DefaultButton(onClick = { onNext() }, enabled = isChecked) {
                Text(text = stringResource(Res.string.next_button))
            }
        },
    ) {
        AccentMarkdownView(
            resourcePath = "files/CONDITIONS.md",
            modifier = Modifier.fillMaxWidth(),
            includeH3 = false,
            extraItems = {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = { isChecked = it })
                        Spacer(Modifier.width(2.dp))
                        Text(text = stringResource(Res.string.license_accept_checkbox))
                    }
                }
            },
        )
    }
}

@Composable
@Preview
private fun LicenceViewPreview() {
    PreviewContainer { LicenceView() }
}
