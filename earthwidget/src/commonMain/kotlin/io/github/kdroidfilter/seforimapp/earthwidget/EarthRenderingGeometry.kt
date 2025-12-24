package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Converts azimuth and elevation angles to a direction vector.
 */
internal fun sunVectorFromAngles(lightDegrees: Float, sunElevationDegrees: Float): Vec3f {
    val az = lightDegrees * DEG_TO_RAD_F
    val el = sunElevationDegrees * DEG_TO_RAD_F
    val cosEl = cos(el)
    return Vec3f(
        x = sin(az) * cosEl,
        y = sin(el),
        z = cos(az) * cosEl,
    )
}

/**
 * Transforms a local horizontal vector (azimuth from North, elevation) into world coordinates.
 */
internal fun horizontalToWorld(
    latitudeDeg: Double,
    longitudeDeg: Double,
    azimuthFromNorthDeg: Double,
    elevationDeg: Double,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
): Vec3f {
    val latRad = latitudeDeg * DEG_TO_RAD
    val lonRad = longitudeDeg * DEG_TO_RAD
    val azRad = azimuthFromNorthDeg * DEG_TO_RAD
    val elRad = elevationDeg * DEG_TO_RAD

    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val sinLon = sin(lonRad)
    val cosLon = cos(lonRad)

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

    val dirX = (eastX * sinAz + northX * cosAz) * cosEl + upX * sinEl
    val dirY = (eastY * sinAz + cosLat * cosAz) * cosEl + sinLat * sinEl
    val dirZ = (eastZ * sinAz + northZ * cosAz) * cosEl + upZ * sinEl

    val earthDir = Vec3d(dirX, dirY, dirZ).normalized()

    val yawRad = (-earthRotationDegrees) * DEG_TO_RAD
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = earthDir.x * cosYaw + earthDir.z * sinYaw
    val z1 = -earthDir.x * sinYaw + earthDir.z * cosYaw
    val y1 = earthDir.y

    val tiltRad = (-earthTiltDegrees) * DEG_TO_RAD
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val x2 = x1 * cosTilt - y1 * sinTilt
    val y2 = x1 * sinTilt + y1 * cosTilt

    return Vec3f(x = x2.toFloat(), y = y2.toFloat(), z = z1.toFloat()).normalized()
}

/**
 * Transforms moon orbit position from orbital plane to camera space.
 */
internal fun transformMoonOrbitPosition(
    moonOrbitDegrees: Float,
    orbitRadius: Float,
    viewPitchRad: Float,
): MoonOrbitPosition {
    val orbitInclinationRad = MOON_ORBIT_INCLINATION_DEG * DEG_TO_RAD_F
    val cosInc = cos(orbitInclinationRad)
    val sinInc = sin(orbitInclinationRad)
    val cosView = cos(viewPitchRad)
    val sinView = sin(viewPitchRad)

    val angle = moonOrbitDegrees * DEG_TO_RAD_F
    val x0 = cos(angle) * orbitRadius
    val z0 = sin(angle) * orbitRadius

    val yInc = -z0 * sinInc
    val zInc = z0 * cosInc

    val yCam = yInc * cosView - zInc * sinView
    val zCam = yInc * sinView + zInc * cosView

    return MoonOrbitPosition(x = x0, yCam = yCam, zCam = zCam)
}

/**
 * Calculates perspective scale factor based on depth.
 */
internal fun perspectiveScale(cameraZ: Float, z: Float): Float {
    val denom = max(1f, cameraZ - z)
    return cameraZ / denom
}
