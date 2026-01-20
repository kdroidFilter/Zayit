package io.github.kdroidfilter.seforimapp.features.onboarding.region

import kotlin.test.Test
import kotlin.test.assertTrue

class RegionConfigUseCaseTest {
    @Test
    fun countries_and_cities_available() {
        val useCase = RegionConfigUseCase()
        val countries = useCase.getCountries()
        assertTrue(countries.isNotEmpty(), "Countries list should not be empty")
        val cities = useCase.getCities(0)
        assertTrue(cities.isNotEmpty(), "Cities list for first country should not be empty")
    }
}
