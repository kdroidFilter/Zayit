package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import io.github.erkko68.filament.compose.scene.Direction
import io.github.erkko68.filament.compose.scene.Rotation
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * The sky as seen from the ground. World frame: +x east, +y up, -z north; the camera sits at the origin, and the
 * sky view draws everything (air, stars, Moon, Sun, land) with the projection below, all in the same frame.
 */

/** Vertical field of view. */
internal const val SKY_VFOV_DEG = 72f

/**
 * Both discs at their true angular radii (mean), magnified by the same factor: seen from the ground they stay
 * the same size as each other, as in the real sky, yet a crescent still reads on a small card.
 */
internal const val BODY_MAGNIFICATION = 18f
internal const val SUN_ANGULAR_RADIUS_DEG = 0.2666f * BODY_MAGNIFICATION
internal const val MOON_ANGULAR_RADIUS_DEG = 0.2590f * BODY_MAGNIFICATION

/** Unit direction for an azimuth (from north, clockwise) and an elevation, in degrees. */
internal fun skyDirection(
    azimuthDeg: Double,
    elevationDeg: Double,
): Direction {
    val az = azimuthDeg * DEG_TO_RAD
    val el = elevationDeg * DEG_TO_RAD
    return Direction((sin(az) * cos(el)).toFloat(), sin(el).toFloat(), (-cos(az) * cos(el)).toFloat())
}

@Immutable
internal data class SkyViewState(
    val widthPx: Int,
    val heightPx: Int,
    /** Where the viewer looks: azimuth from north, and height above the horizon. */
    val yawDeg: Float,
    val pitchDeg: Float,
    val sun: Direction,
    val moon: Direction,
    /** Equatorial frame → sky: turns the star field (and the Milky Way) with the sidereal time. */
    val stars: Rotation,
    /** Vertical field of view (zoom). */
    val fovDeg: Float = SKY_VFOV_DEG,
)

/** The viewer's camera: its basis and its perspective projection, shared by every layer of the sky. */
internal class SkyCamera(
    yawDeg: Float,
    pitchDeg: Float,
    widthPx: Int,
    heightPx: Int,
    fovDeg: Float = SKY_VFOV_DEG,
) {
    constructor(state: SkyViewState) : this(state.yawDeg, state.pitchDeg, state.widthPx, state.heightPx, state.fovDeg)

    val forward = skyDirection(yawDeg.toDouble(), pitchDeg.toDouble())
    val right: Direction
    val up: Direction
    val tanHalfV = tan(fovDeg / 2f * DEG_TO_RAD_F)
    val aspect = widthPx.toFloat() / heightPx.coerceAtLeast(1)
    private val w = widthPx.toFloat()
    private val h = heightPx.toFloat()

    init {
        val yaw = yawDeg * DEG_TO_RAD_F
        right = Direction(cos(yaw), 0f, sin(yaw))
        up = cross(right, forward)
    }

    /** Screen px of direction [d], or null behind the viewer. */
    fun project(d: Direction): Offset? {
        val z = dot(d, forward)
        if (z <= 0.01f) return null
        val x = dot(d, right) / z / (aspect * tanHalfV)
        val y = dot(d, up) / z / tanHalfV
        return Offset((x + 1f) / 2f * w, (1f - y) / 2f * h)
    }
}

private fun dot(
    a: Direction,
    b: Direction,
) = a.x * b.x + a.y * b.y + a.z * b.z

private fun cross(
    a: Direction,
    b: Direction,
) = Direction(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

/**
 * Rotation taking equatorial coordinates (x toward RA 0h, z to the celestial pole) to the sky at local
 * sidereal time [lstRad] and latitude [latitudeDeg].
 */
internal fun equatorialToSky(
    lstRad: Double,
    latitudeDeg: Double,
): Rotation {
    val phi = latitudeDeg * DEG_TO_RAD
    // Where the equator crosses the meridian, due east, and the pole
    val meridian = doubleArrayOf(0.0, cos(phi), sin(phi))
    val east = doubleArrayOf(1.0, 0.0, 0.0)
    val pole = doubleArrayOf(0.0, sin(phi), -cos(phi))
    val c = cos(lstRad)
    val s = sin(lstRad)
    val c0 = DoubleArray(3) { c * meridian[it] - s * east[it] }
    val c1 = DoubleArray(3) { s * meridian[it] + c * east[it] }
    return rotationFromColumns(c0, c1, pole)
}

private fun rotationFromColumns(
    c0: DoubleArray,
    c1: DoubleArray,
    c2: DoubleArray,
): Rotation {
    val m00 = c0[0]
    val m10 = c0[1]
    val m20 = c0[2]
    val m01 = c1[0]
    val m11 = c1[1]
    val m21 = c1[2]
    val m02 = c2[0]
    val m12 = c2[1]
    val m22 = c2[2]
    val trace = m00 + m11 + m22
    val q =
        when {
            trace > 0 -> {
                val s = sqrt(trace + 1.0) * 2.0
                doubleArrayOf((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, s / 4.0)
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1.0 + m00 - m11 - m22) * 2.0
                doubleArrayOf(s / 4.0, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            }
            m11 > m22 -> {
                val s = sqrt(1.0 + m11 - m00 - m22) * 2.0
                doubleArrayOf((m01 + m10) / s, s / 4.0, (m12 + m21) / s, (m02 - m20) / s)
            }
            else -> {
                val s = sqrt(1.0 + m22 - m00 - m11) * 2.0
                doubleArrayOf((m02 + m20) / s, (m12 + m21) / s, s / 4.0, (m10 - m01) / s)
            }
        }
    return Rotation(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat())
}
