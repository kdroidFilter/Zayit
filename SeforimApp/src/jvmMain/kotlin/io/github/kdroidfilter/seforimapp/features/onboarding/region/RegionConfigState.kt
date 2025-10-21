package io.github.kdroidfilter.seforimapp.features.onboarding.region

import androidx.compose.runtime.Immutable

@Immutable
data class RegionConfigState(
    val countries: List<String> = emptyList(),
    val selectedCountryIndex: Int = -1,
    val cities: List<String> = emptyList(),
    val selectedCityIndex: Int = -1
)

