package io.github.kdroidfilter.seforimapp.earthwidget

import java.util.Date
import kotlin.time.Instant

// The desktop views keep their java.util.Date instants
private fun Date.asInstant(): Instant = Instant.fromEpochMilliseconds(time)

internal fun computeSunLightDirectionForEarth(
    referenceTime: Date,
    latitude: Double,
    longitude: Double,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): LightDirection = computeSunLightDirectionForEarth(referenceTime.asInstant(), latitude, longitude, earthRotationDegrees, earthTiltDegrees)

internal fun computeSolarPositionNoaaUtc(
    referenceTime: Date,
    latitude: Double,
    longitude: Double,
): SolarPosition = computeSolarPositionNoaaUtc(referenceTime.asInstant(), latitude, longitude)

internal fun computeJulianDayUtc(date: Date): Double = computeJulianDayUtc(date.asInstant())
