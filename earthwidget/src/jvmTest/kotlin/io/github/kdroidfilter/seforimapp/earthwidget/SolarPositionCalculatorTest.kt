package io.github.kdroidfilter.seforimapp.earthwidget

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class SolarPositionCalculatorTest {
    @Test
    fun sunDirectionChangesSmoothlyPerMinute() {
        val latitude = 31.7683
        val longitude = 35.2137
        val earthRotationDegrees = 0f
        val earthTiltDegrees = 23.44f

        val calendar =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2025, Calendar.JUNE, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

        var previous =
            computeSunLightDirectionForEarth(
                referenceTime = calendar.time,
                latitude = latitude,
                longitude = longitude,
                earthRotationDegrees = earthRotationDegrees,
                earthTiltDegrees = earthTiltDegrees,
            )
        var previousVec: TestVec3d = sunVectorFromAngles(previous.lightDegrees, previous.sunElevationDegrees)

        repeat(24 * 60) { minuteIndex ->
            calendar.add(Calendar.MINUTE, 1)
            val current =
                computeSunLightDirectionForEarth(
                    referenceTime = calendar.time,
                    latitude = latitude,
                    longitude = longitude,
                    earthRotationDegrees = earthRotationDegrees,
                    earthTiltDegrees = earthTiltDegrees,
                )

            val currentVec = sunVectorFromAngles(current.lightDegrees, current.sunElevationDegrees)
            val angleDegrees = angleBetweenDegrees(previousVec, currentVec)

            assertTrue(
                angleDegrees < 5.0,
                "Sun direction changed too much between minutes $minuteIndex and ${minuteIndex + 1}: $angleDegrees°",
            )

            previous = current
            previousVec = currentVec
        }
    }
}

private data class TestVec3d(
    val x: Double,
    val y: Double,
    val z: Double,
)

private fun sunVectorFromAngles(
    lightDegrees: Float,
    sunElevationDegrees: Float,
): TestVec3d {
    val az = lightDegrees.toDouble() * PI / 180.0
    val el = sunElevationDegrees.toDouble() * PI / 180.0
    val cosEl = cos(el)
    return TestVec3d(
        x = sin(az) * cosEl,
        y = sin(el),
        z = cos(az) * cosEl,
    )
}

private fun angleBetweenDegrees(
    a: TestVec3d,
    b: TestVec3d,
): Double {
    val dot = (a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0)
    return acos(dot) * 180.0 / PI
}
