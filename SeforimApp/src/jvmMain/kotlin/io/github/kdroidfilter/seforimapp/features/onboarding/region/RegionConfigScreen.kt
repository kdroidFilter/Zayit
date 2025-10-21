package io.github.kdroidfilter.seforimapp.features.onboarding.region

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.kdroidfilter.seforimapp.core.presentation.components.PredictiveListComboBox
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.OnBoardingDestination
import io.github.kdroidfilter.seforimapp.features.onboarding.navigation.ProgressBarState
import io.github.kdroidfilter.seforimapp.features.onboarding.ui.components.OnBoardingScaffold
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.EditableListComboBox
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.next_button
import seforimapp.seforimapp.generated.resources.onboarding_region_city_label
import seforimapp.seforimapp.generated.resources.onboarding_region_country_label
import seforimapp.seforimapp.generated.resources.onboarding_region_title

@Composable
fun RegionConfigScreen(
    navController: NavController,
    progressBarState: ProgressBarState = ProgressBarState
) {
    val viewModel: RegionConfigViewModel = LocalAppGraph.current.regionConfigViewModel
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { progressBarState.setProgress(0.9f) }

    RegionConfigView(
        state = state,
        onEvent = viewModel::onEvent,
        onNext = {
            val countryIdx = state.selectedCountryIndex
            val cityIdx = state.selectedCityIndex
            if (countryIdx >= 0 && cityIdx >= 0) {
                val country = state.countries[countryIdx]
                val city = state.cities[cityIdx]
                AppSettings.setRegionCountry(country)
                AppSettings.setRegionCity(city)
            }
            navController.navigate(OnBoardingDestination.FinishScreen)
        }
    )
}

@Composable
private fun RegionConfigView(
    state: RegionConfigState,
    onEvent: (RegionConfigEvents) -> Unit,
    onNext: () -> Unit
) {
    val canProceed = state.selectedCountryIndex >= 0 && state.selectedCityIndex >= 0

    OnBoardingScaffold(
        title = stringResource(Res.string.onboarding_region_title),
        bottomAction = {
            DefaultButton(onClick = onNext, enabled = canProceed) {
                Text(stringResource(Res.string.next_button))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Country selector (Speed Search)
            Text(stringResource(Res.string.onboarding_region_country_label))
            SpeedSearchArea(Modifier.widthIn(max = 360.dp)) {
                PredictiveListComboBox(
                    items = state.countries,
                    selectedIndex = state.selectedCountryIndex,
                    onSelectedItemChange = { index -> onEvent(RegionConfigEvents.SelectCountry(index)) },
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // City selector (enabled only after a country is selected)
            Text(stringResource(Res.string.onboarding_region_city_label))
            SpeedSearchArea(Modifier.widthIn(max = 360.dp)) {
                PredictiveListComboBox(
                    items = state.cities,
                    selectedIndex = state.selectedCityIndex,
                    onSelectedItemChange = { index -> onEvent(RegionConfigEvents.SelectCity(index)) },
                    enabled = state.selectedCountryIndex >= 0,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}
