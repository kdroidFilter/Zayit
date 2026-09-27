package io.github.kdroidfilter.seforimapp.earthwidget

import io.github.erkko68.filament.compose.scene.Direction
import java.util.Date
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneFrameTest {
    private fun frameAt(
        date: Date,
        lat: Float,
        lon: Float,
        anchorDate: Date = date,
    ): SceneFrame {
        val jd = computeJulianDayUtc(date)
        val state =
            EarthRenderState(
                renderSizePx = 600,
                earthRotationDegrees = 0f,
                lightDegrees = 0f,
                sunElevationDegrees = 0f,
                earthTiltDegrees = 23.44f,
                moonOrbitDegrees = 0f,
                markerLatitudeDegrees = lat,
                markerLongitudeDegrees = lon,
                showBackgroundStars = false,
                showOrbitPath = false,
                moonLightDegrees = 0f,
                moonSunElevationDegrees = 0f,
                moonPhaseAngleDegrees = null,
                julianDay = jd,
                earthSizeFraction = 0.6f,
                siderealDegrees = (greenwichMeanSiderealTimeRad(jd) * 180.0 / PI).toFloat(),
                sunLongitudeDegrees = computeSunEclipticLongitude(jd),
                moonNodeDegrees = computeMoonAscendingNodeLongitude(jd),
                viewAzimuthFromSunDegrees =
                    computeJulianDayUtc(anchorDate).let { a ->
                        markerAzimuthFromSunDegrees(
                            (greenwichMeanSiderealTimeRad(a) * 180.0 / PI).toFloat(),
                            computeSunEclipticLongitude(a),
                            23.44f,
                            lat,
                            lon,
                        )
                    },
            )
        return SceneFrame(state, computeSceneGeometry(600, 0.6f))
    }

    private fun Direction.dot(o: Direction) = x * o.x + y * o.y + z * o.z

    @Test
    fun sunElevationAtMarkerMatchesNoaa() {
        // Jerusalem, a few instants across the year and the day
        val lat = 31.78f
        val lon = 35.22f
        listOf(1_700_000_000_000L, 1_719_000_000_000L, 1_727_400_000_000L, 1_735_000_000_000L).forEach { ms ->
            val date = Date(ms)
            val frame = frameAt(date, lat, lon)
            val u = latLonToUnitVector(lat, lon)
            val up = frame.earth * Direction(u.x, u.y, u.z)
            val elevation = asin(up.dot(frame.sun)) * 180f / PI.toFloat()
            val noaa = computeSolarPositionNoaaUtc(date, lat.toDouble(), lon.toDouble()).elevationDegrees
            assertEquals(noaa.toFloat(), elevation, 1.5f, "at $date")
        }
    }

    @Test
    fun earthAxisIsTiltedByTheObliquityFromTheEclipticPole() {
        val axis = frameAt(Date(1_727_400_000_000L), 0f, 0f).earth * Direction(0f, 1f, 0f)
        assertEquals(cos(23.44f * PI.toFloat() / 180f), axis.y, 1e-4f)
    }

    @Test
    fun dayOneMoonSitsTowardTheSun() {
        val frame = frameAt(Date(1_727_400_000_000L), 0f, 0f)
        val moon = frame.orbitPoint(ORBIT_DAY_LABEL_START_DEGREES)
        assertEquals(1f, moon.dot(frame.sun), 1f - cos(5.2f * PI.toFloat() / 180f) + 1e-4f)
        // Day ~8 (first quarter) is 90° east of the Sun: counter-clockwise from the north pole (+Y).
        val quarter = frame.orbitPoint(ORBIT_DAY_LABEL_START_DEGREES + 90f)
        val crossY = frame.sun.z * quarter.x - frame.sun.x * quarter.z
        assertEquals(1f, crossY, 0.01f)
    }

    @Test
    fun anchoredCameraFacesTheMarkerThenStaysSunFixed() {
        val anchor = Date(1_727_400_000_000L)
        val u = latLonToUnitVector(31.78f, 35.22f).let { Direction(it.x, it.y, it.z) }

        val atAnchor = frameAt(anchor, 31.78f, 35.22f).let { it.view * it.earth * u }
        assertEquals(0f, atAnchor.x, 1e-4f)

        // Six hours later the Earth has turned ~90° under the same (Sun-fixed) camera
        val later = frameAt(Date(anchor.time + 6 * 3_600_000L), 31.78f, 35.22f, anchorDate = anchor).let { it.view * it.earth * u }
        val sunLater = frameAt(Date(anchor.time + 6 * 3_600_000L), 31.78f, 35.22f, anchorDate = anchor).let { it.view * it.sun }
        val sunAtAnchor = frameAt(anchor, 31.78f, 35.22f).let { it.view * it.sun }
        assertEquals(sunAtAnchor.x, sunLater.x, 0.01f)
        assertEquals(sunAtAnchor.z, sunLater.z, 0.01f)
        kotlin.test.assertTrue(kotlin.math.abs(later.x) > 0.5f)
    }
}
