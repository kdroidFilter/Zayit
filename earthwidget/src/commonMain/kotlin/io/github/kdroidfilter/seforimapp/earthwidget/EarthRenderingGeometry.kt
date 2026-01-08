package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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

    val dir = combineHorizontalDirection(
        sinAz = sinAz,
        cosAz = cosAz,
        cosEl = cosEl,
        sinEl = sinEl,
        eastX = cosLon,
        eastZ = eastZ,
        northX = northX,
        northZ = northZ,
        upX = upX,
        upZ = upZ,
        cosLat = cosLat,
        sinLat = sinLat,
    )

    val earthDir = dir.normalized()

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

internal fun combineHorizontalDirection(
    sinAz: Double,
    cosAz: Double,
    cosEl: Double,
    sinEl: Double,
    eastX: Double,
    eastZ: Double,
    northX: Double,
    northZ: Double,
    upX: Double,
    upZ: Double,
    cosLat: Double,
    sinLat: Double,
): Vec3d {
    val dirX = (eastX * sinAz + northX * cosAz) * cosEl + upX * sinEl
    val dirY = (cosLat * cosAz) * cosEl + sinLat * sinEl
    val dirZ = (eastZ * sinAz + northZ * cosAz) * cosEl + upZ * sinEl
    return Vec3d(dirX, dirY, dirZ)
}

internal fun computeSceneGeometry(
    outputSizePx: Int,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
): SceneGeometry {
    val sceneHalf = outputSizePx / 2f
    val cameraZ = outputSizePx * CAMERA_DISTANCE_FACTOR

    val earthSizePx = (outputSizePx * earthSizeFraction).roundToInt().coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val earthRadiusPx = (earthSizePx - 1) / 2f
    val earthLeft = (sceneHalf - earthSizePx / 2f).roundToInt()
    val earthTop = (sceneHalf - earthSizePx / 2f).roundToInt()

    val moonBaseSizePx = (earthSizePx * MOON_TO_EARTH_DIAMETER_RATIO).roundToInt()
        .coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val moonRadiusWorldPx = (moonBaseSizePx - 1) / 2f
    val edgeMarginPx = max(6f, outputSizePx * 0.02f)
    val orbitRadius = (sceneHalf - moonRadiusWorldPx - edgeMarginPx).coerceAtLeast(0f)

    val desiredSeparation = earthRadiusPx + moonRadiusWorldPx + 1.5f
    val viewPitchRad = if (orbitRadius > EPSILON) {
        asin((desiredSeparation / orbitRadius).coerceIn(0f, 0.999f))
    } else {
        0f
    }

    val orbitInclinationRad = MOON_ORBIT_INCLINATION_DEG * DEG_TO_RAD_F
    val cosInc = cos(orbitInclinationRad)
    val sinInc = sin(orbitInclinationRad)
    val cosView = cos(viewPitchRad)
    val sinView = sin(viewPitchRad)

    return SceneGeometry(
        outputSizePx = outputSizePx,
        sceneHalf = sceneHalf,
        cameraZ = cameraZ,
        earthSizePx = earthSizePx,
        earthRadiusPx = earthRadiusPx,
        earthLeft = earthLeft,
        earthTop = earthTop,
        moonBaseSizePx = moonBaseSizePx,
        moonRadiusWorldPx = moonRadiusWorldPx,
        orbitRadius = orbitRadius,
        viewPitchRad = viewPitchRad,
        cosInc = cosInc,
        sinInc = sinInc,
        cosView = cosView,
        sinView = sinView,
    )
}

internal fun computeMoonScreenLayout(
    geometry: SceneGeometry,
    moonOrbitDegrees: Float,
): MoonScreenLayout {
    val moonOrbit = transformMoonOrbitPosition(moonOrbitDegrees, geometry.orbitRadius, geometry.viewPitchRad)
    val moonScale = perspectiveScale(geometry.cameraZ, moonOrbit.zCam)
    val moonSizePx = (geometry.moonBaseSizePx * moonScale).roundToInt().coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val moonRadiusPx = (moonSizePx - 1) / 2f
    val moonCenterX = geometry.sceneHalf + moonOrbit.x * moonScale
    val moonCenterY = geometry.sceneHalf - moonOrbit.yCam * moonScale
    val moonLeft = (moonCenterX - moonRadiusPx).roundToInt()
    val moonTop = (moonCenterY - moonRadiusPx).roundToInt()

    return MoonScreenLayout(
        moonOrbit = moonOrbit,
        moonScale = moonScale,
        moonSizePx = moonSizePx,
        moonRadiusPx = moonRadiusPx,
        moonCenterX = moonCenterX,
        moonCenterY = moonCenterY,
        moonLeft = moonLeft,
        moonTop = moonTop,
    )
}

internal fun latLonToUnitVector(
    latitudeDegrees: Float,
    longitudeDegrees: Float,
): Vec3f {
    val latRad = latitudeDegrees.coerceIn(-90f, 90f) * DEG_TO_RAD_F
    val lonRad = longitudeDegrees.coerceIn(-180f, 180f) * DEG_TO_RAD_F
    val cosLat = cos(latRad)
    return Vec3f(
        x = sin(lonRad) * cosLat,
        y = sin(latRad),
        z = cos(lonRad) * cosLat,
    )
}
