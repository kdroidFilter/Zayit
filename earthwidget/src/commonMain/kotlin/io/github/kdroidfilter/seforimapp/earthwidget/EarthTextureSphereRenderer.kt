package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// MATHEMATICAL CONSTANTS
// ============================================================================

/** Degrees to radians conversion factor (Double precision). */
private const val DEG_TO_RAD = PI / 180.0

/** Degrees to radians conversion factor (Float precision). */
private const val DEG_TO_RAD_F = (PI / 180.0).toFloat()

/** Two times PI as Float for orbit calculations. */
private const val TWO_PI_F = (2.0 * PI).toFloat()

// ============================================================================
// ASTRONOMICAL CONSTANTS
// ============================================================================

/** Moon diameter relative to Earth diameter (actual ratio ~0.2727). */
private const val MOON_TO_EARTH_DIAMETER_RATIO = 0.2724f

/** Moon orbital inclination relative to ecliptic plane in degrees. */
private const val MOON_ORBIT_INCLINATION_DEG = 5.145f

// ============================================================================
// J2000.0 EPOCH CONSTANTS (Meeus Algorithm)
// ============================================================================

/** Moon mean longitude at J2000.0 epoch (degrees). */
private const val MOON_MEAN_LONGITUDE_J2000 = 218.3164477

/** Moon mean longitude rate (degrees per Julian century). */
private const val MOON_MEAN_LONGITUDE_RATE = 481267.88123421

/** Moon mean anomaly at J2000.0 epoch (degrees). */
private const val MOON_MEAN_ANOMALY_J2000 = 134.9633964

/** Moon mean anomaly rate (degrees per Julian century). */
private const val MOON_MEAN_ANOMALY_RATE = 477198.8675055

/** Moon mean elongation at J2000.0 epoch (degrees). */
private const val MOON_MEAN_ELONGATION_J2000 = 297.8501921

/** Moon mean elongation rate (degrees per Julian century). */
private const val MOON_MEAN_ELONGATION_RATE = 445267.1114034

/** Sun mean anomaly at J2000.0 epoch (degrees). */
private const val SUN_MEAN_ANOMALY_J2000 = 357.5291092

/** Sun mean anomaly rate (degrees per Julian century). */
private const val SUN_MEAN_ANOMALY_RATE = 35999.0502909

/** Moon argument of latitude at J2000.0 epoch (degrees). */
private const val MOON_ARG_LATITUDE_J2000 = 93.2720950

/** Moon argument of latitude rate (degrees per Julian century). */
private const val MOON_ARG_LATITUDE_RATE = 483202.0175233

/** Sun mean longitude at J2000.0 epoch (degrees). */
private const val SUN_MEAN_LONGITUDE_J2000 = 280.4665

/** Sun mean longitude rate (degrees per Julian century). */
private const val SUN_MEAN_LONGITUDE_RATE = 36000.7698

/** J2000.0 epoch Julian Day number. */
private const val J2000_EPOCH_JD = 2451545.0

/** Days per Julian century. */
private const val DAYS_PER_JULIAN_CENTURY = 36525.0

// ============================================================================
// RENDERING CONSTANTS
// ============================================================================

/** Camera distance factor for perspective projection. */
private const val CAMERA_DISTANCE_FACTOR = 1.6f

/** Seed for deterministic starfield generation. */
private const val STARFIELD_SEED = 0x6D2B79F5

/** Edge feathering width for smooth sphere edges (fraction of radius). */
private const val EDGE_FEATHER_WIDTH = 0.012f

/** Minimum star count in starfield. */
private const val MIN_STAR_COUNT = 90

/** Maximum star count in starfield. */
private const val MAX_STAR_COUNT = 2200

/** Pixels per star (divisor for calculating star count). */
private const val PIXELS_PER_STAR = 700

// ============================================================================
// LIGHTING CONSTANTS
// ============================================================================

/** Default ambient light intensity. */
private const val DEFAULT_AMBIENT = 0.18f

/** Default diffuse light strength. */
private const val DEFAULT_DIFFUSE_STRENGTH = 0.92f

/** Default atmosphere rim glow strength. */
private const val DEFAULT_ATMOSPHERE_STRENGTH = 0.22f

/** Earth specular highlight strength. */
private const val EARTH_SPECULAR_STRENGTH = 0.18f

/** Earth specular exponent (shininess). */
private const val EARTH_SPECULAR_EXPONENT = 128

/** Moon ambient light (darker than Earth). */
private const val MOON_AMBIENT = 0.04f

/** Moon diffuse light strength. */
private const val MOON_DIFFUSE_STRENGTH = 0.96f

/** Shadow transition start (cosine of angle). */
private const val SHADOW_EDGE_START = -0.15f

/** Shadow transition end (cosine of angle). */
private const val SHADOW_EDGE_END = 0.1f

/** Earth umbra radius as fraction of Earth-Moon distance (real-world ~0.72 / 60). */
private const val EARTH_UMBRA_DISTANCE_RATIO = 0.01197f

/** Earth penumbra radius as fraction of Earth-Moon distance (real-world ~1.28 / 60). */
private const val EARTH_PENUMBRA_DISTANCE_RATIO = 0.02119f

// ============================================================================
// VISUAL CONSTANTS
// ============================================================================

/** Earth visual size as fraction of output. */
private const val EARTH_SIZE_FRACTION = 0.40f

/** Minimum sphere size in pixels. */
private const val MIN_SPHERE_SIZE_PX = 8

/** Orbit line alpha when in front of Earth. */
private const val ORBIT_ALPHA_FRONT = 0xC8

/** Orbit line alpha when behind Earth. */
private const val ORBIT_ALPHA_BACK = 0x6C

/** Orbit glow intensity multiplier. */
private const val ORBIT_GLOW_INTENSITY = 0.42f

/** Marker radius as fraction of sphere size. */
private const val MARKER_RADIUS_FRACTION = 0.017f

/** Minimum marker radius in pixels. */
private const val MIN_MARKER_RADIUS_PX = 2f

/** Marker outline additional radius. */
private const val MARKER_OUTLINE_EXTRA_PX = 1.6f

/** Marker fill color (Material Red 600). */
private const val MARKER_FILL_COLOR = 0xFFE53935.toInt()

/** Marker outline color (White). */
private const val MARKER_OUTLINE_COLOR = 0xFFFFFFFF.toInt()

/** Transparent black color. */
private const val TRANSPARENT_BLACK = 0x00000000

/** Opaque black color. */
private const val OPAQUE_BLACK = 0xFF000000.toInt()

/** Orbit line base color (white without alpha). */
private const val ORBIT_COLOR_RGB = 0x00FFFFFF

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
 *
 * @property longitude Ecliptic longitude in degrees.
 * @property latitude Ecliptic latitude in degrees.
 */
private data class EclipticPosition(
    val longitude: Float,
    val latitude: Float,
)

/**
 * Moon position in camera space after orbital transformations.
 *
 * @property x Horizontal position (positive = right).
 * @property yCam Vertical position (positive = up).
 * @property zCam Depth position (positive = towards viewer).
 */
private data class MoonOrbitPosition(
    val x: Float,
    val yCam: Float,
    val zCam: Float,
)

/**
 * 3D vector with float precision for graphics calculations.
 */
private data class Vec3f(val x: Float, val y: Float, val z: Float) {
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

// ============================================================================
// VECTOR OPERATIONS
// ============================================================================

/** Computes the cross product of two vectors. */
private fun cross(a: Vec3f, b: Vec3f): Vec3f = Vec3f(
    x = a.y * b.z - a.z * b.y,
    y = a.z * b.x - a.x * b.z,
    z = a.x * b.y - a.y * b.x,
)

/** Computes the dot product of two vectors. */
private fun dot(a: Vec3f, b: Vec3f): Float = a.x * b.x + a.y * b.y + a.z * b.z

/** Small value for floating-point comparisons. */
private const val EPSILON = 1e-6f

// ============================================================================
// ANGLE UTILITIES
// ============================================================================

/**
 * Normalizes an angle to the range [0, 360).
 *
 * @param degrees Input angle in degrees.
 * @return Normalized angle in [0, 360) range.
 */
private fun normalizeAngleDeg(degrees: Double): Double {
    val result = degrees % 360.0
    return if (result < 0) result + 360.0 else result
}

// ============================================================================
// EPHEMERIS CALCULATIONS
// ============================================================================

/**
 * Computes the Moon's ecliptic position using the Meeus algorithm.
 *
 * This implementation uses the principal terms from Jean Meeus'
 * "Astronomical Algorithms" for computing lunar position with
 * accuracy sufficient for visual display (~0.3° maximum error).
 *
 * @param julianDay Julian Day number for the calculation.
 * @return Moon's ecliptic longitude and latitude.
 */
private fun computeMoonEclipticPosition(julianDay: Double): EclipticPosition {
    // Julian centuries since J2000.0
    val T = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY

    // Mean orbital elements
    val Lp = normalizeAngleDeg(MOON_MEAN_LONGITUDE_J2000 + MOON_MEAN_LONGITUDE_RATE * T)
    val Mp = normalizeAngleDeg(MOON_MEAN_ANOMALY_J2000 + MOON_MEAN_ANOMALY_RATE * T)
    val D = normalizeAngleDeg(MOON_MEAN_ELONGATION_J2000 + MOON_MEAN_ELONGATION_RATE * T)
    val Ms = normalizeAngleDeg(SUN_MEAN_ANOMALY_J2000 + SUN_MEAN_ANOMALY_RATE * T)
    val F = normalizeAngleDeg(MOON_ARG_LATITUDE_J2000 + MOON_ARG_LATITUDE_RATE * T)

    // Convert to radians for trigonometric functions
    val MpRad = Mp * DEG_TO_RAD
    val DRad = D * DEG_TO_RAD
    val MsRad = Ms * DEG_TO_RAD
    val FRad = F * DEG_TO_RAD

    // Principal longitude correction terms (degrees)
    // These are the dominant periodic perturbations
    val dL = 6.289 * sin(MpRad) +                    // Equation of center
            1.274 * sin(2.0 * DRad - MpRad) +       // Evection
            0.658 * sin(2.0 * DRad) +               // Variation
            0.214 * sin(2.0 * MpRad) -              // Second equation of center
            0.186 * sin(MsRad) -                    // Annual equation
            0.114 * sin(2.0 * FRad)                 // Reduction to ecliptic

    // Principal latitude correction terms (degrees)
    val dB = 5.128 * sin(FRad) +
            0.281 * sin(MpRad + FRad) +
            0.278 * sin(MpRad - FRad)

    return EclipticPosition(
        longitude = normalizeAngleDeg(Lp + dL).toFloat(),
        latitude = dB.toFloat()
    )
}

/**
 * Computes the Sun's ecliptic longitude using a simplified algorithm.
 *
 * @param julianDay Julian Day number for the calculation.
 * @return Sun's ecliptic longitude in degrees.
 */
private fun computeSunEclipticLongitude(julianDay: Double): Float {
    val T = (julianDay - J2000_EPOCH_JD) / DAYS_PER_JULIAN_CENTURY
    val L0 = normalizeAngleDeg(SUN_MEAN_LONGITUDE_J2000 + SUN_MEAN_LONGITUDE_RATE * T)
    val M = normalizeAngleDeg(SUN_MEAN_ANOMALY_J2000 + SUN_MEAN_ANOMALY_RATE * T)
    val Mrad = M * DEG_TO_RAD

    // Equation of center (simplified)
    val C = 1.9146 * sin(Mrad) + 0.02 * sin(2.0 * Mrad)

    return normalizeAngleDeg(L0 + C).toFloat()
}

/**
 * Computes the geometric Moon illumination direction from ephemeris data.
 *
 * Calculates the phase angle (Sun-Moon elongation) from astronomical ephemeris
 * and uses it to determine how the Moon should be illuminated.
 *
 * The phase angle is the angular distance between Sun and Moon as seen from Earth:
 * - 0° = New Moon (Sun and Moon in same direction, Moon not illuminated)
 * - 180° = Full Moon (Sun and Moon opposite, Moon fully illuminated)
 *
 * @param julianDay Julian Day number.
 * @param viewDirX View direction X component.
 * @param viewDirY View direction Y component.
 * @param viewDirZ View direction Z component.
 * @return Light direction for Moon rendering.
 */
internal fun computeGeometricMoonIllumination(
    julianDay: Double,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
): LightDirection {
    val moonPos = computeMoonEclipticPosition(julianDay)
    val sunLong = computeSunEclipticLongitude(julianDay)

    // Calculate elongation (angular distance between Sun and Moon)
    // This gives us the phase angle directly
    val elongation = normalizeAngleDeg((moonPos.longitude - sunLong).toDouble())

    // Convert elongation to phase angle for rendering:
    // elongation 0° = new moon (phase 0°)
    // elongation 180° = full moon (phase 180°)
    val phaseAngleDegrees = elongation.toFloat()

    // Use the phase-based lighting calculation which correctly handles
    // the geometry of how sunlight illuminates the Moon's visible face
    return computeMoonLightFromPhaseInternal(
        phaseAngleDegrees = phaseAngleDegrees,
        viewDirX = viewDirX,
        viewDirY = viewDirY,
        viewDirZ = viewDirZ,
    )
}

/**
 * Internal implementation of phase-based Moon lighting.
 *
 * Computes the light direction that will produce the correct illumination
 * pattern on the Moon's visible face based on its phase.
 *
 * @param phaseAngleDegrees Moon phase (0 = new, 180 = full).
 * @param viewDirX View direction X.
 * @param viewDirY View direction Y.
 * @param viewDirZ View direction Z.
 * @return Light direction for rendering.
 */
private fun computeMoonLightFromPhaseInternal(
    phaseAngleDegrees: Float,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
): LightDirection {
    val viewDir = Vec3f(viewDirX, viewDirY, viewDirZ).normalized()

    // Normalize phase to [0, 360)
    val normalizedPhase = ((phaseAngleDegrees % 360f) + 360f) % 360f

    // The angle between view direction and sun direction
    // At phase 0° (new moon): sun is behind the moon (theta = 180° from view)
    // At phase 180° (full moon): sun is in front of the moon (theta = 0° from view)
    val thetaDegrees = 180f - normalizedPhase
    val thetaRad = Math.toRadians(thetaDegrees.toDouble())

    // Build a basis perpendicular to the view direction
    // The sun moves in a plane containing the view direction
    var right = cross(Vec3f.WORLD_UP, viewDir)
    if (right.length() <= EPSILON) {
        right = Vec3f(1f, 0f, 0f)
    }
    right = right.normalized()

    // Sun direction: rotate from view direction by theta around the "right" axis
    // This places the sun in the correct position relative to the Moon
    val cosT = cos(thetaRad).toFloat()
    val sinT = sin(thetaRad).toFloat()

    // Rodrigues' rotation formula simplified for rotation around 'right' axis
    val up = cross(viewDir, right).normalized()
    val sunDir = Vec3f(
        viewDir.x * cosT + up.x * sinT,
        viewDir.y * cosT + up.y * sinT,
        viewDir.z * cosT + up.z * sinT,
    ).normalized()

    return LightDirection(
        lightDegrees = Math.toDegrees(atan2(sunDir.x.toDouble(), sunDir.z.toDouble())).toFloat(),
        sunElevationDegrees = Math.toDegrees(asin(sunDir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat()
    )
}

/**
 * Computes Moon lighting direction from phase angle.
 *
 * Alternative to ephemeris-based calculation when only the phase angle is known.
 *
 * @param phaseAngleDegrees Moon phase angle (0 = new moon, 180 = full moon).
 * @param viewDirX View direction X component.
 * @param viewDirY View direction Y component.
 * @param viewDirZ View direction Z component.
 * @param sunReferenceX Sun reference direction X.
 * @param sunReferenceY Sun reference direction Y.
 * @param sunReferenceZ Sun reference direction Z.
 * @return Light direction for Moon rendering.
 */
private fun computeMoonLightFromPhase(
    phaseAngleDegrees: Float,
    viewDirX: Float,
    viewDirY: Float,
    viewDirZ: Float,
    sunReferenceX: Float,
    sunReferenceY: Float,
    sunReferenceZ: Float,
): LightDirection {
    val viewDir = Vec3f(viewDirX, viewDirY, viewDirZ).normalized()
    val normalizedPhase = ((phaseAngleDegrees % 360f) + 360f) % 360f
    val thetaDegrees = abs(180f - normalizedPhase)

    // Build orthonormal basis from sun reference
    val sunReference = Vec3f(sunReferenceX, sunReferenceY, sunReferenceZ)
    val referenceDir = if (sunReference.length() > EPSILON) {
        sunReference.normalized()
    } else {
        Vec3f.WORLD_UP
    }

    // Project reference onto plane perpendicular to view
    val dotRef = dot(referenceDir, viewDir)
    var basis = Vec3f(
        referenceDir.x - viewDir.x * dotRef,
        referenceDir.y - viewDir.y * dotRef,
        referenceDir.z - viewDir.z * dotRef,
    )

    if (basis.length() <= EPSILON) {
        val axisBase = cross(viewDir, Vec3f.WORLD_UP)
        basis = if (axisBase.length() <= EPSILON) {
            cross(viewDir, Vec3f(1f, 0f, 0f))
        } else {
            axisBase
        }
        if (basis.length() <= EPSILON) {
            basis = cross(viewDir, Vec3f.FORWARD)
        }
    }

    val u = basis.normalized()
    val thetaRad = Math.toRadians(thetaDegrees.toDouble())
    val cosT = cos(thetaRad).toFloat()
    val sinT = sin(thetaRad).toFloat()

    val sunDir = Vec3f(
        viewDir.x * cosT + u.x * sinT,
        viewDir.y * cosT + u.y * sinT,
        viewDir.z * cosT + u.z * sinT,
    ).normalized()

    return LightDirection(
        lightDegrees = Math.toDegrees(atan2(sunDir.x.toDouble(), sunDir.z.toDouble())).toFloat(),
        sunElevationDegrees = Math.toDegrees(asin(sunDir.y.toDouble().coerceIn(-1.0, 1.0))).toFloat()
    )
}

/**
 * Converts azimuth and elevation angles to a direction vector.
 */
private fun sunVectorFromAngles(lightDegrees: Float, sunElevationDegrees: Float): Vec3f {
    val az = lightDegrees * DEG_TO_RAD_F
    val el = sunElevationDegrees * DEG_TO_RAD_F
    val cosEl = cos(el)
    return Vec3f(
        x = sin(az) * cosEl,
        y = sin(el),
        z = cos(az) * cosEl,
    )
}

// ============================================================================
// ORBITAL TRANSFORMATIONS
// ============================================================================

/**
 * Transforms moon orbit position from orbital plane to camera space.
 *
 * Applies orbital inclination and view pitch transformations to convert
 * the moon's position on its circular orbit to screen coordinates.
 *
 * @param moonOrbitDegrees Position on orbit (0 = right, 90 = top, etc.).
 * @param orbitRadius Radius of the orbit in pixels.
 * @param viewPitchRad View pitch angle in radians.
 * @return Transformed position in camera space.
 */
private fun transformMoonOrbitPosition(
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

    // Apply orbital inclination (rotation around X axis)
    val yInc = -z0 * sinInc
    val zInc = z0 * cosInc

    // Apply view pitch (rotation to tilt orbit towards viewer)
    val yCam = yInc * cosView - zInc * sinView
    val zCam = yInc * sinView + zInc * cosView

    return MoonOrbitPosition(x = x0, yCam = yCam, zCam = zCam)
}

/**
 * Calculates perspective scale factor based on depth.
 *
 * @param cameraZ Camera distance from origin.
 * @param z Object depth (positive = towards camera).
 * @return Scale factor (>1 for objects closer than cameraZ).
 */
private fun perspectiveScale(cameraZ: Float, z: Float): Float {
    val denom = max(1f, cameraZ - z)
    return cameraZ / denom
}

// ============================================================================
// SPHERE RENDERING
// ============================================================================

/**
 * Renders a textured sphere with Phong lighting.
 *
 * This is the core rendering function that projects a texture onto a sphere
 * and applies realistic lighting including ambient, diffuse, specular, and
 * atmospheric effects.
 *
 * @param texture Source texture to map onto sphere.
 * @param outputSizePx Output image size (square).
 * @param rotationDegrees Y-axis rotation (longitude).
 * @param lightDegrees Sun azimuth angle.
 * @param tiltDegrees X-axis tilt (axial inclination).
 * @param ambient Base ambient light level.
 * @param diffuseStrength Diffuse lighting intensity.
 * @param specularStrength Specular highlight intensity.
 * @param specularExponent Specular highlight sharpness.
 * @param sunElevationDegrees Sun vertical angle.
 * @param viewDirX Camera direction X.
 * @param viewDirY Camera direction Y.
 * @param viewDirZ Camera direction Z.
 * @param upHintX Optional up vector hint X.
 * @param upHintY Optional up vector hint Y.
 * @param upHintZ Optional up vector hint Z.
 * @param sunVisibility Shadow occlusion factor (0-1).
 * @param atmosphereStrength Rim atmosphere glow intensity.
 * @param shadowAlphaStrength Shadow-based alpha blending.
 * @return ARGB pixel array of rendered sphere.
 */
internal fun renderTexturedSphereArgb(
    texture: EarthTexture,
    outputSizePx: Int,
    rotationDegrees: Float,
    lightDegrees: Float,
    tiltDegrees: Float,
    ambient: Float = DEFAULT_AMBIENT,
    diffuseStrength: Float = DEFAULT_DIFFUSE_STRENGTH,
    specularStrength: Float = 0f,
    specularExponent: Int = 64,
    sunElevationDegrees: Float = 0f,
    viewDirX: Float = 0f,
    viewDirY: Float = 0f,
    viewDirZ: Float = 1f,
    upHintX: Float = 0f,
    upHintY: Float = 0f,
    upHintZ: Float = 0f,
    sunVisibility: Float = 1f,
    atmosphereStrength: Float = DEFAULT_ATMOSPHERE_STRENGTH,
    shadowAlphaStrength: Float = 0f,
): IntArray {
    val output = IntArray(outputSizePx * outputSizePx)

    // Pre-compute rotation and tilt matrices
    val rotationRad = rotationDegrees * DEG_TO_RAD_F
    val tiltRad = tiltDegrees * DEG_TO_RAD_F
    val cosYaw = cos(rotationRad)
    val sinYaw = sin(rotationRad)
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)

    // Light direction
    val sunAzimuthRad = lightDegrees * DEG_TO_RAD_F
    val sunElevationRad = sunElevationDegrees * DEG_TO_RAD_F
    val cosSunElevation = cos(sunElevationRad)
    val sunX = sin(sunAzimuthRad) * cosSunElevation
    val sunY = sin(sunElevationRad)
    val sunZ = cos(sunAzimuthRad) * cosSunElevation

    // Texture data
    val texWidth = texture.width
    val texHeight = texture.height
    val tex = texture.argb

    // Build camera coordinate system
    val cameraFrame = buildCameraFrame(viewDirX, viewDirY, viewDirZ, upHintX, upHintY, upHintZ)

    // Pre-compute half vector for specular (Blinn-Phong)
    val halfVector = computeHalfVector(sunX, sunY, sunZ, cameraFrame)
    val specEnabled = specularStrength > 0f && halfVector != null && specularExponent > 0

    // Screen coordinate helpers
    val halfW = (outputSizePx - 1) / 2f
    val halfH = (outputSizePx - 1) / 2f
    val invHalfW = 1f / halfW
    val invHalfH = 1f / halfH
    val lightVisibility = sunVisibility.coerceIn(0f, 1f)

    // Render each pixel
    for (y in 0 until outputSizePx) {
        val ny = (halfH - y) * invHalfH
        for (x in 0 until outputSizePx) {
            val nx = (x - halfW) * invHalfW
            val rr = nx * nx + ny * ny

            // Skip pixels outside sphere
            if (rr > 1f) {
                output[y * outputSizePx + x] = TRANSPARENT_BLACK
                continue
            }

            // Compute sphere normal at this point
            val nz = sqrt(1f - rr)

            // Transform to world space
            val worldX = cameraFrame.rightX * nx + cameraFrame.upX * ny + cameraFrame.forwardX * nz
            val worldY = cameraFrame.rightY * nx + cameraFrame.upY * ny + cameraFrame.forwardY * nz
            val worldZ = cameraFrame.rightZ * nx + cameraFrame.upZ * ny + cameraFrame.forwardZ * nz

            // Apply rotation and tilt to get texture coordinates
            val rotatedX = worldX * cosTilt - worldY * sinTilt
            val rotatedY = worldX * sinTilt + worldY * cosTilt
            val texX = rotatedX * cosYaw + worldZ * sinYaw
            val texZ = -rotatedX * sinYaw + worldZ * cosYaw

            // Convert to spherical coordinates
            val longitude = atan2(texX, texZ)
            val latitude = asin(rotatedY.coerceIn(-1f, 1f))

            // Map to texture UV coordinates
            var u = (longitude / TWO_PI_F) + 0.5f
            u -= floor(u)
            val v = (0.5f - (latitude / PI.toFloat())).coerceIn(0f, 1f)

            // Sample texture
            val tx = (u * texWidth).toInt().coerceIn(0, texWidth - 1)
            val ty = (v * texHeight).toInt().coerceIn(0, texHeight - 1)
            val texColor = tex[ty * texWidth + tx]

            // Compute lighting
            val pixelColor = computePixelLighting(
                texColor = texColor,
                worldX = worldX, worldY = worldY, worldZ = worldZ,
                sunX = sunX, sunY = sunY, sunZ = sunZ,
                nz = nz, rr = rr,
                ambient = ambient,
                diffuseStrength = diffuseStrength,
                specularStrength = specularStrength,
                specularExponent = specularExponent,
                specEnabled = specEnabled,
                halfVector = halfVector,
                lightVisibility = lightVisibility,
                atmosphereStrength = atmosphereStrength,
                shadowAlphaStrength = shadowAlphaStrength,
            )

            output[y * outputSizePx + x] = pixelColor
        }
    }

    return output
}

/**
 * Camera coordinate frame for view transformations.
 */
private data class CameraFrame(
    val forwardX: Float, val forwardY: Float, val forwardZ: Float,
    val rightX: Float, val rightY: Float, val rightZ: Float,
    val upX: Float, val upY: Float, val upZ: Float,
)

/**
 * Builds an orthonormal camera coordinate frame from view direction.
 */
private fun buildCameraFrame(
    viewDirX: Float, viewDirY: Float, viewDirZ: Float,
    upHintX: Float, upHintY: Float, upHintZ: Float,
): CameraFrame {
    // Normalize forward direction
    var forwardX = viewDirX
    var forwardY = viewDirY
    var forwardZ = viewDirZ
    val forwardLen = sqrt(forwardX * forwardX + forwardY * forwardY + forwardZ * forwardZ)
    if (forwardLen > EPSILON) {
        forwardX /= forwardLen
        forwardY /= forwardLen
        forwardZ /= forwardLen
    } else {
        forwardX = 0f
        forwardY = 0f
        forwardZ = 1f
    }

    // Compute right vector from up hint or default
    var rightX: Float
    var rightY: Float
    var rightZ: Float
    val upHintLen = sqrt(upHintX * upHintX + upHintY * upHintY + upHintZ * upHintZ)

    if (upHintLen > EPSILON) {
        val upHX = upHintX / upHintLen
        val upHY = upHintY / upHintLen
        val upHZ = upHintZ / upHintLen
        rightX = upHY * forwardZ - upHZ * forwardY
        rightY = upHZ * forwardX - upHX * forwardZ
        rightZ = upHX * forwardY - upHY * forwardX
    } else {
        rightX = forwardZ
        rightY = 0f
        rightZ = -forwardX
    }

    // Handle degenerate case
    var rightLen = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
    if (rightLen < EPSILON) {
        rightX = 0f
        rightY = forwardZ
        rightZ = -forwardY
        rightLen = sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ)
    }
    if (rightLen > EPSILON) {
        rightX /= rightLen
        rightY /= rightLen
        rightZ /= rightLen
    }

    // Compute up from forward and right
    val upX = forwardY * rightZ - forwardZ * rightY
    val upY = forwardZ * rightX - forwardX * rightZ
    val upZ = forwardX * rightY - forwardY * rightX

    return CameraFrame(forwardX, forwardY, forwardZ, rightX, rightY, rightZ, upX, upY, upZ)
}

/**
 * Half vector for Blinn-Phong specular, or null if not applicable.
 */
private data class HalfVector(val x: Float, val y: Float, val z: Float)

/**
 * Computes the Blinn-Phong half vector between light and view directions.
 */
private fun computeHalfVector(
    sunX: Float, sunY: Float, sunZ: Float,
    cameraFrame: CameraFrame,
): HalfVector? {
    var halfX = sunX + cameraFrame.forwardX
    var halfY = sunY + cameraFrame.forwardY
    var halfZ = sunZ + cameraFrame.forwardZ
    val halfLen = sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)

    if (halfLen <= EPSILON) return null

    halfX /= halfLen
    halfY /= halfLen
    halfZ /= halfLen

    return HalfVector(halfX, halfY, halfZ)
}

/**
 * Computes the final pixel color with lighting applied.
 */
private fun computePixelLighting(
    texColor: Int,
    worldX: Float, worldY: Float, worldZ: Float,
    sunX: Float, sunY: Float, sunZ: Float,
    nz: Float, rr: Float,
    ambient: Float,
    diffuseStrength: Float,
    specularStrength: Float,
    specularExponent: Int,
    specEnabled: Boolean,
    halfVector: HalfVector?,
    lightVisibility: Float,
    atmosphereStrength: Float,
    shadowAlphaStrength: Float,
): Int {
    // Diffuse lighting (Lambertian)
    val dot = worldX * sunX + worldY * sunY + worldZ * sunZ
    val shadowMask = smoothStep(SHADOW_EDGE_START, SHADOW_EDGE_END, dot) * lightVisibility
    val diffuse = max(dot, 0f) * lightVisibility

    // Combined ambient (darkened in shadow)
    val ambientShade = ambient * (0.25f + 0.75f * shadowMask)
    val baseShade = (ambientShade + diffuseStrength * diffuse).coerceIn(0f, 1f)

    // View-dependent shading (subtle)
    val viewShade = 0.75f + 0.25f * nz
    val shade = (baseShade * viewShade).coerceIn(0f, 1f)

    // Atmospheric rim glow
    val rim = (1f - nz).coerceIn(0f, 1f)
    val atmosphere = (rim * rim * atmosphereStrength * shadowMask).coerceIn(0f, atmosphereStrength)

    // Extract and linearize texture color (gamma decode)
    val a = (texColor ushr 24) and 0xFF
    val r = (texColor ushr 16) and 0xFF
    val g = (texColor ushr 8) and 0xFF
    val b = texColor and 0xFF

    val rLin = (r / 255f).let { it * it }
    val gLin = (g / 255f).let { it * it }
    val bLin = (b / 255f).let { it * it }

    // Specular highlights (primarily on water/oceans)
    val spec = if (specEnabled && diffuse > 0f && halfVector != null) {
        val dotH = (worldX * halfVector.x + worldY * halfVector.y + worldZ * halfVector.z)
            .coerceAtLeast(0f)
        val baseSpec = specularStrength * powInt(dotH, specularExponent) * lightVisibility
        // Ocean detection: blue channel significantly higher than red/green
        val oceanMask = ((b - max(r, g)).coerceAtLeast(0) / 255f).let { it * it }
        baseSpec * (0.12f + 0.88f * oceanMask)
    } else {
        0f
    }

    // Apply shading in linear space
    val shadedRLin = (rLin * shade + spec).coerceIn(0f, 1f)
    val shadedGLin = (gLin * shade + spec).coerceIn(0f, 1f)
    val shadedBLin = (bLin * shade + spec).coerceIn(0f, 1f)

    // Gamma encode back to sRGB and add atmosphere
    val sr = (sqrt(shadedRLin) * 255f).roundToInt().coerceIn(0, 255)
    val sg = (sqrt(shadedGLin) * 255f).roundToInt().coerceIn(0, 255)
    val sb = ((sqrt(shadedBLin) * 255f) + (255f * atmosphere)).roundToInt().coerceIn(0, 255)

    // Edge feathering for smooth sphere boundary
    val dist = sqrt(rr)
    val alpha = ((1f - dist) / EDGE_FEATHER_WIDTH).coerceIn(0f, 1f)

    // Shadow-based alpha (for Moon phases)
    val shadowAlpha = if (shadowAlphaStrength <= 0f) {
        1f
    } else {
        val strength = shadowAlphaStrength.coerceIn(0f, 1f)
        (1f - strength) + strength * shadowMask
    }

    val outA = (a * alpha * shadowAlpha).toInt().coerceIn(0, 255)

    return (outA shl 24) or (sr shl 16) or (sg shl 8) or sb
}

// ============================================================================
// COMPOSITE SCENE RENDERING
// ============================================================================

/**
 * Renders Earth with Moon in orbit.
 *
 * Creates a complete scene with Earth, Moon, optional starfield background,
 * and orbital path visualization.
 *
 * @param earthTexture Earth surface texture.
 * @param moonTexture Moon surface texture.
 * @param outputSizePx Output image size (square).
 * @param earthRotationDegrees Earth rotation angle.
 * @param lightDegrees Sun azimuth for Earth.
 * @param sunElevationDegrees Sun elevation.
 * @param earthTiltDegrees Earth axial tilt.
 * @param moonOrbitDegrees Moon position on orbit.
 * @param markerLatitudeDegrees Marker latitude on Earth.
 * @param markerLongitudeDegrees Marker longitude on Earth.
 * @param moonRotationDegrees Moon rotation.
 * @param showBackgroundStars Whether to draw starfield.
 * @param showOrbitPath Whether to draw orbit path.
 * @param moonLightDegrees Override for Moon light direction.
 * @param moonSunElevationDegrees Override for Moon sun elevation.
 * @param moonPhaseAngleDegrees Moon phase for lighting calculation.
 * @param julianDay Julian Day for ephemeris calculation.
 * @return ARGB pixel array of complete scene.
 */
internal fun renderEarthWithMoonArgb(
    earthTexture: EarthTexture?,
    moonTexture: EarthTexture?,
    outputSizePx: Int,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    moonRotationDegrees: Float = 0f,
    showBackgroundStars: Boolean = true,
    showOrbitPath: Boolean = true,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
): IntArray {
    val out = IntArray(outputSizePx * outputSizePx)
    if (earthTexture == null) return out

    val sceneHalf = outputSizePx / 2f
    val cameraZ = outputSizePx * CAMERA_DISTANCE_FACTOR

    // Fill background
    out.fill(OPAQUE_BLACK)
    if (showBackgroundStars) {
        drawStarfield(dst = out, dstW = outputSizePx, dstH = outputSizePx, seed = STARFIELD_SEED)
    }

    // Calculate Earth dimensions
    val earthSizePx = (outputSizePx * EARTH_SIZE_FRACTION).roundToInt().coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val earthRadiusPx = (earthSizePx - 1) / 2f
    val earthLeft = (sceneHalf - earthSizePx / 2f).roundToInt()
    val earthTop = (sceneHalf - earthSizePx / 2f).roundToInt()

    // Calculate Moon dimensions
    val moonBaseSizePx = (earthSizePx * MOON_TO_EARTH_DIAMETER_RATIO).roundToInt()
        .coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val moonRadiusWorldPx = (moonBaseSizePx - 1) / 2f
    val edgeMarginPx = max(6f, outputSizePx * 0.02f)
    val orbitRadius = (sceneHalf - moonRadiusWorldPx - edgeMarginPx).coerceAtLeast(0f)

    // Calculate view pitch to ensure Moon clears Earth
    val desiredSeparation = earthRadiusPx + moonRadiusWorldPx + 1.5f
    val viewPitchRad = if (orbitRadius > EPSILON) {
        asin((desiredSeparation / orbitRadius).coerceIn(0f, 0.999f))
    } else {
        0f
    }

    // Transform moon position
    val moonOrbit = transformMoonOrbitPosition(moonOrbitDegrees, orbitRadius, viewPitchRad)

    // Pre-compute orbit transform parameters
    val orbitInclinationRad = MOON_ORBIT_INCLINATION_DEG * DEG_TO_RAD_F
    val cosInc = cos(orbitInclinationRad)
    val sinInc = sin(orbitInclinationRad)
    val cosView = cos(viewPitchRad)
    val sinView = sin(viewPitchRad)

    // Calculate Moon screen position
    val moonScale = perspectiveScale(cameraZ, moonOrbit.zCam)
    val moonSizePx = (moonBaseSizePx * moonScale).roundToInt().coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val moonRadiusPx = (moonSizePx - 1) / 2f
    val moonCenterX = sceneHalf + moonOrbit.x * moonScale
    val moonCenterY = sceneHalf - moonOrbit.yCam * moonScale
    val moonLeft = (moonCenterX - moonRadiusPx).roundToInt()
    val moonTop = (moonCenterY - moonRadiusPx).roundToInt()

    // Render Earth
    val earth = renderTexturedSphereArgb(
        texture = earthTexture,
        outputSizePx = earthSizePx,
        rotationDegrees = earthRotationDegrees,
        lightDegrees = lightDegrees,
        tiltDegrees = earthTiltDegrees,
        specularStrength = EARTH_SPECULAR_STRENGTH,
        specularExponent = EARTH_SPECULAR_EXPONENT,
        sunElevationDegrees = sunElevationDegrees,
        viewDirZ = 1f,
    )

    // Draw marker on Earth
    drawMarkerOnSphere(
        sphereArgb = earth,
        sphereSizePx = earthSizePx,
        markerLatitudeDegrees = markerLatitudeDegrees,
        markerLongitudeDegrees = markerLongitudeDegrees,
        rotationDegrees = earthRotationDegrees,
        tiltDegrees = earthTiltDegrees,
    )

    // Composite Earth onto scene
    blitOver(dst = out, dstW = outputSizePx, src = earth, srcW = earthSizePx, left = earthLeft, top = earthTop)

    // Draw orbit path
    if (showOrbitPath) {
        drawOrbitPath(
            dst = out,
            dstW = outputSizePx,
            dstH = outputSizePx,
            center = sceneHalf,
            earthRadiusPx = earthRadiusPx,
            orbitRadius = orbitRadius,
            cosInc = cosInc,
            sinInc = sinInc,
            cosView = cosView,
            sinView = sinView,
            moonCenterX = moonCenterX,
            moonCenterY = moonCenterY,
            moonRadiusPx = if (moonTexture != null) moonRadiusPx else 0f,
            cameraZ = cameraZ,
        )
    }

    if (moonTexture == null) return out

    // Calculate Moon lighting
    val moonViewDirX = -moonOrbit.x
    val moonViewDirY = -moonOrbit.yCam
    val moonViewDirZ = cameraZ - moonOrbit.zCam

    val moonLighting = computeMoonLighting(
        julianDay = julianDay,
        moonPhaseAngleDegrees = moonPhaseAngleDegrees,
        moonViewDirX = moonViewDirX,
        moonViewDirY = moonViewDirY,
        moonViewDirZ = moonViewDirZ,
        lightDegrees = lightDegrees,
        sunElevationDegrees = sunElevationDegrees,
    )
    val moonLightDegreesResolved = moonLighting?.lightDegrees ?: moonLightDegrees
    val moonSunElevationDegreesResolved = moonLighting?.sunElevationDegrees ?: moonSunElevationDegrees

    // Calculate Moon shadow from Earth
    val sunVisibility = moonSunVisibility(
        moonCenterX = moonOrbit.x,
        moonCenterY = moonOrbit.yCam,
        moonCenterZ = moonOrbit.zCam,
        moonRadius = moonRadiusWorldPx,
        sunAzimuthDegrees = moonLightDegreesResolved,
        sunElevationDegrees = moonSunElevationDegreesResolved,
    )

    // Render Moon
    val moon = renderTexturedSphereArgb(
        texture = moonTexture,
        outputSizePx = moonSizePx,
        rotationDegrees = moonRotationDegrees,
        lightDegrees = moonLightDegreesResolved,
        tiltDegrees = 0f,
        ambient = MOON_AMBIENT,
        diffuseStrength = MOON_DIFFUSE_STRENGTH,
        sunElevationDegrees = moonSunElevationDegreesResolved,
        viewDirX = moonViewDirX,
        viewDirY = moonViewDirY,
        viewDirZ = moonViewDirZ,
        sunVisibility = sunVisibility,
        atmosphereStrength = 0f,
        shadowAlphaStrength = 1f,
    )

    // Composite Moon with depth sorting
    compositeMoonWithDepth(
        out = out,
        outputSizePx = outputSizePx,
        earth = earth,
        earthSizePx = earthSizePx,
        earthLeft = earthLeft,
        earthTop = earthTop,
        earthRadiusPx = earthRadiusPx,
        moon = moon,
        moonSizePx = moonSizePx,
        moonLeft = moonLeft,
        moonTop = moonTop,
        moonRadiusPx = moonRadiusPx,
        moonRadiusWorldPx = moonRadiusWorldPx,
        moonZCam = moonOrbit.zCam,
        moonScale = moonScale,
    )

    return out
}

/**
 * Computes Moon lighting from ephemeris or phase angle.
 */
private fun computeMoonLighting(
    julianDay: Double?,
    moonPhaseAngleDegrees: Float?,
    moonViewDirX: Float,
    moonViewDirY: Float,
    moonViewDirZ: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
): LightDirection? = when {
    julianDay != null -> computeGeometricMoonIllumination(
        julianDay = julianDay,
        viewDirX = moonViewDirX,
        viewDirY = moonViewDirY,
        viewDirZ = moonViewDirZ,
    )
    moonPhaseAngleDegrees != null -> {
        val sunReference = sunVectorFromAngles(lightDegrees, sunElevationDegrees)
        computeMoonLightFromPhase(
            phaseAngleDegrees = moonPhaseAngleDegrees,
            viewDirX = moonViewDirX,
            viewDirY = moonViewDirY,
            viewDirZ = moonViewDirZ,
            sunReferenceX = sunReference.x,
            sunReferenceY = sunReference.y,
            sunReferenceZ = sunReference.z,
        )
    }
    else -> null
}

/**
 * Composites Moon onto scene with depth-aware blending against Earth.
 */
private fun compositeMoonWithDepth(
    out: IntArray,
    outputSizePx: Int,
    earth: IntArray,
    earthSizePx: Int,
    earthLeft: Int,
    earthTop: Int,
    earthRadiusPx: Float,
    moon: IntArray,
    moonSizePx: Int,
    moonLeft: Int,
    moonTop: Int,
    moonRadiusPx: Float,
    moonRadiusWorldPx: Float,
    moonZCam: Float,
    moonScale: Float,
) {
    val x0Moon = moonLeft.coerceAtLeast(0)
    val y0Moon = moonTop.coerceAtLeast(0)
    val x1Moon = (moonLeft + moonSizePx).coerceAtMost(outputSizePx)
    val y1Moon = (moonTop + moonSizePx).coerceAtMost(outputSizePx)
    val invMoonScale = if (moonScale > EPSILON) 1f / moonScale else 1f

    for (y in y0Moon until y1Moon) {
        val moonY = y - moonTop
        val moonDyScreen = moonRadiusPx - moonY
        val moonDyWorld = moonDyScreen * invMoonScale
        val moonRow = moonY * moonSizePx

        val earthY = y - earthTop
        val earthDy = earthRadiusPx - earthY
        val earthRow = earthY * earthSizePx
        val hasEarthRow = earthY in 0 until earthSizePx

        for (x in x0Moon until x1Moon) {
            val moonX = x - moonLeft
            val moonColor = moon[moonRow + moonX]
            val moonA = (moonColor ushr 24) and 0xFF
            if (moonA == 0) continue

            val dstIndex = y * outputSizePx + x

            if (!hasEarthRow) {
                out[dstIndex] = alphaOver(moonColor, out[dstIndex])
                continue
            }

            val earthX = x - earthLeft
            if (earthX !in 0 until earthSizePx) {
                out[dstIndex] = alphaOver(moonColor, out[dstIndex])
                continue
            }

            val earthColor = earth[earthRow + earthX]
            val earthA = (earthColor ushr 24) and 0xFF
            if (earthA == 0) {
                out[dstIndex] = alphaOver(moonColor, out[dstIndex])
                continue
            }

            // Depth comparison
            val earthDx = earthX - earthRadiusPx
            val moonDxScreen = moonX - moonRadiusPx
            val moonDxWorld = moonDxScreen * invMoonScale
            val earthR2 = (earthDx * earthDx + earthDy * earthDy) / (earthRadiusPx * earthRadiusPx)
            val moonR2 = (moonDxWorld * moonDxWorld + moonDyWorld * moonDyWorld) /
                    (moonRadiusWorldPx * moonRadiusWorldPx)
            val earthZ = sqrt(max(0f, 1f - earthR2)) * earthRadiusPx
            val moonZ = sqrt(max(0f, 1f - moonR2)) * moonRadiusWorldPx

            val moonDepth = moonZCam + moonZ
            out[dstIndex] = if (moonDepth > earthZ) {
                alphaOver(moonColor, earthColor)
            } else {
                alphaOver(earthColor, moonColor)
            }
        }
    }
}

/**
 * Renders the Moon as seen from a marker position on Earth.
 *
 * @param moonTexture Moon surface texture.
 * @param outputSizePx Output image size.
 * @param lightDegrees Sun azimuth.
 * @param sunElevationDegrees Sun elevation.
 * @param earthRotationDegrees Earth rotation.
 * @param earthTiltDegrees Earth axial tilt.
 * @param moonOrbitDegrees Moon position on orbit.
 * @param markerLatitudeDegrees Observer latitude.
 * @param markerLongitudeDegrees Observer longitude.
 * @param moonRotationDegrees Moon rotation.
 * @param showBackgroundStars Whether to draw starfield.
 * @param moonLightDegrees Override for Moon light direction.
 * @param moonSunElevationDegrees Override for Moon sun elevation.
 * @param moonPhaseAngleDegrees Moon phase for lighting.
 * @param julianDay Julian Day for ephemeris.
 * @return ARGB pixel array of Moon view.
 */
internal fun renderMoonFromMarkerArgb(
    moonTexture: EarthTexture?,
    outputSizePx: Int,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    moonRotationDegrees: Float = 0f,
    showBackgroundStars: Boolean = true,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
): IntArray {
    val out = IntArray(outputSizePx * outputSizePx)
    out.fill(OPAQUE_BLACK)

    if (showBackgroundStars) {
        drawStarfield(dst = out, dstW = outputSizePx, dstH = outputSizePx, seed = STARFIELD_SEED)
    }

    if (moonTexture == null) return out

    // Calculate scene geometry (same as main scene for consistency)
    val sceneHalf = outputSizePx / 2f
    val earthSizePx = (outputSizePx * EARTH_SIZE_FRACTION).roundToInt().coerceAtLeast(MIN_SPHERE_SIZE_PX)
    val earthRadiusPx = (earthSizePx - 1) / 2f

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

    // Get Moon position
    val moonOrbit = transformMoonOrbitPosition(moonOrbitDegrees, orbitRadius, viewPitchRad)

    // Calculate observer position on Earth surface
    val observerPosition = calculateObserverPosition(
        markerLatitudeDegrees = markerLatitudeDegrees,
        markerLongitudeDegrees = markerLongitudeDegrees,
        earthRotationDegrees = earthRotationDegrees,
        earthTiltDegrees = earthTiltDegrees,
        earthRadiusPx = earthRadiusPx,
    )

    // View direction from observer to Moon
    val viewDirX = observerPosition.x - moonOrbit.x
    val viewDirY = observerPosition.y - moonOrbit.yCam
    val viewDirZ = observerPosition.z - moonOrbit.zCam

    // Up hint is radial direction at observer position
    val upLen = sqrt(observerPosition.x * observerPosition.x +
            observerPosition.y * observerPosition.y +
            observerPosition.z * observerPosition.z)
    val upHintX = if (upLen > EPSILON) observerPosition.x / upLen else 0f
    val upHintY = if (upLen > EPSILON) observerPosition.y / upLen else 1f
    val upHintZ = if (upLen > EPSILON) observerPosition.z / upLen else 0f

    // Calculate Moon lighting
    val moonLighting = computeMoonLighting(
        julianDay = julianDay,
        moonPhaseAngleDegrees = moonPhaseAngleDegrees,
        moonViewDirX = viewDirX,
        moonViewDirY = viewDirY,
        moonViewDirZ = viewDirZ,
        lightDegrees = lightDegrees,
        sunElevationDegrees = sunElevationDegrees,
    )
    val moonLightDegreesResolved = moonLighting?.lightDegrees ?: moonLightDegrees
    val moonSunElevationDegreesResolved = moonLighting?.sunElevationDegrees ?: moonSunElevationDegrees

    val sunVisibility = moonSunVisibility(
        moonCenterX = moonOrbit.x,
        moonCenterY = moonOrbit.yCam,
        moonCenterZ = moonOrbit.zCam,
        moonRadius = moonRadiusWorldPx,
        sunAzimuthDegrees = moonLightDegreesResolved,
        sunElevationDegrees = moonSunElevationDegreesResolved,
    )

    // Render Moon
    val moon = renderTexturedSphereArgb(
        texture = moonTexture,
        outputSizePx = outputSizePx,
        rotationDegrees = moonRotationDegrees,
        lightDegrees = moonLightDegreesResolved,
        tiltDegrees = 0f,
        ambient = MOON_AMBIENT,
        diffuseStrength = MOON_DIFFUSE_STRENGTH,
        sunElevationDegrees = moonSunElevationDegreesResolved,
        viewDirX = viewDirX,
        viewDirY = viewDirY,
        viewDirZ = viewDirZ,
        upHintX = upHintX,
        upHintY = upHintY,
        upHintZ = upHintZ,
        sunVisibility = sunVisibility,
        atmosphereStrength = 0f,
        shadowAlphaStrength = 1f,
    )

    blitOver(dst = out, dstW = outputSizePx, src = moon, srcW = outputSizePx, left = 0, top = 0)
    return out
}

/**
 * Observer position on Earth surface in world coordinates.
 */
private data class ObserverPosition(val x: Float, val y: Float, val z: Float)

/**
 * Calculates observer position on Earth surface given lat/lon and Earth orientation.
 */
private fun calculateObserverPosition(
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    earthRotationDegrees: Float,
    earthTiltDegrees: Float,
    earthRadiusPx: Float,
): ObserverPosition {
    val latRad = markerLatitudeDegrees.coerceIn(-90f, 90f) * DEG_TO_RAD_F
    val lonRad = markerLongitudeDegrees.coerceIn(-180f, 180f) * DEG_TO_RAD_F
    val cosLat = cos(latRad)

    // Position on unit sphere
    val texX = sin(lonRad) * cosLat
    val texY = sin(latRad)
    val texZ = cos(lonRad) * cosLat

    // Apply Earth rotation (yaw)
    val earthYawRad = earthRotationDegrees * DEG_TO_RAD_F
    val cosYaw = cos(earthYawRad)
    val sinYaw = sin(earthYawRad)
    val xRot = texX * cosYaw - texZ * sinYaw
    val zRot = texX * sinYaw + texZ * cosYaw

    // Apply Earth tilt (pitch)
    val tiltRad = earthTiltDegrees * DEG_TO_RAD_F
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)

    return ObserverPosition(
        x = (xRot * cosTilt + texY * sinTilt) * earthRadiusPx,
        y = (-xRot * sinTilt + texY * cosTilt) * earthRadiusPx,
        z = zRot * earthRadiusPx,
    )
}

// ============================================================================
// ORBIT PATH RENDERING
// ============================================================================

/**
 * Draws the Moon's orbital path with perspective projection.
 */
private fun drawOrbitPath(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    center: Float,
    earthRadiusPx: Float,
    orbitRadius: Float,
    cosInc: Float,
    sinInc: Float,
    cosView: Float,
    sinView: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusPx: Float,
    cameraZ: Float,
) {
    if (orbitRadius <= 0f) return

    val steps = (orbitRadius * 5.2f).roundToInt().coerceIn(420, 1600)
    val earthRadius2 = earthRadiusPx * earthRadiusPx
    val moonRadiusClip2 = if (moonRadiusPx > 0f) (moonRadiusPx + 3.0f).let { it * it } else -1f

    var prevX = Int.MIN_VALUE
    var prevY = Int.MIN_VALUE
    var prevZ = 0f

    for (i in 0..steps) {
        val t = (i.toFloat() / steps) * TWO_PI_F
        val x0 = cos(t) * orbitRadius
        val z0 = sin(t) * orbitRadius

        // Apply orbital inclination
        val yInc = -z0 * sinInc
        val zInc = z0 * cosInc

        // Apply view pitch
        val yCam = yInc * cosView - zInc * sinView
        val zCam = yInc * sinView + zInc * cosView

        val orbitScale = perspectiveScale(cameraZ, zCam)
        val sx = (center + x0 * orbitScale).roundToInt()
        val sy = (center - yCam * orbitScale).roundToInt()

        if (prevX != Int.MIN_VALUE) {
            val avgZ = (prevZ + zCam) * 0.5f
            val alpha = if (avgZ >= 0f) ORBIT_ALPHA_FRONT else ORBIT_ALPHA_BACK
            val color = (alpha shl 24) or ORBIT_COLOR_RGB

            drawOrbitLineSegment(
                dst = dst, dstW = dstW, dstH = dstH,
                x0 = prevX, y0 = prevY, x1 = sx, y1 = sy,
                color = color, orbitZ = avgZ,
                earthCenter = center, earthRadiusPx = earthRadiusPx, earthRadius2 = earthRadius2,
                moonCenterX = moonCenterX, moonCenterY = moonCenterY, moonRadiusClip2 = moonRadiusClip2,
            )
        }

        prevX = sx
        prevY = sy
        prevZ = zCam
    }
}

/**
 * Draws a line segment of the orbit path using Bresenham's algorithm.
 */
private fun drawOrbitLineSegment(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    color: Int,
    orbitZ: Float,
    earthCenter: Float,
    earthRadiusPx: Float,
    earthRadius2: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusClip2: Float,
) {
    var x = x0
    var y = y0
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy

    while (true) {
        plotOrbitPixel(
            dst = dst, dstW = dstW, dstH = dstH,
            x = x, y = y, color = color, orbitZ = orbitZ,
            earthCenter = earthCenter, earthRadiusPx = earthRadiusPx, earthRadius2 = earthRadius2,
            moonCenterX = moonCenterX, moonCenterY = moonCenterY, moonRadiusClip2 = moonRadiusClip2,
        )

        if (x == x1 && y == y1) break
        val e2 = 2 * err
        if (e2 >= dy) {
            if (x == x1) break
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            if (y == y1) break
            err += dx
            y += sy
        }
    }
}

/**
 * Plots a single orbit pixel with depth testing and glow effect.
 */
private fun plotOrbitPixel(
    dst: IntArray,
    dstW: Int,
    dstH: Int,
    x: Int,
    y: Int,
    color: Int,
    orbitZ: Float,
    earthCenter: Float,
    earthRadiusPx: Float,
    earthRadius2: Float,
    moonCenterX: Float,
    moonCenterY: Float,
    moonRadiusClip2: Float,
) {
    if (x !in 0 until dstW || y !in 0 until dstH) return

    // Depth test against Earth
    val dx = x - earthCenter
    val dy = y - earthCenter
    val r2 = dx * dx + dy * dy
    if (r2 <= earthRadius2) {
        val earthZ = sqrt((earthRadius2 - r2).coerceAtLeast(0f))
        if (orbitZ <= earthZ) return
    }

    // Clip against Moon
    if (moonRadiusClip2 > 0f) {
        val mdx = x - moonCenterX
        val mdy = y - moonCenterY
        if (mdx * mdx + mdy * mdy <= moonRadiusClip2) return
    }

    // Draw main pixel
    val index = y * dstW + x
    dst[index] = alphaOver(color, dst[index])

    // Draw glow
    val a = (color ushr 24) and 0xFF
    val glowAlpha = (a * ORBIT_GLOW_INTENSITY).roundToInt().coerceIn(0, 255)
    if (glowAlpha == 0) return

    val glowColor = (glowAlpha shl 24) or (color and 0x00FFFFFF)

    // Helper to blend glow at adjacent pixels
    fun blendGlowAt(px: Int, py: Int, dstIndex: Int) {
        val gx = px - earthCenter
        val gy = py - earthCenter
        val gr2 = gx * gx + gy * gy
        if (gr2 <= earthRadius2) {
            val earthZ = sqrt((earthRadius2 - gr2).coerceAtLeast(0f))
            if (orbitZ <= earthZ) return
        }
        if (moonRadiusClip2 > 0f) {
            val gmx = px - moonCenterX
            val gmy = py - moonCenterY
            if (gmx * gmx + gmy * gmy <= moonRadiusClip2) return
        }
        dst[dstIndex] = alphaOver(glowColor, dst[dstIndex])
    }

    if (x + 1 < dstW) blendGlowAt(x + 1, y, index + 1)
    if (x - 1 >= 0) blendGlowAt(x - 1, y, index - 1)
    if (y + 1 < dstH) blendGlowAt(x, y + 1, index + dstW)
    if (y - 1 >= 0) blendGlowAt(x, y - 1, index - dstW)
}

// ============================================================================
// STARFIELD RENDERING
// ============================================================================

/**
 * Draws a procedurally generated starfield background.
 *
 * Uses a seeded PRNG for deterministic star placement.
 */
private fun drawStarfield(dst: IntArray, dstW: Int, dstH: Int, seed: Int) {
    val pixelCount = dstW * dstH
    val starCount = (pixelCount / PIXELS_PER_STAR).coerceIn(MIN_STAR_COUNT, MAX_STAR_COUNT)
    var state = seed xor (dstW shl 16) xor dstH

    repeat(starCount) {
        // Random position
        state = xorshift32(state)
        val x = (state ushr 1) % dstW
        state = xorshift32(state)
        val y = (state ushr 1) % dstH

        // Random brightness with cubic falloff (more dim stars)
        state = xorshift32(state)
        val t = ((state ushr 24) and 0xFF) / 255f
        val intensity = (32f + 223f * (t * t * t)).roundToInt().coerceIn(0, 255)

        // Subtle color tint
        state = xorshift32(state)
        val tint = (state ushr 29) and 0x7
        val r = (intensity + (tint - 3) * 4).coerceIn(0, 255)
        val g = (intensity + (tint - 3) * 2).coerceIn(0, 255)
        val b = (intensity + (tint - 3) * 5).coerceIn(0, 255)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        val index = y * dstW + x
        if (dst[index] != OPAQUE_BLACK) return@repeat
        dst[index] = color

        // Occasional sparkle for bright stars
        val sparkleChance = (state ushr 25) and 0x1F
        if (sparkleChance == 0) {
            stampStar(dst = dst, dstW = dstW, dstH = dstH, centerX = x, centerY = y, color = color)
        }
    }
}

/**
 * Stamps a star sparkle pattern around a bright star.
 */
private fun stampStar(dst: IntArray, dstW: Int, dstH: Int, centerX: Int, centerY: Int, color: Int) {
    val offsets = intArrayOf(-1, 0, 1)
    for (dy in offsets) {
        val y = centerY + dy
        if (y !in 0 until dstH) continue
        val row = y * dstW
        for (dx in offsets) {
            if (dx == 0 && dy == 0) continue
            val x = centerX + dx
            if (x !in 0 until dstW) continue
            val index = row + x
            if (dst[index] != OPAQUE_BLACK) continue

            // Cross pattern is brighter than diagonal
            val alpha = if (dx == 0 || dy == 0) 0x88 else 0x66
            val tinted = (alpha shl 24) or (color and 0x00FFFFFF)
            dst[index] = alphaOver(tinted, dst[index])
        }
    }
}

/**
 * xorshift32 pseudo-random number generator.
 *
 * Fast, deterministic PRNG suitable for procedural generation.
 */
private fun xorshift32(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}

// ============================================================================
// MARKER RENDERING
// ============================================================================

/**
 * Draws a location marker on a rendered sphere.
 *
 * @param sphereArgb Sphere pixel data to modify.
 * @param sphereSizePx Sphere size in pixels.
 * @param markerLatitudeDegrees Marker latitude.
 * @param markerLongitudeDegrees Marker longitude.
 * @param rotationDegrees Sphere rotation.
 * @param tiltDegrees Sphere tilt.
 */
private fun drawMarkerOnSphere(
    sphereArgb: IntArray,
    sphereSizePx: Int,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    rotationDegrees: Float,
    tiltDegrees: Float,
) {
    // Convert marker coordinates to 3D position
    val latRad = markerLatitudeDegrees.coerceIn(-90f, 90f) * DEG_TO_RAD_F
    val lonRad = markerLongitudeDegrees.coerceIn(-180f, 180f) * DEG_TO_RAD_F
    val cosLat = cos(latRad)

    val texX = sin(lonRad) * cosLat
    val texY = sin(latRad)
    val texZ = cos(lonRad) * cosLat

    // Apply rotation
    val yawRad = rotationDegrees * DEG_TO_RAD
    val cosYaw = cos(yawRad)
    val sinYaw = sin(yawRad)
    val x1 = texX * cosYaw - texZ * sinYaw
    val z1 = texX * sinYaw + texZ * cosYaw

    // Apply tilt
    val tiltRad = tiltDegrees * DEG_TO_RAD
    val cosTilt = cos(tiltRad)
    val sinTilt = sin(tiltRad)
    val x2 = x1 * cosTilt + texY * sinTilt
    val y2 = -x1 * sinTilt + texY * cosTilt

    // Only draw if marker is on visible hemisphere (facing camera)
    if (z1 <= 0f) return

    // Project to screen coordinates
    val half = (sphereSizePx - 1) / 2f
    val centerX = half + x2 * half
    val centerY = half - y2 * half

    // Calculate marker sizes
    val markerRadiusPx = max(MIN_MARKER_RADIUS_PX, sphereSizePx * MARKER_RADIUS_FRACTION)
    val outlineRadiusPx = markerRadiusPx + MARKER_OUTLINE_EXTRA_PX

    // Bounds for iteration
    val minX = (centerX - outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val maxX = (centerX + outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val minY = (centerY - outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)
    val maxY = (centerY + outlineRadiusPx).roundToInt().coerceIn(0, sphereSizePx - 1)

    val outlineR2 = outlineRadiusPx * outlineRadiusPx
    val fillR2 = markerRadiusPx * markerRadiusPx

    // Draw marker
    for (y in minY..maxY) {
        val dy = y - centerY
        val row = y * sphereSizePx
        for (x in minX..maxX) {
            val dstIndex = row + x
            // Only draw on visible sphere surface
            if (((sphereArgb[dstIndex] ushr 24) and 0xFF) == 0) continue

            val dx = x - centerX
            val d2 = dx * dx + dy * dy
            when {
                d2 <= fillR2 -> sphereArgb[dstIndex] = MARKER_FILL_COLOR
                d2 <= outlineR2 -> sphereArgb[dstIndex] = MARKER_OUTLINE_COLOR
            }
        }
    }
}

// ============================================================================
// SHADOW CALCULATIONS
// ============================================================================

/**
 * Computes visibility of sun from Moon's position (for eclipse shadows).
 *
 * Returns 0 when Moon is in Earth's shadow (lunar eclipse),
 * 1 when fully illuminated, with smooth penumbra transition.
 *
 * @param moonCenterX Moon X position.
 * @param moonCenterY Moon Y position.
 * @param moonCenterZ Moon Z position.
 * @param moonRadius Moon radius.
 * @param sunAzimuthDegrees Sun azimuth.
 * @param sunElevationDegrees Sun elevation.
 * @return Visibility factor (0-1).
 */
private fun moonSunVisibility(
    moonCenterX: Float,
    moonCenterY: Float,
    moonCenterZ: Float,
    moonRadius: Float,
    sunAzimuthDegrees: Float,
    sunElevationDegrees: Float,
): Float {
    val sunDir = sunVectorFromAngles(sunAzimuthDegrees, sunElevationDegrees)

    // Project Moon position onto sun direction
    val proj = moonCenterX * sunDir.x + moonCenterY * sunDir.y + moonCenterZ * sunDir.z

    // If Moon is on sun side of Earth, fully lit
    if (proj > 0f) return 1f

    // Distance from Moon to Earth-Sun axis
    val r2 = moonCenterX * moonCenterX + moonCenterY * moonCenterY + moonCenterZ * moonCenterZ
    val moonDistance = sqrt(r2)
    val d2 = (r2 - proj * proj).coerceAtLeast(0f)
    val d = sqrt(d2)

    // Scale Earth's shadow using realistic umbra/penumbra size at lunar distance
    val umbraRadius = moonDistance * EARTH_UMBRA_DISTANCE_RATIO
    val penumbraRadius = moonDistance * EARTH_PENUMBRA_DISTANCE_RATIO
    val softPenumbra = (penumbraRadius + moonRadius * 0.12f).coerceAtLeast(umbraRadius)

    // Smooth transition through penumbra
    return smoothStep(umbraRadius, softPenumbra, d)
}

// ============================================================================
// ALPHA BLENDING
// ============================================================================

/**
 * Copies source image onto destination with alpha blending.
 */
private fun blitOver(dst: IntArray, dstW: Int, src: IntArray, srcW: Int, left: Int, top: Int) {
    val srcH = src.size / srcW
    val dstH = dst.size / dstW

    val x0 = left.coerceAtLeast(0)
    val y0 = top.coerceAtLeast(0)
    val x1 = (left + srcW).coerceAtMost(dstW)
    val y1 = (top + srcH).coerceAtMost(dstH)

    for (y in y0 until y1) {
        val srcY = y - top
        val dstRow = y * dstW
        val srcRow = srcY * srcW
        for (x in x0 until x1) {
            val srcColor = src[srcRow + (x - left)]
            val srcA = (srcColor ushr 24) and 0xFF
            if (srcA == 0) continue

            val dstIndex = dstRow + x
            if (srcA == 255) {
                dst[dstIndex] = srcColor
                continue
            }

            dst[dstIndex] = alphaOver(srcColor, dst[dstIndex])
        }
    }
}

/**
 * Porter-Duff "over" alpha compositing operation.
 *
 * Blends foreground color over background color.
 */
private fun alphaOver(foreground: Int, background: Int): Int {
    val fgA = (foreground ushr 24) and 0xFF
    if (fgA == 255) return foreground
    if (fgA == 0) return background

    val bgA = (background ushr 24) and 0xFF
    val invA = 255 - fgA
    val outA = (fgA + (bgA * invA + 127) / 255).coerceIn(0, 255)

    val fgR = (foreground ushr 16) and 0xFF
    val fgG = (foreground ushr 8) and 0xFF
    val fgB = foreground and 0xFF
    val bgR = (background ushr 16) and 0xFF
    val bgG = (background ushr 8) and 0xFF
    val bgB = background and 0xFF

    val outR = (fgR * fgA + bgR * invA + 127) / 255
    val outG = (fgG * fgA + bgG * invA + 127) / 255
    val outB = (fgB * fgA + bgB * invA + 127) / 255

    return (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
}

// ============================================================================
// MATH UTILITIES
// ============================================================================

/**
 * Fast integer power function using binary exponentiation.
 */
private fun powInt(base: Float, exponent: Int): Float {
    var result = 1f
    var powBase = base
    var exp = exponent
    while (exp > 0) {
        if ((exp and 1) == 1) result *= powBase
        powBase *= powBase
        exp = exp ushr 1
    }
    return result
}

/**
 * Smooth Hermite interpolation between edges.
 *
 * Returns 0 if x <= edge0, 1 if x >= edge1, smoothly interpolated between.
 */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
