package io.github.kdroidfilter.seforimapp.features.onboarding.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RegionConfigViewModel(
    private val useCase: RegionConfigUseCase
) : ViewModel() {

    private val countries = useCase.getCountries()

    private val selectedCountryIndex = MutableStateFlow(-1)
    private val selectedCityIndex = MutableStateFlow(-1)

    val state = combine(
        selectedCountryIndex,
        selectedCityIndex,
    ) { countryIdx, cityIdx ->
        val cities = if (countryIdx >= 0) useCase.getCities(countryIdx) else emptyList()
        RegionConfigState(
            countries = countries,
            selectedCountryIndex = countryIdx,
            cities = cities,
            selectedCityIndex = if (cityIdx in cities.indices) cityIdx else -1
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RegionConfigState(countries = countries)
    )

    fun onEvent(event: RegionConfigEvents) {
        when (event) {
            is RegionConfigEvents.SelectCountry -> {
                selectedCountryIndex.value = event.index
                // Reset city selection when country changes
                selectedCityIndex.value = -1
            }
            is RegionConfigEvents.SelectCity -> selectedCityIndex.value = event.index
        }
    }
}

