package io.github.kdroidfilter.seforimapp.features.onboarding.region

import io.github.kdroidfilter.seforimapp.features.zmanim.data.worldPlaces

class RegionConfigUseCase {
    fun getCountries(): List<String> = worldPlaces.keys.toList()

    fun getCities(countryIndex: Int): List<String> {
        val countries = getCountries()
        if (countryIndex !in countries.indices) return emptyList()
        val country = countries[countryIndex]
        return worldPlaces[country]?.keys?.toList().orEmpty()
    }
}
