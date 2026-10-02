package io.github.kdroidfilter.seforimapp.features.home.widgets.solarsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class SolarSystemOptionsTest {
    @Test
    fun roundTripsAndFallsBackToDefaults() {
        val options =
            SolarSystemOptions(showSky = false, daysPerSecond = 1f, proportionalSpin = false, earth = CardFrame(10f, 20f, 300f, 250f))
        assertEquals(options, SolarSystemOptions.decode(options.encode()))
        assertEquals(SolarSystemOptions(), SolarSystemOptions.decode(null))
        assertEquals(SolarSystemOptions(), SolarSystemOptions.decode("not json"))
    }
}
