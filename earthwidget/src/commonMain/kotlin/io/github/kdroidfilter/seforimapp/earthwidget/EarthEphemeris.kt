package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// ============================================================================
// EPHEMERIS CALCULATIONS
// ============================================================================

private fun meanObliquityRad(julianDay: Double): Double {
    val T = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val seconds =
        MEAN_OBLIQUITY_COEFF0 -
            T * (MEAN_OBLIQUITY_COEFF1 + T * (MEAN_OBLIQUITY_COEFF2 - MEAN_OBLIQUITY_COEFF3 * T))
    val degrees = 23.0 + (26.0 + seconds / 60.0) / 60.0
    return degrees * DEG_TO_RAD
}

/**
 * Computes the Moon's ecliptic position using the Meeus algorithm.
 */
internal fun computeMoonEclipticPosition(julianDay: Double): EclipticPosition {
    val T = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY

    val Lp = normalizeAngleDeg(MOON_MEAN_LONGITUDE_J2000 + MOON_MEAN_LONGITUDE_RATE * T)
    val Mp = normalizeAngleDeg(MOON_MEAN_ANOMALY_J2000 + MOON_MEAN_ANOMALY_RATE * T)
    val D = normalizeAngleDeg(MOON_MEAN_ELONGATION_J2000 + MOON_MEAN_ELONGATION_RATE * T)
    val Ms = normalizeAngleDeg(SUN_MEAN_ANOMALY_J2000 + SUN_MEAN_ANOMALY_RATE * T)
    val F = normalizeAngleDeg(MOON_ARG_LATITUDE_J2000 + MOON_ARG_LATITUDE_RATE * T)

    val MpRad = Mp * DEG_TO_RAD
    val DRad = D * DEG_TO_RAD
    val MsRad = Ms * DEG_TO_RAD
    val FRad = F * DEG_TO_RAD

    val dL =
        6.289 * sin(MpRad) +
            1.274 * sin(2.0 * DRad - MpRad) +
            0.658 * sin(2.0 * DRad) +
            0.214 * sin(2.0 * MpRad) -
            0.186 * sin(MsRad) -
            0.114 * sin(2.0 * FRad)

    val dB =
        5.128 * sin(FRad) +
            0.281 * sin(MpRad + FRad) +
            0.278 * sin(MpRad - FRad)

    return EclipticPosition(
        longitude = normalizeAngleDeg(Lp + dL).toFloat(),
        latitude = dB.toFloat(),
    )
}

/**
 * Moon equatorial coordinates (right ascension/declination).
 */
internal fun computeMoonEquatorialPosition(julianDay: Double): EquatorialPosition {
    val ecliptic = computeMoonEclipticPosition(julianDay)
    val lonRad = ecliptic.longitude * DEG_TO_RAD
    val latRad = ecliptic.latitude * DEG_TO_RAD
    val obliquityRad = meanObliquityRad(julianDay)

    val sinLon = sin(lonRad)
    val cosLon = cos(lonRad)
    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinObliq = sin(obliquityRad)
    val cosObliq = cos(obliquityRad)

    val decRad = asin((sinLat * cosObliq + cosLat * sinObliq * sinLon).coerceIn(-1.0, 1.0))
    val y = sinLon * cosObliq - tan(latRad) * sinObliq
    val x = cosLon
    var raRad = atan2(y, x)
    if (raRad < 0) raRad += 2 * PI

    return EquatorialPosition(
        rightAscensionRad = raRad,
        declinationRad = decRad,
    )
}

internal fun greenwichMeanSiderealTimeRad(julianDay: Double): Double {
    val T = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val gmstDeg =
        280.46061837 +
            360.98564736629 * (julianDay - J2000_EPOCH_JD) +
            T * T * (0.000387933 - T / 38710000.0)
    return normalizeRad(gmstDeg * DEG_TO_RAD)
}

internal fun localSiderealTimeRad(
    julianDay: Double,
    longitudeDeg: Double,
): Double = normalizeRad(greenwichMeanSiderealTimeRad(julianDay) + longitudeDeg * DEG_TO_RAD)

/**
 * Computes Moon azimuth/elevation for the observer.
 */
internal fun computeMoonHorizontalPosition(
    julianDay: Double,
    latitudeDeg: Double,
    longitudeDeg: Double,
): HorizontalPosition {
    val eq = computeMoonEquatorialPosition(julianDay)
    val latRad = latitudeDeg * DEG_TO_RAD
    val lstRad = localSiderealTimeRad(julianDay, longitudeDeg)
    val hourAngleRad = normalizeRad(lstRad - eq.rightAscensionRad)

    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinDec = sin(eq.declinationRad)
    val cosDec = cos(eq.declinationRad)
    val sinH = sin(hourAngleRad)
    val cosH = cos(hourAngleRad)

    val elevationRad = asin((sinDec * sinLat + cosDec * cosLat * cosH).coerceIn(-1.0, 1.0))
    val azimuthRad =
        atan2(
            -sinH,
            tan(eq.declinationRad) * cosLat - sinLat * cosH,
        )

    return HorizontalPosition(
        azimuthFromNorthDeg = normalizeAngleDeg(Math.toDegrees(azimuthRad)),
        elevationDeg = Math.toDegrees(elevationRad),
    )
}

/**
 * Computes the Sun's ecliptic longitude using a simplified algorithm.
 */
internal fun computeSunEclipticLongitude(julianDay: Double): Float {
    val T = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val L0 = normalizeAngleDeg(SUN_MEAN_LONGITUDE_J2000 + SUN_MEAN_LONGITUDE_RATE * T)
    val M = normalizeAngleDeg(SUN_MEAN_ANOMALY_J2000 + SUN_MEAN_ANOMALY_RATE * T)
    val Mrad = M * DEG_TO_RAD

    val C = 1.9146 * sin(Mrad) + 0.02 * sin(2.0 * Mrad)

    return normalizeAngleDeg(L0 + C).toFloat()
}

/**
 * Computes the geometric Moon illumination direction from ephemeris data.
 */
internal fun computeGeometricMoonIllumination(
    julianDay: Double,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
): LightDirection {
    val moonPos = computeMoonEclipticPosition(julianDay)
    val sunLong = computeSunEclipticLongitude(julianDay)
    val elongation = normalizeAngleDeg((moonPos.longitude - sunLong).toDouble())

    val phaseAngleDegrees = elongation.toFloat()

    return computeMoonLightFromPhaseInternal(
        phaseAngleDegrees = phaseAngleDegrees,
        viewDirX = viewDirX,
        viewDirY = viewDirY,
        viewDirZ = viewDirZ,
    )
}

/**
 * Internal implementation of phase-based Moon lighting.
 */
internal fun computeMoonLightFromPhaseInternal(
    phaseAngleDegrees: Float,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
): LightDirection {
    val viewDir = Vec3f(viewDirX, viewDirY, viewDirZ).normalized()
    val normalizedPhase = ((phaseAngleDegrees % 360f) + 360f) % 360f
    val thetaDegrees = 180f - normalizedPhase
    val thetaRad = Math.toRadians(thetaDegrees.toDouble())

    var right = cross(Vec3f.WORLD_UP, viewDir)
    if (right.length() <= EPSILON) {
        right = Vec3f(1f, 0f, 0f)
    }
    right = right.normalized()

    val cosT = cos(thetaRad).toFloat()
    val sinT = sin(thetaRad).toFloat()

    val up = cross(viewDir, right).normalized()
    val sunDir = rotateTowards(viewDir, up, cosT, sinT)

    return LightDirection(
        lightDegrees = Math.toDegrees(atan2(sunDir.x.toDouble(), sunDir.z.toDouble())).toFloat(),
        sunElevationDegrees = Math.toDegrees(asin(sunDir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat(),
    )
}

/**
 * Computes Moon lighting using the observer's up direction as reference.
 */
internal fun computeMoonLightFromPhaseWithObserverUp(
    phaseAngleDegrees: Float,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
    observerUpX: Float,
    observerUpY: Float,
    observerUpZ: Float,
    sunDirectionHint: Vec3f? = null,
): LightDirection {
    val viewDir = Vec3f(viewDirX, viewDirY, viewDirZ).normalized()
    val observerUp = Vec3f(observerUpX, observerUpY, observerUpZ).normalized()
    val sunHint = sunDirectionHint?.normalized()

    val normalizedPhase = ((phaseAngleDegrees % 360f) + 360f) % 360f
    val thetaDegrees = 180f - normalizedPhase
    val thetaRad = Math.toRadians(thetaDegrees.toDouble())

    val baseUp =
        projectOntoViewPlane(viewDir, observerUp)
            ?: projectOntoViewPlane(viewDir, Vec3f.WORLD_UP)
            ?: run {
                val fallbackRight = if (abs(viewDir.x) < 0.9f) Vec3f(1f, 0f, 0f) else Vec3f(0f, 0f, 1f)
                cross(viewDir, fallbackRight).normalized()
            }
    val baseRight = cross(viewDir, baseUp).normalized()

    val sunAngle: Float =
        if (sunHint != null) {
            val sunProj = projectOntoViewPlane(viewDir, sunHint)
            if (sunProj != null) {
                val sunUpComponent = dot(sunProj, baseUp)
                val sunRightComponent = dot(sunProj, baseRight)
                atan2(sunRightComponent, sunUpComponent)
            } else {
                0f
            }
        } else {
            0f
        }

    val cosAngle = cos(sunAngle)
    val sinAngle = sin(sunAngle)
    val planeUp =
        Vec3f(
            baseUp.x * cosAngle + baseRight.x * sinAngle,
            baseUp.y * cosAngle + baseRight.y * sinAngle,
            baseUp.z * cosAngle + baseRight.z * sinAngle,
        ).normalized()

    val cosT = cos(thetaRad).toFloat()
    val sinT = sin(thetaRad).toFloat()

    val sunDir = rotateTowards(viewDir, planeUp, cosT, sinT)

    return LightDirection(
        lightDegrees = Math.toDegrees(atan2(sunDir.x.toDouble(), sunDir.z.toDouble())).toFloat(),
        sunElevationDegrees = Math.toDegrees(asin(sunDir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat(),
    )
}
