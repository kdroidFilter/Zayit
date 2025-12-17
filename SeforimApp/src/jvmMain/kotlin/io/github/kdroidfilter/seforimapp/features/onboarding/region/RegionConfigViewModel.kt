package io.github.kdroidfilter.seforimapp.features.onboarding.region

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@ContributesIntoMap(AppScope::class)
@ViewModelKey(RegionConfigViewModel::class)
class RegionConfigViewModel @Inject constructor(
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

    init {
        // Initialize from persisted settings when available
        val savedCountry = AppSettings.getRegionCountry()
        val countryIdx = savedCountry?.let { countries.indexOf(it) } ?: -1
        if (countryIdx >= 0) {
            selectedCountryIndex.value = countryIdx
            val cities = useCase.getCities(countryIdx)
            val savedCity = AppSettings.getRegionCity()
            val cityIdx = savedCity?.let { cities.indexOf(it) } ?: -1
            if (cityIdx >= 0) selectedCityIndex.value = cityIdx
        }
    }

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
