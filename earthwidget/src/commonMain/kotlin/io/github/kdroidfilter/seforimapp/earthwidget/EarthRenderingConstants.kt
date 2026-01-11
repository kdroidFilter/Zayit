package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI

// ============================================================================
// MATHEMATICAL CONSTANTS
// ============================================================================

/** Degrees to radians conversion factor (Double precision). */
internal const val DEG_TO_RAD = PI / 180.0

/** Degrees to radians conversion factor (Float precision). */
internal const val DEG_TO_RAD_F = (PI / 180.0).toFloat()

/** Two times PI as Float for orbit calculations. */
internal const val TWO_PI_F = (2.0 * PI).toFloat()

// ============================================================================
// ASTRONOMICAL CONSTANTS
// ============================================================================

/** Moon diameter relative to Earth diameter (actual ratio ~0.2727). */
internal const val MOON_TO_EARTH_DIAMETER_RATIO = 0.2724f

/** Moon orbital inclination relative to ecliptic plane in degrees. */
internal const val MOON_ORBIT_INCLINATION_DEG = 5.145f

// ============================================================================
// J2000.0 EPOCH CONSTANTS (Meeus Algorithm)
// ============================================================================

/** Moon mean longitude at J2000.0 epoch (degrees). */
internal const val MOON_MEAN_LONGITUDE_J2000 = 218.3164477

/** Moon mean longitude rate (degrees per Julian century). */
internal const val MOON_MEAN_LONGITUDE_RATE = 481267.88123421

/** Moon mean anomaly at J2000.0 epoch (degrees). */
internal const val MOON_MEAN_ANOMALY_J2000 = 134.9633964

/** Moon mean anomaly rate (degrees per Julian century). */
internal const val MOON_MEAN_ANOMALY_RATE = 477198.8675055

/** Moon mean elongation at J2000.0 epoch (degrees). */
internal const val MOON_MEAN_ELONGATION_J2000 = 297.8501921

/** Moon mean elongation rate (degrees per Julian century). */
internal const val MOON_MEAN_ELONGATION_RATE = 445267.1114034

/** Sun mean anomaly at J2000.0 epoch (degrees). */
internal const val SUN_MEAN_ANOMALY_J2000 = 357.5291092

/** Sun mean anomaly rate (degrees per Julian century). */
internal const val SUN_MEAN_ANOMALY_RATE = 35999.0502909

/** Moon argument of latitude at J2000.0 epoch (degrees). */
internal const val MOON_ARG_LATITUDE_J2000 = 93.2720950

/** Moon argument of latitude rate (degrees per Julian century). */
internal const val MOON_ARG_LATITUDE_RATE = 483202.0175233

/** Mean obliquity coefficients for equatorial conversion (arcseconds). */
internal const val MEAN_OBLIQUITY_COEFF0 = 21.448
internal const val MEAN_OBLIQUITY_COEFF1 = 46.815
internal const val MEAN_OBLIQUITY_COEFF2 = 0.00059
internal const val MEAN_OBLIQUITY_COEFF3 = 0.001813

/** Sun mean longitude at J2000.0 epoch (degrees). */
internal const val SUN_MEAN_LONGITUDE_J2000 = 280.4665

/** Sun mean longitude rate (degrees per Julian century). */
internal const val SUN_MEAN_LONGITUDE_RATE = 36000.7698

/** J2000.0 epoch Julian Day number. */
internal const val J2000_EPOCH_JD = 2451545.0

/** Days per Julian century. */
internal const val DAYS_PER_JULIAN_CENTURY = 36525.0

// ============================================================================
// RENDERING CONSTANTS
// ============================================================================

/** Camera distance factor for perspective projection. */
internal const val CAMERA_DISTANCE_FACTOR = 1.6f

/** Seed for deterministic starfield generation. */
internal const val STARFIELD_SEED = 0x6D2B79F5

/** Edge feathering width for smooth sphere edges (fraction of radius). */
internal const val EDGE_FEATHER_WIDTH = 0.012f

/** Minimum star count in starfield. */
internal const val MIN_STAR_COUNT = 90

/** Maximum star count in starfield. */
internal const val MAX_STAR_COUNT = 2200

/** Pixels per star (divisor for calculating star count). */
internal const val PIXELS_PER_STAR = 700

// ============================================================================
// LIGHTING CONSTANTS
// ============================================================================

/** Default ambient light intensity. */
internal const val DEFAULT_AMBIENT = 0.18f

/** Default diffuse light strength. */
internal const val DEFAULT_DIFFUSE_STRENGTH = 0.92f

/** Default atmosphere rim glow strength. */
internal const val DEFAULT_ATMOSPHERE_STRENGTH = 0.22f

/** Earth specular highlight strength. */
internal const val EARTH_SPECULAR_STRENGTH = 0.18f

/** Earth specular exponent (shininess). */
internal const val EARTH_SPECULAR_EXPONENT = 128

/** Moon ambient light (darker than Earth). */
internal const val MOON_AMBIENT = 0.04f

/** Moon diffuse light strength. */
internal const val MOON_DIFFUSE_STRENGTH = 0.96f

/** Shadow transition start (cosine of angle). */
internal const val SHADOW_EDGE_START = -0.15f

/** Shadow transition end (cosine of angle). */
internal const val SHADOW_EDGE_END = 0.1f

/** Earth umbra radius as fraction of Earth-Moon distance (real-world ~0.72 / 60). */
internal const val EARTH_UMBRA_DISTANCE_RATIO = 0.01197f

/** Earth penumbra radius as fraction of Earth-Moon distance (real-world ~1.28 / 60). */
internal const val EARTH_PENUMBRA_DISTANCE_RATIO = 0.02119f

// ============================================================================
// VISUAL CONSTANTS
// ============================================================================

/** Earth visual size as fraction of output. */
internal const val EARTH_SIZE_FRACTION = 0.40f

/** Minimum sphere size in pixels. */
internal const val MIN_SPHERE_SIZE_PX = 8

/** Orbit line alpha when in front of Earth. */
internal const val ORBIT_ALPHA_FRONT = 0xC8

/** Orbit line alpha when behind Earth. */
internal const val ORBIT_ALPHA_BACK = 0x6C

/** Orbit glow intensity multiplier. */
internal const val ORBIT_GLOW_INTENSITY = 0.42f

/** Ghost outline alpha for fully invisible Moon (new moon / eclipse). */
internal const val GHOST_MOON_OUTLINE_ALPHA = 0x54

/** Ghost outline RGB color for fully invisible Moon. */
internal const val GHOST_MOON_OUTLINE_RGB = 0x00C8C8C8

/** Marker radius as fraction of sphere size. */
internal const val MARKER_RADIUS_FRACTION = 0.017f

/** Minimum marker radius in pixels. */
internal const val MIN_MARKER_RADIUS_PX = 2f

/** Marker outline additional radius. */
internal const val MARKER_OUTLINE_EXTRA_PX = 1.6f

/** Marker fill color (Material Red 600). */
internal const val MARKER_FILL_COLOR = 0xFFE53935.toInt()

/** Marker outline color (White). */
internal const val MARKER_OUTLINE_COLOR = 0xFFFFFFFF.toInt()

/** Transparent black color. */
internal const val TRANSPARENT_BLACK = 0x00000000

/** Opaque black color. */
internal const val OPAQUE_BLACK = 0xFF000000.toInt()

/** Orbit line base color (white without alpha). */
internal const val ORBIT_COLOR_RGB = 0x00FFFFFF

// ============================================================================
// KIDDUSH LEVANA VISUAL CONSTANTS
// ============================================================================

/** Kiddush Levana arc color (golden/amber without alpha). */
internal const val KIDDUSH_LEVANA_COLOR_RGB = 0x00FFD700

/** Kiddush Levana arc alpha when in front of Earth. */
internal const val KIDDUSH_LEVANA_ALPHA_FRONT = 0xFF

/** Kiddush Levana arc alpha when behind Earth. */
internal const val KIDDUSH_LEVANA_ALPHA_BACK = 0xA0

/** Kiddush Levana glow intensity multiplier. */
internal const val KIDDUSH_LEVANA_GLOW_INTENSITY = 0.55f
