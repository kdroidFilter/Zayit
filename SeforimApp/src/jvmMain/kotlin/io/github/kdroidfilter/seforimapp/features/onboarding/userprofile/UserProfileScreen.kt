package io.github.kdroidfilter.seforimapp.features.onboarding.userprofile

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.icons.Ink_pen
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.next_button
import seforimapp.seforimapp.generated.resources.onboarding_user_community_ashkenaze
import seforimapp.seforimapp.generated.resources.onboarding_user_community_label
import seforimapp.seforimapp.generated.resources.onboarding_user_community_sefard
import seforimapp.seforimapp.generated.resources.onboarding_user_community_sepharade
import seforimapp.seforimapp.generated.resources.onboarding_user_info_description
import seforimapp.seforimapp.generated.resources.onboarding_user_info_title
import seforimapp.seforimapp.generated.resources.onboarding_user_first_name_label
import seforimapp.seforimapp.generated.resources.onboarding_user_last_name_label

@Composable
fun UserProfileScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: UserProfileViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()

    // Slightly before region config in the progress
    LaunchedEffect(Unit) { progressBarState.setProgress(0.85f) }

    val canProceed = state.firstName.isNotBlank() && state.lastName.isNotBlank() && state.selectedCommunityIndex >= 0

    UserProfileView(
        state = state,
        onEvent = viewModel::onEvent,
        onNext = {
            val firstName = state.firstName.trim()
            val lastName = state.lastName.trim()
            val community = state.communities.getOrNull(state.selectedCommunityIndex)
            AppSettings.setUserFirstName(firstName)
            AppSettings.setUserLastName(lastName)
            AppSettings.setUserCommunityCode(community?.name)
            navController.navigate(OnBoardingDestination.RegionConfigScreen)
        },
        canProceed = canProceed
    )
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun UserProfileView(
    state: UserProfileState,
    onEvent: (UserProfileEvents) -> Unit,
    onNext: () -> Unit,
    canProceed: Boolean
) {
    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_user_info_title),
        bottomAction = {
            DefaultButton(onClick = onNext, enabled = canProceed) {
                Text(stringResource(Res.string.next_button))
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InlineInformationBanner(
                style = JewelTheme.inlineBannerStyle.information,
                text = stringResource(Res.string.onboarding_user_info_description),
            )

            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column {
                            // First name
                            Text(stringResource(Res.string.onboarding_user_first_name_label))
                            Spacer(Modifier.height(8.dp))
                            val firstNameState = rememberTextFieldState(state.firstName)
                            // Keep UI state in sync with VM state
                            LaunchedEffect(state.firstName) {
                                if (firstNameState.text != state.firstName) {
                                    firstNameState.setTextAndPlaceCursorAtEnd(state.firstName)
                                }
                            }
                            LaunchedEffect(firstNameState.text) {
                                val value = firstNameState.text.toString()
                                if (value != state.firstName) onEvent(UserProfileEvents.FirstNameChanged(value))
                            }
                            TextField(
                                state = firstNameState,
                                modifier = Modifier.widthIn(max = 240.dp),
                            )
                        }
                        Column {

                            // Last name
                            Text(stringResource(Res.string.onboarding_user_last_name_label))
                            Spacer(Modifier.height(8.dp))
                            val lastNameState = rememberTextFieldState(state.lastName)
                            LaunchedEffect(state.lastName) {
                                if (lastNameState.text != state.lastName) {
                                    lastNameState.setTextAndPlaceCursorAtEnd(state.lastName)
                                }
                            }
                            LaunchedEffect(lastNameState.text) {
                                val value = lastNameState.text.toString()
                                if (value != state.lastName) onEvent(UserProfileEvents.LastNameChanged(value))
                            }
                            TextField(
                                state = lastNameState,
                                modifier = Modifier.widthIn(max = 240.dp),
                            )

                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Community selection
                    Text(stringResource(Res.string.onboarding_user_community_label))
                    Spacer(Modifier.height(8.dp))
                    val communityLabels = listOf(
                        stringResource(Res.string.onboarding_user_community_sepharade),
                        stringResource(Res.string.onboarding_user_community_ashkenaze),
                        stringResource(Res.string.onboarding_user_community_sefard),
                    )
                    SpeedSearchArea(Modifier.widthIn(max = 160.dp)) {
                        ListComboBox(
                            items = communityLabels,
                            selectedIndex = state.selectedCommunityIndex,
                            onSelectedItemChange = { index -> onEvent(UserProfileEvents.SelectCommunity(index)) },
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                    Icon(
                        Ink_pen,
                        null,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        tint = JewelTheme.globalColors.text.normal
                    )
                }
            }
        }
    }
}

@Composable
@Preview
private fun UserProfileView_Preview() {
    PreviewContainer {
        UserProfileView(
            state = UserProfileState(
                firstName = "",
                lastName = "",
                communities = listOf(Community.SEPHARADE, Community.ASHKENAZE, Community.SEFARD),
                selectedCommunityIndex = -1
            ),
            onEvent = {},
            onNext = {},
            canProceed = false
        )
    }
}
