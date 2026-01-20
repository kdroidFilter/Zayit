package io.github.kdroidfilter.seforimapp.earthwidget

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Solar azimuth/elevation at a specific location.
 *
 * @property azimuthDegreesFromNorth Azimuth in degrees (North = 0, clockwise).
 * @property elevationDegrees Elevation above horizon in degrees.
 */
internal data class SolarPosition(
    val azimuthDegreesFromNorth: Double,
    val elevationDegrees: Double,
)

/**
 * Computes the sun direction angles used by the renderer (world coordinates).
 *
 * The returned angles match the convention used by [renderTexturedSphereArgb]:
 * - `lightDegrees`: azimuth around the Y axis (0° = +Z, 90° = +X).
 * - `sunElevationDegrees`: elevation above the XZ plane (positive = +Y).
 */
internal fun computeSunLightDirectionForEarth(
    referenceTime: Date,
    latitude: Double,
    longitude: Double,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): LightDirection {
    val solarPosition =
        computeSolarPositionNoaaUtc(
            referenceTime = referenceTime,
            latitude = latitude,
            longitude = longitude,
        )
    return computeSunDirectionWorld(
        latitude = latitude,
        longitude = longitude,
        azimuthDegrees = solarPosition.azimuthDegreesFromNorth,
        elevationDegrees = solarPosition.elevationDegrees.toFloat(),
        earthRotationDegrees = earthRotationDegrees,
        earthTiltDegrees = earthTiltDegrees,
    )
}

/**
 * Computes solar position using NOAA equations with the input time interpreted as an instant (UTC-based).
 *
 * This avoids discontinuities caused by mixing local calendar fields with an algorithm that expects UTC.
 */
internal fun computeSolarPositionNoaaUtc(
    referenceTime: Date,
    latitude: Double,
    longitude: Double,
): SolarPosition {
    val julianDay = computeJulianDayUtc(referenceTime)
    val julianCenturies = (julianDay - 2451545.0) / 36525.0

    val meanLongitude =
        normalizeDegrees360(
            280.46646 + julianCenturies * (36000.76983 + julianCenturies * 0.0003032),
        )
    val meanAnomaly =
        normalizeDegrees360(
            357.52911 + julianCenturies * (35999.05029 - 0.0001537 * julianCenturies),
        )
    val earthOrbitEccentricity =
        0.016708634 -
            julianCenturies * (0.000042037 + 0.0000001267 * julianCenturies)

    val meanAnomalyRad = Math.toRadians(meanAnomaly)
    val sinM = sin(meanAnomalyRad)
    val sin2M = sin(2.0 * meanAnomalyRad)
    val sin3M = sin(3.0 * meanAnomalyRad)

    val equationOfCenter =
        sinM * (1.914602 - julianCenturies * (0.004817 + 0.000014 * julianCenturies)) +
            sin2M * (0.019993 - 0.000101 * julianCenturies) +
            sin3M * 0.000289

    val trueLongitude = meanLongitude + equationOfCenter
    val omega = 125.04 - 1934.136 * julianCenturies
    val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(Math.toRadians(omega))

    val meanObliquity =
        23.0 + (
            26.0 + (
                (21.448 - julianCenturies * (46.815 + julianCenturies * (0.00059 - julianCenturies * 0.001813))) / 60.0
            )
        ) / 60.0
    val obliquityCorrection = meanObliquity + 0.00256 * cos(Math.toRadians(omega))

    val obliquityRad = Math.toRadians(obliquityCorrection)
    val apparentLongitudeRad = Math.toRadians(apparentLongitude)

    val sinDeclination = (sin(obliquityRad) * sin(apparentLongitudeRad)).coerceIn(-1.0, 1.0)
    val declinationRad = asin(sinDeclination)
    val cosDeclination = cos(declinationRad)

    val y = tan(obliquityRad / 2.0)
    val y2 = y * y
    val meanLongitudeRad = Math.toRadians(meanLongitude)

    val equationOfTimeMinutes =
        4.0 *
            Math.toDegrees(
                y2 * sin(2.0 * meanLongitudeRad) -
                    2.0 * earthOrbitEccentricity * sinM +
                    4.0 * earthOrbitEccentricity * y2 * sinM * cos(2.0 * meanLongitudeRad) -
                    0.5 * y2 * y2 * sin(4.0 * meanLongitudeRad) -
                    1.25 * earthOrbitEccentricity * earthOrbitEccentricity * sin(2.0 * meanAnomalyRad),
            )

    val utcMinutes = utcMinutesOfDay(referenceTime)
    val trueSolarTimeMinutes = normalizeMinutes1440(utcMinutes + equationOfTimeMinutes + 4.0 * longitude)

    var hourAngleDegrees = trueSolarTimeMinutes / 4.0 - 180.0
    if (hourAngleDegrees < -180.0) hourAngleDegrees += 360.0

    val hourAngleRad = Math.toRadians(hourAngleDegrees)
    val latRad = Math.toRadians(latitude)
    val sinLat = sin(latRad)
    val cosLat = cos(latRad)

    val cosZenith = (sinLat * sinDeclination + cosLat * cosDeclination * cos(hourAngleRad)).coerceIn(-1.0, 1.0)
    val zenithRad = acos(cosZenith)
    val elevationDegrees = 90.0 - Math.toDegrees(zenithRad)

    val azimuthRad =
        atan2(
            sin(hourAngleRad),
            cos(hourAngleRad) * sinLat - tan(declinationRad) * cosLat,
        )
    val azimuthDegreesFromNorth = normalizeDegrees360(Math.toDegrees(azimuthRad) + 180.0)

    return SolarPosition(
        azimuthDegreesFromNorth = azimuthDegreesFromNorth,
        elevationDegrees = elevationDegrees,
    )
}

/**
 * Converts a [Date] instant to Julian Day number (UTC), including fractional day.
 */
internal fun computeJulianDayUtc(date: Date): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
    var year = cal.get(Calendar.YEAR)
    var month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val millisecond = cal.get(Calendar.MILLISECOND)

    if (month <= 2) {
        year -= 1
        month += 12
    }

    val A = year / 100
    val B = 2 - A + A / 4

    val dayFraction =
        (
            hour +
                minute / 60.0 +
                second / 3600.0 +
                millisecond / 3_600_000.0
        ) / 24.0

    return floor(365.25 * (year + 4716)) +
        floor(30.6001 * (month + 1)) +
        day +
        dayFraction +
        B -
        1524.5
}

private fun utcMinutesOfDay(date: Date): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)
    val millisecond = cal.get(Calendar.MILLISECOND)
    return hour * 60.0 + minute + second / 60.0 + millisecond / 60_000.0
}

private fun normalizeDegrees360(degrees: Double): Double {
    val mod = degrees % 360.0
    return if (mod < 0.0) mod + 360.0 else mod
}

private fun normalizeMinutes1440(minutes: Double): Double {
    val mod = minutes % 1440.0
    return if (mod < 0.0) mod + 1440.0 else mod
}

/**
 * Transforms sun direction from local horizontal (azimuth/elevation at observer) to world coordinates.
 */
private fun computeSunDirectionWorld(
    latitude: Double,
    longitude: Double,
    azimuthDegrees: Double,
    elevationDegrees: Float,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): LightDirection {
    val latRad = Math.toRadians(latitude)
    val lonRad = Math.toRadians(longitude)
    val azRad = Math.toRadians(azimuthDegrees)
    val elRad = Math.toRadians(elevationDegrees.toDouble())

    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinLon = sin(lonRad)
    val cosLon = cos(lonRad)

    // Local ENU basis in Earth-fixed coordinates (matching the renderer's axis convention).
    val eastX = cosLon
    val eastY = 0.0
    val eastZ = -sinLon

    val northX = -sinLat * sinLon
    val northZ = -sinLat * cosLon

    val upX = cosLat * sinLon
    val upZ = cosLat * cosLon

    val sinAz = sin(azRad)
    val cosAz = cos(azRad)
    val cosEl = cos(elRad)
    val sinEl = sin(elRad)

    val earthDir =
        combineHorizontalDirection(
            sinAz = sinAz,
            cosAz = cosAz,
            cosEl = cosEl,
            sinEl = sinEl,
            eastX = eastX,
            eastZ = eastZ,
            northX = northX,
            northZ = northZ,
            upX = upX,
            upZ = upZ,
            cosLat = cosLat,
            sinLat = sinLat,
        ).normalized()
    val worldDir = earthToWorld(earthDir, earthRotationDegrees, earthTiltDegrees).normalized()

    return LightDirection(
        lightDegrees = Math.toDegrees(atan2(worldDir.x, worldDir.z)).toFloat(),
        sunElevationDegrees = Math.toDegrees(asin(worldDir.y.coerceIn(-1.0, 1.0))).toFloat(),
    )
}

/**
 * Transforms a vector from Earth-fixed to world coordinates using the same inverse orientation
 * applied by the renderer when mapping world positions to texture coordinates.
 */
private fun earthToWorld(
    earthDir: Vec3d,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): Vec3d {
    val yawRad = Math.toRadians(-earthRotationDegrees.toDouble())
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = earthDir.x * cosYaw + earthDir.z * sinYaw
    val z1 = -earthDir.x * sinYaw + earthDir.z * cosYaw
    val y1 = earthDir.y

    val tiltRad = Math.toRadians(-earthTiltDegrees.toDouble())
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val x2 = x1 * cosTilt - y1 * sinTilt
    val y2 = x1 * sinTilt + y1 * cosTilt

    return Vec3d(x2, y2, z1)
}
