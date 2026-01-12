package io.github.kdroidfilter.seforimapp.features.settings.ui

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigEvents
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigState
import io.github.kdroidfilter.seforimapp.features.onboarding.region.RegionConfigViewModel
import io.github.kdroidfilter.seforimapp.theme.PreviewContainer
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SpeedSearchArea
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.onboarding_region_city_label
import seforimapp.seforimapp.generated.resources.onboarding_region_country_label

@Composable
fun RegionSettingsScreen() {
    val viewModel: RegionConfigViewModel =
        metroViewModel(viewModelStoreOwner = LocalWindowViewModelStoreOwner.current)
    val state by viewModel.state.collectAsState()
    RegionSettingsView(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalJewelApi::class)
@Composable
private fun RegionSettingsView(
    state: RegionConfigState,
    onEvent: (RegionConfigEvents) -> Unit
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
                        onSelectedItemChange = { index ->
                            onEvent(RegionConfigEvents.SelectCountry(index))
                            val country = state.countries.getOrNull(index)
                            AppSettings.setRegionCountry(country)
                        },
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
                        onSelectedItemChange = { index ->
                            onEvent(RegionConfigEvents.SelectCity(index))
                            val city = state.cities.getOrNull(index)
                            AppSettings.setRegionCity(city)
                        },
                        enabled = state.selectedCountryIndex >= 0,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview
private fun RegionSettingsView_Preview() {
    PreviewContainer {
        RegionSettingsView(state = RegionConfigState.preview, onEvent = {})
    }
}
