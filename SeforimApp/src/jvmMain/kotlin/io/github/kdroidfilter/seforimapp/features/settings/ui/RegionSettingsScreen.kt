package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigState
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_region_city_label
import seforimapp.seforimapp.generated.resources.onboarding_region_country_label
import seforimapp.seforimapp.generated.resources.save_button

@Composable
fun RegionSettingsScreen() {
    val viewModel: RegionConfigViewModel = LocalAppGraph.current.regionConfigViewModel
    val state by viewModel.state.collectAsState()
    val canSave = state.selectedCountryIndex >= 0 && state.selectedCityIndex >= 0
    RegionSettingsView(
        state = state,
        onEvent = viewModel::onEvent,
        onSave = {
            val countryIdx = state.selectedCountryIndex
            val cityIdx = state.selectedCityIndex
            if (countryIdx >= 0 && cityIdx >= 0) {
                val country = state.countries[countryIdx]
                val city = state.cities[cityIdx]
                AppSettings.setRegionCountry(country)
                AppSettings.setRegionCity(city)
            }
        },
        canSave = canSave
    )
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun RegionSettingsView(
    state: RegionConfigState,
    onEvent: (RegionConfigEvents) -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.onboarding_region_country_label))
                SpeedSearchArea(Modifier.widthIn(max = 240.dp)) {
                    ListComboBox(
                        items = state.countries,
                        selectedIndex = state.selectedCountryIndex,
                        onSelectedItemChange = { index -> onEvent(RegionConfigEvents.SelectCountry(index)) },
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.onboarding_region_city_label))
                SpeedSearchArea(Modifier.widthIn(max = 240.dp)) {
                    ListComboBox(
                        items = state.cities,
                        selectedIndex = state.selectedCityIndex,
                        onSelectedItemChange = { index -> onEvent(RegionConfigEvents.SelectCity(index)) },
                        enabled = state.selectedCountryIndex >= 0,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DefaultButton(onClick = onSave, enabled = canSave) {
            Text(stringResource(Res.string.save_button))
        }
    }
}

@Composable
@Preview
private fun RegionSettingsView_Preview() {
    PreviewContainer {
        RegionSettingsView(state = RegionConfigState.preview, onEvent = {}, onSave = {}, canSave = true)
    }
}
