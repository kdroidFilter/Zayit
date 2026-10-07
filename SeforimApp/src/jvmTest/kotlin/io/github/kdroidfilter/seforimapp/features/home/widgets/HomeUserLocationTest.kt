package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.zmanim.data.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeUserLocationTest {
    private val testPlace = Place(lat = 48.8566, lng = 2.3522, elevation = 35.0, zoneId = "Europe/Paris")

    @Test
    fun `state stores userPlace`() {
        val state =
            HomeUserLocation(
                userPlace = testPlace,
                userCityLabel = null,
                inIsrael = false,
            )
        assertEquals(testPlace, state.userPlace)
    }

    @Test
    fun `state stores userCityLabel`() {
        val state =
            HomeUserLocation(
                userPlace = testPlace,
                userCityLabel = "Paris",
                inIsrael = false,
            )
        assertEquals("Paris", state.userCityLabel)
    }

    @Test
    fun `state can have null userCityLabel`() {
        val state =
            HomeUserLocation(
                userPlace = testPlace,
                userCityLabel = null,
                inIsrael = false,
            )
        assertNull(state.userCityLabel)
    }

    @Test
    fun `preview companion object is available`() {
        val preview = HomeUserLocation.preview
        assertNotNull(preview)
        assertNull(preview.userCityLabel)
    }

    @Test
    fun `preview has valid place`() {
        val preview = HomeUserLocation.preview
        assertNotNull(preview.userPlace)
    }

    @Test
    fun `copy works correctly`() {
        val original =
            HomeUserLocation(
                userPlace = testPlace,
                userCityLabel = "Original",
                inIsrael = false,
            )
        val modified = original.copy(userCityLabel = "Modified")

        assertEquals(testPlace, modified.userPlace)
        assertEquals("Modified", modified.userCityLabel)
    }

    @Test
    fun `equals works correctly`() {
        val state1 = HomeUserLocation(userPlace = testPlace, userCityLabel = "A", inIsrael = false)
        val state2 = HomeUserLocation(userPlace = testPlace, userCityLabel = "A", inIsrael = false)
        val state3 = HomeUserLocation(userPlace = testPlace, userCityLabel = "B", inIsrael = false)

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
