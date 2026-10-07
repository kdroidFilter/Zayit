package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.erkko68.filament.compose.scene.Direction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthwidget_sky_chatzos
import seforimapp.earthwidget.generated.resources.earthwidget_sky_sunrise
import seforimapp.earthwidget.generated.resources.earthwidget_sky_sunset
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val DEFAULT_PITCH_DEG = 12f

/** Field of view when facing the Moon: close enough for it to fill about 40% of the card's height. */
private const val MOON_FOCUS_FOV_DEG = 24f

/** Where the view looks, and how wide. */
private data class SkyAim(
    val yawDeg: Float,
    val pitchDeg: Float,
    val fovDeg: Float,
)

/** The Home card's width, which the labels are sized for. */
private const val CARD_WIDTH_DP = 265f

private val SunPathColor = Color(0xFFFFD27A)

/** Stars of the HYG catalogue (to magnitude 4): equatorial unit vectors, magnitudes, colours. */
private class StarCatalog(
    val xyz: FloatArray,
    val magnitude: FloatArray,
    val color: IntArray,
)

private fun parseStars(csv: String): StarCatalog {
    val rows =
        csv
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(',') }
            .toList()
    val xyz = FloatArray(rows.size * 3)
    val magnitude = FloatArray(rows.size)
    val color = IntArray(rows.size)
    rows.forEachIndexed { i, (ra, dec, mag, bv) ->
        val a = ra.toDouble() * 15.0 * DEG_TO_RAD
        val d = dec.toDouble() * DEG_TO_RAD
        xyz[i * 3] = (kotlin.math.cos(d) * kotlin.math.cos(a)).toFloat()
        xyz[i * 3 + 1] = (kotlin.math.cos(d) * kotlin.math.sin(a)).toFloat()
        xyz[i * 3 + 2] = kotlin.math.sin(d).toFloat()
        magnitude[i] = mag.toFloat()
        color[i] = bvToRgb(bv.toDouble())
    }
    return StarCatalog(xyz, magnitude, color)
}

/** Zenith brightness as shown under a dark night sky and a day sky. */
private const val NIGHT_SHOWN = 0.06f
private const val DAY_SHOWN = 0.35f

/** The simplified card shows only the stars that draw the familiar shapes. */
private const val CARD_FAINTEST_MAGNITUDE = 3.5f

/**
 * Stars as points of light (after Stellarium Web): the faintest that show depend on how bright the sky is,
 * and the air near the horizon dims them (extinction).
 */
private fun DrawScope.drawStars(
    catalog: StarCatalog,
    state: SkyViewState,
    camera: SkyCamera,
    light: SkyLight,
) {
    // Faintest magnitude that shows, from the sky's shown brightness: 5.5 under a dark night sky, none by day
    val shown = light.zenithShown.coerceAtLeast(NIGHT_SHOWN)
    var limit = 5.5f - 7f * kotlin.math.log10(shown / NIGHT_SHOWN) / kotlin.math.log10(DAY_SHOWN / NIGHT_SHOWN)
    limit = minOf(limit, CARD_FAINTEST_MAGNITUDE)
    if (limit < -1.5f) return
    val unit = 1.dp.toPx()
    val r = state.stars
    for (i in catalog.magnitude.indices) {
        if (catalog.magnitude[i] > limit + 0.5f) break // sorted by magnitude (the file stops at 4.0)
        val d = r * Direction(catalog.xyz[i * 3], catalog.xyz[i * 3 + 1], catalog.xyz[i * 3 + 2])
        if (d.y < -0.01f) continue
        val at = camera.project(d) ?: continue
        if (at.x < -4f || at.y < -4f || at.x > size.width + 4f || at.y > size.height + 4f) continue
        val air = airMass(d.y.toDouble()).coerceAtMost(40.0)
        val magnitude = catalog.magnitude[i] + 0.13f * (air - 1.0).toFloat()
        val excess = limit - magnitude
        if (excess < 0f) continue
        // log10 of the brightness above the limit
        val bright = excess * 0.4f
        val radius = unit * (0.6f + 0.45f * bright).coerceAtMost(3.2f)
        val alpha = (0.35f + 0.25f * bright).coerceIn(0.2f, 1f)
        val c = Color(catalog.color[i] or (0xFF shl 24))
        if (bright > 1.2f) drawCircle(c.copy(alpha = 0.10f * alpha), radius = radius * 3.2f, center = at, blendMode = BlendMode.Plus)
        drawCircle(c.copy(alpha = alpha), radius = radius, center = at, blendMode = BlendMode.Plus)
    }
}

/** The Sun's disc: coloured by the air it shines through, darker at its edge, flattened low down by refraction. */
private fun DrawScope.drawSun(
    state: SkyViewState,
    camera: SkyCamera,
    trueElevationDeg: Double,
) {
    val at = camera.project(state.sun) ?: return
    val t = skyTransmittance(state.sun)
    val top = t.max()
    if (top <= 0.0) return

    // Blinding white high up, orange to red as the air thickens toward the horizon
    fun ch(v: Double) = (1.0 - kotlin.math.exp(-v * 6.0)).toFloat()
    val c = Color(ch(t[0]), ch(t[1]), ch(t[2]))
    val radius = kotlin.math.tan(SUN_ANGULAR_RADIUS_DEG * DEG_TO_RAD_F) / camera.tanHalfV * size.height / 2f
    val half = 0.2666
    val squash =
        (
            (2 * half + refractionDeg(trueElevationDeg + half) - refractionDeg(trueElevationDeg - half)) / (2 * half)
        ).toFloat().coerceIn(0.6f, 1f)
    // Its halo: a soft glow clearly dimmer than the disc, which stays the only solid, bright part (so the Sun
    // reads the Moon's size, with light around it, rather than bigger)
    drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(
            0f to c.copy(alpha = 0.34f),
            0.25f to c.copy(alpha = 0.26f),
            0.45f to c.copy(alpha = 0.10f),
            1f to Color.Transparent,
            center = at,
            radius = radius * 4f,
        ),
        radius = radius * 4f,
        center = at,
        blendMode = BlendMode.Plus,
    )
    // Limb darkening, softened: the disc is too bright for the eye to see much of it
    val disc =
        androidx.compose.ui.graphics.Brush.radialGradient(
            0f to c,
            0.7f to c.copy(red = c.red * 0.99f, green = c.green * 0.97f, blue = c.blue * 0.95f),
            0.92f to c.copy(red = c.red * 0.94f, green = c.green * 0.89f, blue = c.blue * 0.82f),
            1f to c.copy(red = c.red * 0.86f, green = c.green * 0.78f, blue = c.blue * 0.66f),
            center = at,
            radius = radius,
        )
    // Added to the sky, like the light it is: never darker than the glare around it
    drawOval(
        disc,
        blendMode = BlendMode.Plus,
        topLeft = Offset(at.x - radius, at.y - radius * squash),
        size =
            androidx.compose.ui.geometry.Size(
                radius * 2f,
                radius * 2f * squash,
            ),
    )
}

/** What the shaders need this frame: eye adaptation, haze at the horizon, how lit the land is (Stellarium). */
private fun skyLight(
    state: SkyViewState,
    moonIllumination: Float,
): SkyLight {
    val sun = state.sun
    val moon = state.moon
    val moonUp = ((moon.y + 0.02f) / 0.07f).coerceIn(0f, 1f)
    val moonLight = (ATMOS_SUN * 2.5e-6 * moonIllumination * moonUp).toFloat()
    val zenithRadiance = skyRadiance(Direction(0f, 1f, 0f), sun)
    // Moonlit zenith, roughly the sunlit one scaled by the moonlight
    val zenith = luminance(zenithRadiance) + 0.35 * 2.5e-6 * moonIllumination * moonUp
    // The eye adapts to the brightest of the sky too (Stellarium Web's lwmax), not the zenith alone: a glow low in
    // the west darkens the rest, rather than being burnt out as bright as a sunset
    val glowRadiance = skyRadiance(skyDirection(Math.toDegrees(kotlin.math.atan2(sun.x.toDouble(), -sun.z.toDouble())), 2.0), sun)
    val adapted = adaptationLuminance(zenith, luminance(glowRadiance), Math.toDegrees(kotlin.math.asin(sun.y.toDouble())))
    val exposure = skyExposure(adapted)
    val scotopic = scotopic(zenith)
    val azSun = kotlin.math.atan2(sun.x.toDouble(), -sun.z.toDouble())

    fun haze(az: Double) = shownColor(skyRadiance(skyDirection(Math.toDegrees(az), 1.0), sun), exposure, scotopic)
    val shownZenith = shownColor(zenithRadiance, exposure, scotopic).let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue }

    // Stellarium's landscape brightness: the Sun up to 8° below the horizon, else the Moon or the town's glow
    val sinSun = kotlin.math.sin(minOf(kotlin.math.PI / 2, kotlin.math.asin(sun.y.toDouble()) + 8 * DEG_TO_RAD))
    var brightness = if (sinSun > -0.1 / 1.5) 1.5 * (sinSun + 0.1 / 1.5) else 0.0
    brightness += maxOf(0.21 * moonIllumination * moon.y.coerceAtLeast(0f), 0.05)
    brightness = brightness.coerceAtMost(0.95)
    val albedo = floatArrayOf(0.24f, 0.25f, 0.21f)
    return SkyLight(
        exposure = exposure.toFloat(),
        scotopic = scotopic.toFloat(),
        moonLight = moonLight,
        hazeSun = haze(azSun),
        hazeAnti = haze(azSun + kotlin.math.PI),
        land = Color(albedo[0] * brightness.toFloat(), albedo[1] * brightness.toFloat(), albedo[2] * brightness.toFloat()),
        zenithShown = shownZenith,
    )
}

/**
 * Centres the element on [at] (px, from the parent's top-left corner, whatever the layout direction), kept
 * inside the parent's width.
 */
private fun Modifier.centredAt(at: Offset) =
    layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            val edge = 4.dp.roundToPx()
            val x = (at.x - p.width / 2f).roundToInt().coerceIn(edge, (constraints.maxWidth - p.width - edge).coerceAtLeast(edge))
            p.place(x, (at.y - p.height / 2f).roundToInt())
        }
    }

private const val PASS_STEP_MS = 5 * 60_000L
private const val PASS_REACH_MS = 26 * 3_600_000L
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

/** How long the sky takes to glide to a new moment. */
private const val MOMENT_GLIDE_MS = 1400

/** A body's way across the sky: from where it rises, through its highest point, to where it sets. */
private class SkyPass(
    val path: List<Pair<Long, Direction>>,
    val hours: List<Direction>,
    /** Null when it never crosses the horizon within reach (near the poles). */
    val rise: Pair<Long, Direction>?,
    val peak: Pair<Long, Direction>,
    val set: Pair<Long, Direction>?,
)

/**
 * The pass of a body whose (azimuth, elevation) at a time is [at]: the one it is on at [instant], or, while it
 * is down, the next one ([upcoming]) or the last one. Rise and set are found where the elevation crosses [horizon].
 */
private fun skyPass(
    instant: Long,
    horizon: Double,
    upcoming: Boolean,
    at: (Long) -> Pair<Double, Double>,
): SkyPass? {
    fun el(t: Long) = at(t).second

    fun dir(t: Long) = at(t).let { (az, e) -> skyDirection(az, e) }
    var t = instant
    var reach = 0L
    while (el(t) < horizon) {
        t += if (upcoming) PASS_STEP_MS else -PASS_STEP_MS
        reach += PASS_STEP_MS
        if (reach > PASS_REACH_MS) return null
    }

    // Walk out to both ends, then place each end on the horizon between the last step above it and the one below
    fun end(step: Long): Long? {
        var u = t
        var walked = 0L
        while (el(u + step) >= horizon) {
            u += step
            walked += PASS_STEP_MS
            if (walked > PASS_REACH_MS) return null
        }
        val above = el(u)
        val below = el(u + step)
        return u + (step * ((above - horizon) / (above - below))).toLong()
    }
    val riseAt = end(-PASS_STEP_MS)
    val setAt = end(PASS_STEP_MS)
    val from = riseAt ?: (t - PASS_REACH_MS / 2)
    val to = setAt ?: (t + PASS_REACH_MS / 2)
    val times = (from until to step PASS_STEP_MS) + to
    val path = times.map { it to dir(it) }
    val peak = path.maxBy { it.second.y }
    val hours = ((from / HOUR_MS + 1) * HOUR_MS until to step HOUR_MS).map(::dir)
    return SkyPass(path, hours, riseAt?.let { it to dir(it) }, peak, setAt?.let { it to dir(it) })
}

/** Where a label for direction [d] goes: on it, or pinned to the edge on its side ([side] -1 left, 1 right). */
private class Spot(
    val at: Offset,
    val side: Int,
    /** How far (degrees of azimuth) the view must turn to face it. */
    val turn: Float = 0f,
    /** Held near the top while it is higher up, out of view. */
    val above: Boolean = false,
)

private class SkyLabelSpec(
    val group: Int,
    val text: String,
    val spot: Spot,
    val color: Color,
)

private val SunPathNames =
    listOf(Res.string.earthwidget_sky_sunrise, Res.string.earthwidget_sky_chatzos, Res.string.earthwidget_sky_sunset)

private fun SkyCamera.spot(
    d: Direction,
    yawDeg: Float,
    widthPx: Float,
): Spot? {
    val p = project(d)
    val az = Math.toDegrees(kotlin.math.atan2(d.x.toDouble(), -d.z.toDouble())).toFloat()
    val delta = (az - yawDeg + 540f).mod(360f) - 180f
    if (p != null && p.x in 0f..widthPx) return Spot(p, 0, kotlin.math.abs(delta))
    if (kotlin.math.abs(delta) > 150f) return null
    val side = if (delta < 0f) -1 else 1
    val elevation = Math.toDegrees(kotlin.math.asin(d.y.toDouble()))
    val y = p?.y ?: project(skyDirection((yawDeg + side * 30f).toDouble(), elevation))?.y ?: return null
    return Spot(Offset(if (side < 0) 0f else widthPx, y), side, kotlin.math.abs(delta))
}

/** A label on the sky; pinned to an edge, it points (◂ ▸) the way to turn. */
@Composable
private fun SkyLabel(
    text: String,
    spot: Spot,
    dy: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shown =
        when {
            // Hebrew runs right to left: the arrow is written last to land on the left, first for the right
            spot.side < 0 -> "$text ◂"
            spot.side > 0 -> "▸ $text"
            spot.above -> "▴ $text"
            else -> text
        }
    Text(
        shown,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f)),
        modifier = modifier.centredAt(Offset(spot.at.x, spot.at.y + dy)),
    )
}

/** The pass as a line: solid where the body has been, dashed ahead, a dot on each hour. */
private fun DrawScope.drawPass(
    pass: SkyPass,
    now: Long,
    camera: SkyCamera,
    color: Color,
) {
    val done = Path()
    val ahead = Path()
    var last: Offset? = null
    var lastDone = false
    for ((t, d) in pass.path) {
        val p = camera.project(d)
        val isDone = t <= now
        if (p != null && last != null) {
            val target = if (isDone) done else ahead
            if (target.isEmpty || isDone != lastDone) target.moveTo(last.x, last.y)
            target.lineTo(p.x, p.y)
        }
        last = p
        lastDone = isDone
    }
    drawPath(done, color.copy(alpha = 0.9f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(
        ahead,
        color.copy(alpha = 0.6f),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
    )
    pass.hours.forEach { d -> camera.project(d)?.let { drawCircle(color.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = it) } }
}

/**
 * The sky over [latitude]/[longitude] as seen from the ground, in 3D: the Sun and Moon where they really are
 * (the Moon lit by the Sun, so its phase is real), the stars turning with the night, and a sky whose colours
 * tell dawn, midday, sunset and night apart; the day's path of the Sun with its rise, highest point and set.
 * A fixed view (the Sun while any daylight is left; in full night the Moon close in if it is up), that glides along
 * when the moment changes.
 */
@Composable
fun SkyWidgetView(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    /** The moment to show (the Earth widget's); null follows now. */
    timeMillis: Long? = null,
    /** Glides to a new moment and re-aims at what is up; false follows [timeMillis] as is (a moment per frame). */
    glide: Boolean = true,
) {
    val catalog by produceState<StarCatalog?>(null) {
        value = withContext(Dispatchers.Default) { parseStars(Res.readBytes("files/stars.csv").decodeToString()) }
    }
    val moonMap by produceState<Pair<org.jetbrains.skia.Image, org.jetbrains.skia.Shader>?>(null) {
        value =
            withContext(Dispatchers.Default) {
                org.jetbrains.skia.Image
                    .makeFromEncoded(Res.readBytes("drawable/moonmap.jpg"))
                    .let { it to imageShader(it) }
            }
    }
    var liveNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000L)
            liveNow = System.currentTimeMillis()
        }
    }
    // A new moment glides there: the Sun moves along its path and the sky changes colour on the way. Across days,
    // only the time of day is run through (a day/night strobe otherwise); the live minute ticks just step.
    val target = timeMillis ?: liveNow
    var shown by remember { mutableLongStateOf(target) }
    LaunchedEffect(target) {
        val delta = target - shown
        if (!glide || kotlin.math.abs(delta) < 60_000L) {
            shown = target
            return@LaunchedEffect
        }
        val from = if (kotlin.math.abs(delta) > 30 * HOUR_MS) target - ((delta.mod(DAY_MS) + DAY_MS / 2) % DAY_MS - DAY_MS / 2) else shown
        animate(0f, 1f, animationSpec = tween(MOMENT_GLIDE_MS, easing = FastOutSlowInEasing)) { v, _ ->
            shown = from + ((target - from) * v).toLong()
        }
        shown = target
    }
    val instant = shown

    val date = Date(instant)
    val julianDay = computeJulianDayUtc(date)
    val sunPos = computeSolarPositionNoaaUtc(date, latitude, longitude)
    val sunSoon = computeSolarPositionNoaaUtc(Date(instant + 600_000L), latitude, longitude)
    val moonPos = computeMoonHorizontalPosition(julianDay, latitude, longitude)
    // Seen through the air: raised by refraction, most at the horizon
    val sun = skyDirection(sunPos.azimuthDegreesFromNorth, sunPos.elevationDegrees + refractionDeg(sunPos.elevationDegrees))
    val moon = skyDirection(moonPos.azimuthFromNorthDeg, moonPos.elevationDeg + refractionDeg(moonPos.elevationDeg))

    // Today's path of the Sun (while it or its glow is up) and the Moon's while it is up; refreshed every 10 min
    // Played frame by frame, minutes go by at each frame: by day then
    val passKey = instant / if (glide) 600_000L else DAY_MS
    val sunRising = sunSoon.elevationDegrees > sunPos.elevationDegrees
    val sunPass =
        remember(latitude, longitude, passKey, sunPos.elevationDegrees > -10.0) {
            if (sunPos.elevationDegrees <= -10.0) {
                null
            } else {
                skyPass(instant, horizon = -0.833, upcoming = sunRising) { t ->
                    computeSolarPositionNoaaUtc(Date(t), latitude, longitude).let { it.azimuthDegreesFromNorth to it.elevationDegrees }
                }
            }
        }
    val clock = remember(latitude, longitude) { SimpleDateFormat("HH:mm").apply { timeZone = timeZoneForLocation(latitude, longitude) } }

    // The Sun while any of its light is left in the sky (down to 18° under: alos, netz, shkia, tzeis all look at
    // its side); in full night the Moon if it is up, close in so its phase reads; else the south
    fun facing(
        sunPos: SolarPosition,
        moonPos: HorizontalPosition,
    ): SkyAim =
        when {
            sunPos.elevationDegrees > -18.0 -> {
                val el = sunPos.elevationDegrees.toFloat()
                SkyAim(sunPos.azimuthDegreesFromNorth.toFloat(), (el - 18f).coerceIn(DEFAULT_PITCH_DEG, 60f), SKY_VFOV_DEG)
            }
            moonPos.elevationDeg > 0.0 -> {
                // Low, it keeps a strip of skyline under it
                val el = moonPos.elevationDeg.toFloat()
                SkyAim(moonPos.azimuthFromNorthDeg.toFloat(), el.coerceAtLeast(MOON_FOCUS_FOV_DEG * 0.3f), MOON_FOCUS_FOV_DEG)
            }
            else -> SkyAim(if (latitude >= 0) 180f else 0f, DEFAULT_PITCH_DEG, SKY_VFOV_DEG)
        }

    // Re-aimed whenever the place or the moment changes
    fun facingAt(t: Long): SkyAim {
        val at = Date(t)
        return facing(
            computeSolarPositionNoaaUtc(at, latitude, longitude),
            computeMoonHorizontalPosition(computeJulianDayUtc(at), latitude, longitude),
        )
    }
    // Turned toward whatever is up at the new place or moment, gliding there with the time
    // Not glided, the view holds still: re-aimed at every frame it would swing about
    val aim = remember(latitude, longitude, if (glide) timeMillis else Unit) { facingAt(timeMillis ?: System.currentTimeMillis()) }
    var yaw by remember { mutableFloatStateOf(aim.yawDeg) }
    var pitch by remember { mutableFloatStateOf(aim.pitchDeg) }
    var fov by remember { mutableFloatStateOf(aim.fovDeg) }
    LaunchedEffect(aim) {
        val yaw0 = yaw
        val pitch0 = pitch
        val fov0 = fov
        val turn = (aim.yawDeg - yaw0 + 540f).mod(360f) - 180f
        if (kotlin.math.abs(turn) < 0.01f && kotlin.math.abs(aim.pitchDeg - pitch0) < 0.01f && aim.fovDeg == fov0) {
            return@LaunchedEffect
        }
        animate(0f, 1f, animationSpec = tween(MOMENT_GLIDE_MS, easing = FastOutSlowInEasing)) { v, _ ->
            yaw = (yaw0 + turn * v).mod(360f)
            pitch = pitch0 + (aim.pitchDeg - pitch0) * v
            fov = fov0 + (aim.fovDeg - fov0) * v
        }
    }
    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                // The shaders draw into their own layer, under the same clip wherever the card is: cut by the edge of a
                // scrolling page, they would otherwise need new GPU programs, compiled there and then (a ~100 ms frame)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        val state =
            SkyViewState(
                widthPx = widthPx,
                heightPx = heightPx,
                yawDeg = yaw,
                pitchDeg = pitch,
                fovDeg = fov,
                sun = sun,
                moon = moon,
                stars = equatorialToSky(localSiderealTimeRad(julianDay, longitude), latitude),
            )
        // Everything is drawn in this frame (no 3D image arriving frames later): nothing to hold back
        val overlay = state

        val camera = SkyCamera(overlay)
        val moonIllumination =
            (1f - (overlay.sun.x * overlay.moon.x + overlay.sun.y * overlay.moon.y + overlay.sun.z * overlay.moon.z)) / 2f
        val light = skyLight(overlay, moonIllumination)

        // Back to front: the air, the stars, the Moon, the Sun, the day's path, then the land
        // over them all, so whatever sets goes down behind the skyline
        Canvas(Modifier.fillMaxSize()) {
            drawRect(skyBrush(overlay, light))
            catalog?.let { drawStars(it, overlay, camera, light) }
            moonMap?.let { (image, shader) ->
                drawRect(moonBrush(overlay, camera, image, shader, moonIllumination), blendMode = BlendMode.Plus)
            }
            drawSun(overlay, camera, sunPos.elevationDegrees)
        }
        Canvas(Modifier.fillMaxSize()) {
            sunPass?.let { drawPass(it, instant, camera, SunPathColor) }
            drawRect(groundBrush(overlay, light))
        }

        // Labels are designed at the card's size; a larger view scales them up
        val ui = Density(density.density * sqrt(maxWidth.value / CARD_WIDTH_DP).coerceIn(1f, 1.4f), density.fontScale)
        CompositionLocalProvider(LocalDensity provides ui) {
            // Rise, highest point and set on the day's path, with their times. Out of view they pin to the edge (one
            // per edge, the nearest) with an arrow the way to turn.
            val w = widthPx.toFloat()
            val lift = with(ui) { 14.dp.toPx() }
            val top = with(ui) { 14.dp.toPx() }
            val band = heightPx - with(ui) { 12.dp.toPx() }
            val keep = with(ui) { 80.dp.toPx() }
            val labels = ArrayList<SkyLabelSpec>()
            listOfNotNull(
                sunPass?.let { it to (SunPathColor to SunPathNames) },
            ).forEach { (pass, style) ->
                listOf(pass.rise to 0, pass.peak to 1, pass.set to 2).forEach { (point, which) ->
                    point?.let { (t, d) ->
                        camera.spot(d, overlay.yawDeg, w)?.let { spot ->
                            val y = (spot.at.y + if (which == 1) -lift else lift).coerceIn(top, band - lift * 1.5f)
                            labels +=
                                SkyLabelSpec(
                                    group = 1,
                                    text = "${stringResource(style.second[which])} ${clock.format(Date(t))}",
                                    spot = Spot(Offset(spot.at.x, y), spot.side, spot.turn, above = spot.at.y + lift < top),
                                    color = style.first,
                                )
                        }
                    }
                }
            }
            labels
                .filter { label ->
                    val side = label.spot.side
                    side == 0 ||
                        // The nearest of its group on that edge, and no label in view already standing there
                        (
                            labels.none { it.group == label.group && it.spot.side == side && it.spot.turn < label.spot.turn } &&
                                labels.none {
                                    it.group == label.group && it.spot.side == 0 && kotlin.math.abs(it.spot.at.x - label.spot.at.x) < keep
                                }
                        )
                }.forEach { label ->
                    SkyLabel(label.text, label.spot, dy = 0f, color = label.color, modifier = Modifier.align(AbsoluteAlignment.TopLeft))
                }
        }
    }
}
