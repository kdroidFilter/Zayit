package io.github.kdroidfilter.seforimapp.earthwidget

import kotlin.math.PI

// ============================================================================
// VECTOR OPERATIONS
// ============================================================================

/** Small value for floating-point comparisons. */
internal const val EPSILON = 1e-6f

/** Computes the cross product of two vectors. */
internal fun cross(a: Vec3f, b: Vec3f): Vec3f = Vec3f(
    x = a.y * b.z - a.z * b.y,
    y = a.z * b.x - a.x * b.z,
    z = a.x * b.y - a.y * b.x,
)

/** Computes the dot product of two vectors. */
internal fun dot(a: Vec3f, b: Vec3f): Float = a.x * b.x + a.y * b.y + a.z * b.z

/** Projects [target] onto the plane perpendicular to [viewDir] and normalizes the result. */
internal fun projectOntoViewPlane(viewDir: Vec3f, target: Vec3f?): Vec3f? {
    if (target == null) return null
    val projection = Vec3f(
        target.x - viewDir.x * dot(viewDir, target),
        target.y - viewDir.y * dot(viewDir, target),
        target.z - viewDir.z * dot(viewDir, target),
    )
    val len = projection.length()
    if (len <= EPSILON) return null
    val inv = 1f / len
    return Vec3f(projection.x * inv, projection.y * inv, projection.z * inv)
}

// ============================================================================
// ANGLE UTILITIES
// ============================================================================

/**
 * Normalizes an angle to the range [0, 360).
 */
internal fun normalizeAngleDeg(degrees: Double): Double {
    val result = degrees % 360.0
    return if (result < 0) result + 360.0 else result
}

internal fun normalizeRad(angle: Double): Double {
    val twoPi = 2 * PI
    val mod = angle % twoPi
    return if (mod < 0) mod + twoPi else mod
}

// ============================================================================
// MATH UTILITIES
// ============================================================================

/**
 * Fast integer power function using binary exponentiation.
 */
internal fun powInt(base: Float, exponent: Int): Float {
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
internal fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Rotates a vector towards an "up" direction by the provided cos/sin.
 */
internal fun rotateTowards(view: Vec3f, up: Vec3f, cosT: Float, sinT: Float): Vec3f {
    return Vec3f(
        view.x * cosT + up.x * sinT,
        view.y * cosT + up.y * sinT,
        view.z * cosT + up.z * sinT,
    ).normalized()
}
