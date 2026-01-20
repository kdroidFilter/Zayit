package io.github.kdroidfilter.seforimapp.features.onboarding.region

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionConfigStateTest {
    @Test
    fun `default state has empty lists and negative indices`() {
        val state = RegionConfigState()

        assertTrue(state.countries.isEmpty())
        assertEquals(-1, state.selectedCountryIndex)
        assertTrue(state.cities.isEmpty())
        assertEquals(-1, state.selectedCityIndex)
    }

    @Test
    fun `state can be created with countries`() {
        val countries = listOf("Israel", "USA", "France")
        val state = RegionConfigState(countries = countries, selectedCountryIndex = 0)

        assertEquals(3, state.countries.size)
        assertEquals(0, state.selectedCountryIndex)
        assertEquals("Israel", state.countries[0])
    }

    @Test
    fun `state can be created with cities`() {
        val cities = listOf("Jerusalem", "Tel Aviv", "Haifa")
        val state = RegionConfigState(cities = cities, selectedCityIndex = 1)

        assertEquals(3, state.cities.size)
        assertEquals(1, state.selectedCityIndex)
        assertEquals("Tel Aviv", state.cities[1])
    }

    @Test
    fun `copy preserves unchanged values`() {
        val original =
            RegionConfigState(
                countries = listOf("A", "B"),
                selectedCountryIndex = 0,
                cities = listOf("C", "D"),
                selectedCityIndex = 1,
            )
        val modified = original.copy(selectedCityIndex = 0)

        assertEquals(original.countries, modified.countries)
        assertEquals(original.selectedCountryIndex, modified.selectedCountryIndex)
        assertEquals(original.cities, modified.cities)
        assertEquals(0, modified.selectedCityIndex)
    }

    @Test
    fun `preview companion object is available`() {
        val preview = RegionConfigState.preview

        assertTrue(preview.countries.isNotEmpty())
        assertEquals(0, preview.selectedCountryIndex)
        assertTrue(preview.cities.isNotEmpty())
        assertEquals(0, preview.selectedCityIndex)
    }

    @Test
    fun `equals works correctly`() {
        val state1 =
            RegionConfigState(
                countries = listOf("A"),
                selectedCountryIndex = 0,
            )
        val state2 =
            RegionConfigState(
                countries = listOf("A"),
                selectedCountryIndex = 0,
            )

        assertEquals(state1, state2)
    }
}
