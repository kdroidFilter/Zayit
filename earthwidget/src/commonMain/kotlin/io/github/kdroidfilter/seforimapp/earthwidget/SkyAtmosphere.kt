package io.github.kdroidfilter.seforimapp.earthwidget

import io.github.erkko68.filament.compose.scene.Direction
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Single-scattering atmosphere: the ray march of wwwtyro/glsl-atmosphere (public domain) with the Earth constants
 * and ozone layer of Sébastien Hillaire's sky (sebh/UnrealEngineSkyAtmosphere, MIT), in km. The sky shader runs the
 * same code per pixel; this CPU copy gives the few values the rest of the view needs each frame (eye adaptation,
 * the Sun's colour through the air, the horizon haze).
 */

internal const val ATMOS_R_PLANET = 6360.0
internal const val ATMOS_R_TOP = 6460.0
internal const val ATMOS_OBSERVER_KM = 0.2
internal const val ATMOS_SUN = 22.0

private val BETA_R = doubleArrayOf(0.005802, 0.013558, 0.033100)
private const val BETA_M_SCATTER = 0.003996
private const val BETA_M_EXTINCTION = 0.004440
private val BETA_OZONE = doubleArrayOf(0.000650, 0.001881, 0.000085)
private const val H_R = 8.0
private const val H_M = 1.2
private const val MIE_G = 0.8
private const val PRIMARY_STEPS = 12
private const val SECONDARY_STEPS = 6

/** Nightglow and starlight: the sky never quite reaches black. */
internal val NIGHT_FLOOR = doubleArrayOf(0.6e-6, 0.8e-6, 1.3e-6)

/** Far intersection distance of a ray starting inside a sphere of radius [r] centred at the origin. */
private fun far(
    ox: Double,
    oy: Double,
    oz: Double,
    dx: Double,
    dy: Double,
    dz: Double,
    r: Double,
): Double {
    val b = ox * dx + oy * dy + oz * dz
    val c = ox * ox + oy * oy + oz * oz - r * r
    return -b + sqrt(max(b * b - c, 0.0))
}

/** Whether the ray from inside the atmosphere hits the planet (the Earth's shadow). */
private fun hitsPlanet(
    ox: Double,
    oy: Double,
    oz: Double,
    dx: Double,
    dy: Double,
    dz: Double,
): Boolean {
    val b = ox * dx + oy * dy + oz * dz
    val c = ox * ox + oy * oy + oz * oz - ATMOS_R_PLANET * ATMOS_R_PLANET
    return b < 0 && b * b - c > 0
}

private fun ozone(h: Double) = max(0.0, 1.0 - abs(h - 25.0) / 15.0)

/** Scattered radiance from direction [d] (unit, y up) with the Sun toward [s]. */
internal fun skyRadiance(
    d: Direction,
    s: Direction,
    sun: Double = ATMOS_SUN,
): DoubleArray {
    val dx = d.x.toDouble()
    val dy = max(d.y.toDouble(), 0.0005)
    val dz = d.z.toDouble()
    val sx = s.x.toDouble()
    val sy = s.y.toDouble()
    val sz = s.z.toDouble()
    val oy = ATMOS_R_PLANET + ATMOS_OBSERVER_KM
    val length = far(0.0, oy, 0.0, dx, dy, dz, ATMOS_R_TOP)
    val mu = dx * sx + dy * sy + dz * sz
    // Aureole capped inside 25° of the Sun, as in the shader
    val muM = minOf(mu, 0.906)
    val phaseR = 3.0 / (16.0 * PI) * (1.0 + mu * mu)
    val gg = MIE_G * MIE_G
    val phaseM = 3.0 / (8.0 * PI) * ((1.0 - gg) * (muM * muM + 1.0)) / ((1.0 + gg - 2.0 * muM * MIE_G).pow(1.5) * (2.0 + gg))
    var odR = 0.0
    var odM = 0.0
    var odO = 0.0
    val totalR = DoubleArray(3)
    val totalM = DoubleArray(3)
    for (i in 0 until PRIMARY_STEPS) {
        // Samples bunched near the observer (t = L u²), where the air is dense: a low ray crosses ~1000 km
        val u = (i + 0.5) / PRIMARY_STEPS
        val t = length * u * u
        val step = length * 2.0 * u / PRIMARY_STEPS
        val px = dx * t
        val py = oy + dy * t
        val pz = dz * t
        val h = sqrt(px * px + py * py + pz * pz) - ATMOS_R_PLANET
        val stepR = exp(-h / H_R) * step
        val stepM = exp(-h / H_M) * step
        odR += stepR
        odM += stepM
        odO += ozone(h) * step
        if (hitsPlanet(px, py, pz, sx, sy, sz)) continue
        val sunLength = far(px, py, pz, sx, sy, sz, ATMOS_R_TOP)
        val sunStep = sunLength / SECONDARY_STEPS
        var sR = 0.0
        var sM = 0.0
        var sO = 0.0
        for (j in 0 until SECONDARY_STEPS) {
            val u = (j + 0.5) * sunStep
            val qx = px + sx * u
            val qy = py + sy * u
            val qz = pz + sz * u
            val hq = sqrt(qx * qx + qy * qy + qz * qz) - ATMOS_R_PLANET
            sR += exp(-hq / H_R) * sunStep
            sM += exp(-hq / H_M) * sunStep
            sO += ozone(hq) * sunStep
        }
        for (c in 0..2) {
            val attenuation =
                exp(-(BETA_R[c] * (odR + sR) + BETA_M_EXTINCTION * (odM + sM) + BETA_OZONE[c] * (odO + sO)))
            totalR[c] += stepR * attenuation
            totalM[c] += stepM * attenuation
        }
    }
    val twilight = twilightGlow(sy, dx, dy, dz, sx, sz)
    return DoubleArray(3) { c -> sun * (phaseR * BETA_R[c] * totalR[c] + phaseM * BETA_M_SCATTER * totalM[c]) + twilight[c] }
}

private val TWILIGHT_TINT = doubleArrayOf(0.35, 0.60, 1.60)
private val AFTERGLOW_TINT = doubleArrayOf(1.30, 0.62, 0.28)

/**
 * Multiple scattering, which single scattering misses and which carries twilight: once the Sun is a few degrees
 * down, the sky is lit by light already scattered once. Fitted to measured twilight zenith luminances (about ×13
 * dimmer for every 3° of solar depression), brighter low toward the Sun.
 */
internal fun twilightGlow(
    sunY: Double,
    dx: Double,
    dy: Double,
    dz: Double,
    sx: Double,
    sz: Double,
): DoubleArray {
    val el = Math.toDegrees(kotlin.math.asin(sunY.coerceIn(-1.0, 1.0)))
    if (el > 4.0 || el < -24.0) return DoubleArray(3)
    val fade = ((4.0 - el) / 6.0).coerceIn(0.0, 1.0)
    val level = 2.0e-4 * exp(0.855 * (minOf(el, 0.0) + 6.0)) * fade
    val hs = sqrt(sx * sx + sz * sz).coerceAtLeast(1e-6)
    val hd = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6)
    val toward = ((dx * sx + dz * sz) / (hs * hd)) * 0.5 + 0.5
    // Blue overhead; the afterglow low on the Sun's side is orange
    // ...which fades as the Sun sinks: bright just after shkia, a last red line by tzeis
    val glow = 3.0 * toward * toward * exp(-max(dy, 0.0) * 6.0) * ((1.0 - (-el - 3.0) / 5.0).coerceIn(0.08, 1.0))
    return DoubleArray(3) { level * (2.0 * TWILIGHT_TINT[it] + glow * AFTERGLOW_TINT[it]) }
}

/** Share of sunlight reaching the ground from direction [d]: the Sun's colour through the air. */
internal fun skyTransmittance(d: Direction): DoubleArray {
    val dx = d.x.toDouble()
    val dy = d.y.toDouble()
    val dz = d.z.toDouble()
    val oy = ATMOS_R_PLANET + ATMOS_OBSERVER_KM
    if (hitsPlanet(0.0, oy, 0.0, dx, dy, dz)) return DoubleArray(3)
    val length = far(0.0, oy, 0.0, dx, dy, dz, ATMOS_R_TOP)
    val step = length / (PRIMARY_STEPS * 2)
    var odR = 0.0
    var odM = 0.0
    var odO = 0.0
    for (i in 0 until PRIMARY_STEPS * 2) {
        val t = (i + 0.5) * step
        val px = dx * t
        val py = oy + dy * t
        val pz = dz * t
        val h = sqrt(px * px + py * py + pz * pz) - ATMOS_R_PLANET
        odR += exp(-h / H_R) * step
        odM += exp(-h / H_M) * step
        odO += ozone(h) * step
    }
    return DoubleArray(3) { c -> exp(-(BETA_R[c] * odR + BETA_M_EXTINCTION * odM + BETA_OZONE[c] * odO)) }
}

/** Relative luminance of a radiance. */
internal fun luminance(rgb: DoubleArray) = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

/**
 * What the eye adapts to: mostly the brightest part of the sky, partly the zenith, so the sky overhead stays blue
 * at dusk; ever more the glow in the west as the Sun sinks (3° → 9° under), so by tzeis that glow reads dim.
 */
internal fun adaptationLuminance(
    zenith: Double,
    brightest: Double,
    sunElevationDeg: Double,
): Double {
    val b = maxOf(brightest, zenith) + 1e-12
    val t = ((-sunElevationDeg - 3.0) / 6.0).coerceIn(0.0, 1.0)
    val w = 0.7 + 0.25 * t * t * (3 - 2 * t)
    return exp(w * ln(b) + (1 - w) * ln(zenith + 1e-12))
}

/**
 * Eye adaptation: exposure for a sky whose brightest part (the zenith, or the horizon under the Sun) has luminance
 * [brightest], as Stellarium Web adapts to its brightest sky value; not full, so twilight and night read darker.
 */
internal fun skyExposure(brightest: Double): Double {
    // Partial adaptation (power 0.7) to the brightest part of the sky: the day sky shows a mid blue under its
    // bright horizon, the glow after shkia stays bright and fades by tzeis, the night stays dark
    val l = brightest + luminance(NIGHT_FLOOR)
    return 3.6 * (1.0 / l).pow(0.7)
}

/** Scotopic (night vision) share, 0 by day, 1 in the dark: colours fade to a blue-grey. */
internal fun scotopic(zenith: Double): Double {
    // None above 1e-4 (the end of civil twilight), full below 1e-6 (the night)
    val x = ((-ln(zenith + 1e-12) / ln(10.0)) - 4.0) / 2.0
    return (x * x * (3 - 2 * x)).takeIf { x in 0.0..1.0 } ?: if (x > 1) 1.0 else 0.0
}

/** Atmospheric refraction (Saemundsson), degrees, at apparent-ish elevation [h] degrees. */
internal fun refractionDeg(h: Double): Double {
    val e = max(h, -1.5)
    return 1.02 / tan(Math.toRadians(e + 10.3 / (e + 5.11))) / 60.0
}

/** Air mass (Rozenberg) toward elevation sine [sinAlt]. */
internal fun airMass(sinAlt: Double): Double = 1.0 / (max(sinAlt, 0.0) + 0.025 * exp(-11.0 * max(sinAlt, 0.0)))

/** Star colour from its B-V index: temperature (Ballesteros), then a blackbody approximation to sRGB. */
internal fun bvToRgb(bv: Double): Int {
    val b = bv.coerceIn(-0.4, 2.0)
    val t = 4600.0 * (1.0 / (0.92 * b + 1.7) + 1.0 / (0.92 * b + 0.62))
    val k = t / 100.0
    val r = if (k <= 66) 255.0 else 329.7 * (k - 60).pow(-0.1332)
    val g = if (k <= 66) 99.47 * ln(k) - 161.12 else 288.12 * (k - 60).pow(-0.0755)
    val bl =
        if (k >= 66) {
            255.0
        } else if (k <= 19) {
            0.0
        } else {
            138.52 * ln(k - 10) - 305.04
        }

    fun c(v: Double) = v.coerceIn(0.0, 255.0).toInt()

    // Paler than the blackbody: stars look near white to the eye, only tinted
    fun pale(v: Int) = (v + (255 - v) * 0.45).toInt()
    return (pale(c(r)) shl 16) or (pale(c(g)) shl 8) or pale(c(bl))
}
