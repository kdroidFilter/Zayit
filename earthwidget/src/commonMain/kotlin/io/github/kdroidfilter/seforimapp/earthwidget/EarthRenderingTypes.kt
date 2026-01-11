package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.sqrt

// ============================================================================
// DATA CLASSES
// ============================================================================

/**
 * Holds texture data for sphere rendering.
 *
 * @property argb Pixel data in ARGB format.
 * @property width Texture width in pixels.
 * @property height Texture height in pixels.
 */
internal data class EarthTexture(
    val argb: IntArray,
    val width: Int,
    val height: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EarthTexture) return false
        return width == other.width && height == other.height && argb.contentEquals(other.argb)
    }

    override fun hashCode(): Int {
        var result = argb.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * Represents light direction for illumination calculations.
 *
 * @property lightDegrees Horizontal angle of light source (azimuth).
 * @property sunElevationDegrees Vertical angle of light source (elevation).
 */
data class LightDirection(
    val lightDegrees: Float,
    val sunElevationDegrees: Float,
)

/**
 * Moon position in ecliptic coordinates.
 */
internal data class EclipticPosition(
    val longitude: Float,
    val latitude: Float,
)

internal data class EquatorialPosition(
    val rightAscensionRad: Double,
    val declinationRad: Double,
)

internal data class HorizontalPosition(
    val azimuthFromNorthDeg: Double,
    val elevationDeg: Double,
)

/**
 * Moon position in camera space after orbital transformations.
 *
 * @property x Horizontal position (positive = right).
 * @property yCam Vertical position (positive = up).
 * @property zCam Depth position (positive = towards viewer).
 */
internal data class MoonOrbitPosition(
    val x: Float,
    val yCam: Float,
    val zCam: Float,
)

internal data class SceneGeometry(
    val outputSizePx: Int,
    val sceneHalf: Float,
    val cameraZ: Float,
    val earthSizePx: Int,
    val earthRadiusPx: Float,
    val earthLeft: Int,
    val earthTop: Int,
    val moonBaseSizePx: Int,
    val moonRadiusWorldPx: Float,
    val orbitRadius: Float,
    val viewPitchRad: Float,
    val cosInc: Float,
    val sinInc: Float,
    val cosView: Float,
    val sinView: Float,
)

internal data class MoonScreenLayout(
    val moonOrbit: MoonOrbitPosition,
    val moonScale: Float,
    val moonSizePx: Int,
    val moonRadiusPx: Float,
    val moonCenterX: Float,
    val moonCenterY: Float,
    val moonLeft: Int,
    val moonTop: Int,
)

/**
 * Simple 3D vector with double precision for geocentric calculations.
 */
internal data class Vec3d(val x: Double, val y: Double, val z: Double) {
    fun normalized(): Vec3d {
        val len = sqrt(x * x + y * y + z * z)
        if (len <= 1e-12) return this
        val inv = 1.0 / len
        return Vec3d(x * inv, y * inv, z * inv)
    }

    fun toVec3f(): Vec3f = Vec3f(x.toFloat(), y.toFloat(), z.toFloat())
}

/**
 * 3D vector with float precision for graphics calculations.
 */
internal data class Vec3f(val x: Float, val y: Float, val z: Float) {
    /** Computes the Euclidean length of this vector. */
    fun length(): Float = sqrt(x * x + y * y + z * z)

    /** Returns a unit vector in the same direction, or this vector if length is near zero. */
    fun normalized(): Vec3f {
        val len = length()
        if (len <= EPSILON_F) return this
        val inv = 1f / len
        return Vec3f(x * inv, y * inv, z * inv)
    }

    companion object {
        /** Small value for floating-point comparisons. */
        private const val EPSILON_F = 1e-6f

        /** World up vector (Y-axis). */
        val WORLD_UP = Vec3f(0f, 1f, 0f)

        /** Forward vector (Z-axis). */
        val FORWARD = Vec3f(0f, 0f, 1f)
    }
}
