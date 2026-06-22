package io.github.kdroidfilter.seforimapp.features.onboarding.region

import androidx.compose.runtime.Immutable
import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces

@Immutable
data class RegionConfigState(
    val countries: List<String> = emptyList(),
    val selectedCountryIndex: Int = -1,
    val cities: List<String> = emptyList(),
    val selectedCityIndex: Int = -1,
) {
    companion object {
        val preview =
            RegionConfigState(
                countries = worldPlaces.keys.toList(),
                selectedCountryIndex = 0,
                cities =
                    worldPlaces.values
                        .first()
                        .keys
                        .toList(),
                selectedCityIndex = 0,
            )
    }
}
