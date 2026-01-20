package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigState
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileState
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.UserProfileViewModel
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_region_city_label
import seforimapp.seforimapp.generated.resources.onboarding_region_country_label
import seforimapp.seforimapp.generated.resources.onboarding_user_community_ashkenaze
import seforimapp.seforimapp.generated.resources.onboarding_user_community_label
import seforimapp.seforimapp.generated.resources.onboarding_user_community_sefard
import seforimapp.seforimapp.generated.resources.onboarding_user_community_sepharade
import seforimapp.seforimapp.generated.resources.onboarding_user_first_name_label
import seforimapp.seforimapp.generated.resources.onboarding_user_last_name_label
import seforimapp.seforimapp.generated.resources.settings_category_profile
import seforimapp.seforimapp.generated.resources.settings_category_region

@Composable
fun ProfileSettingsScreen() {
    val userProfileViewModel: UserProfileViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val userProfileState by userProfileViewModel.state.collectAsState()

    val regionViewModel: RegionConfigViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val regionState by regionViewModel.state.collectAsState()

    ProfileSettingsView(
        userProfileState = userProfileState,
        onUserProfileEvent = userProfileViewModel::onEvent,
        regionState = regionState,
        onRegionEvent = regionViewModel::onEvent,
    )
}

@Composable
private fun ProfileSettingsView(
    userProfileState: UserProfileState,
    onUserProfileEvent: (UserProfileEvents) -> Unit,
    regionState: RegionConfigState,
    onRegionEvent: (RegionConfigEvents) -> Unit,
) {
    VerticallyScrollableContainer(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Profile Section
            SectionCard(title = stringResource(Res.string.settings_category_profile)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.onboarding_user_first_name_label))
                        val firstNameState = rememberTextFieldState(userProfileState.firstName)
                        LaunchedEffect(userProfileState.firstName) {
                            if (firstNameState.text != userProfileState.firstName) {
                                firstNameState.setTextAndPlaceCursorAtEnd(userProfileState.firstName)
                            }
                        }
                        LaunchedEffect(firstNameState.text) {
                            val value = firstNameState.text.toString()
                            if (value != userProfileState.firstName) {
                                onUserProfileEvent(UserProfileEvents.FirstNameChanged(value))
                            }
                            AppSettings.setUserFirstName(value.trim())
                        }
                        TextField(state = firstNameState, modifier = Modifier.widthIn(max = 240.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.onboarding_user_last_name_label))
                        val lastNameState = rememberTextFieldState(userProfileState.lastName)
                        LaunchedEffect(userProfileState.lastName) {
                            if (lastNameState.text != userProfileState.lastName) {
                                lastNameState.setTextAndPlaceCursorAtEnd(userProfileState.lastName)
                            }
                        }
                        LaunchedEffect(lastNameState.text) {
                            val value = lastNameState.text.toString()
                            if (value != userProfileState.lastName) {
                                onUserProfileEvent(UserProfileEvents.LastNameChanged(value))
                            }
                            AppSettings.setUserLastName(value.trim())
                        }
                        TextField(state = lastNameState, modifier = Modifier.widthIn(max = 240.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.onboarding_user_community_label),
                        modifier = Modifier.weight(1f),
                    )
                    val communityLabels =
                        listOf(
                            stringResource(Res.string.onboarding_user_community_sepharade),
                            stringResource(Res.string.onboarding_user_community_ashkenaze),
                            stringResource(Res.string.onboarding_user_community_sefard),
                        )
                    ListComboBox(
                        items = communityLabels,
                        selectedIndex = userProfileState.selectedCommunityIndex,
                        onSelectedItemChange = { index ->
                            onUserProfileEvent(UserProfileEvents.SelectCommunity(index))
                            val community = userProfileState.communities.getOrNull(index)
                            AppSettings.setUserCommunityCode(community?.name)
                        },
                        modifier = Modifier.fillMaxWidth(0.33f),
                    )
                }
            }

            // Region Section
            SectionCard(title = stringResource(Res.string.settings_category_region)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.onboarding_region_country_label),
                        modifier = Modifier.weight(1f),
                    )
                    ListComboBox(
                        items = regionState.countries,
                        selectedIndex = regionState.selectedCountryIndex,
                        onSelectedItemChange = { index ->
                            onRegionEvent(RegionConfigEvents.SelectCountry(index))
                            val country = regionState.countries.getOrNull(index)
                            AppSettings.setRegionCountry(country)
                        },
                        modifier = Modifier.fillMaxWidth(0.33f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(Res.string.onboarding_region_city_label),
                        modifier = Modifier.weight(1f),
                    )
                    ListComboBox(
                        items = regionState.cities,
                        selectedIndex = regionState.selectedCityIndex,
                        onSelectedItemChange = { index ->
                            onRegionEvent(RegionConfigEvents.SelectCity(index))
                            val city = regionState.cities.getOrNull(index)
                            AppSettings.setRegionCity(city)
                        },
                        enabled = regionState.selectedCountryIndex >= 0,
                        modifier = Modifier.fillMaxWidth(0.33f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
        )
        content()
    }
}

@Composable
@Preview
private fun ProfileSettingsView_Preview() {
    PreviewContainer {
        ProfileSettingsView(
            userProfileState =
                UserProfileState(
                    firstName = "אברהם",
                    lastName = "כהן",
                    communities = listOf(Community.SEPHARADE, Community.ASHKENAZE, Community.SEFARD),
                    selectedCommunityIndex = 0,
                ),
            onUserProfileEvent = {},
            regionState = RegionConfigState.preview,
            onRegionEvent = {},
        )
    }
}
