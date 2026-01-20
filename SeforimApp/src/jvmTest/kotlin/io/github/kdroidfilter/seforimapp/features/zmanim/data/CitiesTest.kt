package io.github.kdroidfilter.seforimapp.features.zmanim.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CitiesTest {
    @Test
    fun `Place data class stores coordinates correctly`() {
        val place = Place(lat = 31.7683, lng = 35.2137, elevation = 800.0)

        assertEquals(31.7683, place.lat)
        assertEquals(35.2137, place.lng)
        assertEquals(800.0, place.elevation)
    }

    @Test
    fun `Place equals works correctly`() {
        val place1 = Place(31.0, 35.0, 100.0)
        val place2 = Place(31.0, 35.0, 100.0)
        val place3 = Place(32.0, 35.0, 100.0)

        assertEquals(place1, place2)
        assertTrue(place1 != place3)
    }

    @Test
    fun `worldPlaces is not empty`() {
        assertTrue(worldPlaces.isNotEmpty())
    }

    @Test
    fun `worldPlaces contains Israel`() {
        assertTrue(worldPlaces.containsKey("ישראל"))
    }

    @Test
    fun `Israel has Jerusalem`() {
        val israel = worldPlaces["ישראל"]
        assertNotNull(israel)
        assertTrue(israel.containsKey("ירושלים"))
    }

    @Test
    fun `Jerusalem has correct coordinates`() {
        val jerusalem = worldPlaces["ישראל"]?.get("ירושלים")
        assertNotNull(jerusalem)
        assertEquals(31.7683, jerusalem.lat, 0.001)
        assertEquals(35.2137, jerusalem.lng, 0.001)
        assertEquals(800.0, jerusalem.elevation, 0.1)
    }

    @Test
    fun `worldPlaces contains United States`() {
        assertTrue(worldPlaces.containsKey("ארצות הברית"))
    }

    @Test
    fun `United States has New York`() {
        val usa = worldPlaces["ארצות הברית"]
        assertNotNull(usa)
        assertTrue(usa.containsKey("ניו יורק"))
    }

    @Test
    fun `New York has correct coordinates`() {
        val newYork = worldPlaces["ארצות הברית"]?.get("ניו יורק")
        assertNotNull(newYork)
        assertEquals(40.7128, newYork.lat, 0.001)
        assertEquals(-74.0060, newYork.lng, 0.001)
    }

    @Test
    fun `worldPlaces contains France`() {
        assertTrue(worldPlaces.containsKey("צרפת"))
    }

    @Test
    fun `France has Paris`() {
        val france = worldPlaces["צרפת"]
        assertNotNull(france)
        assertTrue(france.containsKey("פריז"))
    }

    @Test
    fun `all countries have at least one city`() {
        worldPlaces.forEach { (country, cities) ->
            assertTrue(cities.isNotEmpty(), "Country $country should have at least one city")
        }
    }

    @Test
    fun `all places have valid latitude`() {
        worldPlaces.values.flatMap { it.values }.forEach { place ->
            assertTrue(
                place.lat >= -90.0 && place.lat <= 90.0,
                "Latitude ${place.lat} should be between -90 and 90",
            )
        }
    }

    @Test
    fun `all places have valid longitude`() {
        worldPlaces.values.flatMap { it.values }.forEach { place ->
            assertTrue(
                place.lng >= -180.0 && place.lng <= 180.0,
                "Longitude ${place.lng} should be between -180 and 180",
            )
        }
    }

    @Test
    fun `Israel has many cities`() {
        val israel = worldPlaces["ישראל"]
        assertNotNull(israel)
        assertTrue(israel.size >= 40, "Israel should have at least 40 cities")
    }

    @Test
    fun `Tel Aviv exists in Israel`() {
        val telAviv = worldPlaces["ישראל"]?.get("תל אביב")
        assertNotNull(telAviv)
        assertTrue(telAviv.lat > 32.0 && telAviv.lat < 32.2)
    }

    @Test
    fun `worldPlaces contains expected countries`() {
        val expectedCountries =
            listOf(
                "ישראל",
                "ארצות הברית",
                "קנדה",
                "בריטניה",
                "צרפת",
                "גרמניה",
                "איטליה",
                "שוויץ",
                "אוסטריה",
                "הונגריה",
            )
        expectedCountries.forEach { country ->
            assertTrue(worldPlaces.containsKey(country), "Should contain $country")
        }
    }

    @Test
    fun `total number of places is significant`() {
        val totalPlaces = worldPlaces.values.sumOf { it.size }
        assertTrue(totalPlaces >= 100, "Should have at least 100 places total, got $totalPlaces")
    }
}
