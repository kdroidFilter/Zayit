package io.github.kdroidfilter.seforimapp.features.onboarding.region

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.icons.Map
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.theme.inlineBannerStyle
import seforimapp.seforimapp.generated.resources.*

@Composable
fun RegionConfigScreen(
    navController: NavController, progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: RegionConfigViewModel = LocalAppGraph.current.regionConfigViewModel
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { progressBarState.setProgress(0.9f) }

    RegionConfigView(
        state = state, onEvent = viewModel::onEvent, onNext = {
            val countryIdx = state.selectedCountryIndex
            val cityIdx = state.selectedCityIndex
            if (countryIdx >= 0 && cityIdx >= 0) {
                val country = state.countries[countryIdx]
                val city = state.cities[cityIdx]
                AppSettings.setRegionCountry(country)
                AppSettings.setRegionCity(city)
            }
            navController.navigate(OnBoardingDestination.FinishScreen)
        })
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun RegionConfigView(
    state: RegionConfigState, onEvent: (RegionConfigEvents) -> Unit, onNext: () -> Unit
) {
    val canProceed = state.selectedCountryIndex >= 0 && state.selectedCityIndex >= 0

    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_region_title), bottomAction = {
            DefaultButton(onClick = onNext, enabled = canProceed) {
                Text(stringResource(Res.string.next_button))
            }
        }) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            InlineInformationBanner(
                style = JewelTheme.inlineBannerStyle.information,
                text = stringResource(Res.string.onboarding_region_description),
            )


            Row(Modifier.fillMaxSize()) {

                Column(
                    Modifier.fillMaxSize().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Country selector (Speed Search)
                    Text(stringResource(Res.string.onboarding_region_country_label))
                    Spacer(Modifier.height(8.dp))
                    SpeedSearchArea(Modifier.widthIn(max = 240.dp)) {
                        ListComboBox(
                            items = state.countries,
                            selectedIndex = state.selectedCountryIndex,
                            onSelectedItemChange = { index -> onEvent(RegionConfigEvents.SelectCountry(index)) },
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // City selector (enabled only after a country is selected)
                    Text(stringResource(Res.string.onboarding_region_city_label))
                    Spacer(Modifier.height(8.dp))
                    SpeedSearchArea(Modifier.widthIn(max = 240.dp)) {
                        ListComboBox(
                            items = state.cities,
                            selectedIndex = state.selectedCityIndex,
                            onSelectedItemChange = { index -> onEvent(RegionConfigEvents.SelectCity(index)) },
                            enabled = state.selectedCountryIndex >= 0,
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                    Icon(
                        Map,
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
private fun RegionConfigView_Preview() {
    PreviewContainer {
        RegionConfigView(state = RegionConfigState.preview, onEvent = {}, onNext = {})
    }
}