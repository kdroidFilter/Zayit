package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.compose.FilamentSceneView
import io.github.erkko68.filament.compose.scene.Direction
import io.github.erkko68.filament.compose.scene.DirectionalLight
import io.github.erkko68.filament.compose.scene.GltfInstance
import io.github.erkko68.filament.compose.scene.LightIntensity
import io.github.erkko68.filament.compose.scene.Position
import io.github.erkko68.filament.compose.scene.Projection
import io.github.erkko68.filament.compose.scene.Rotation
import io.github.erkko68.filament.compose.scene.Scale
import io.github.erkko68.filament.compose.scene.SphericalHarmonics
import io.github.erkko68.filament.compose.scene.Vignette
import io.github.erkko68.filament.compose.scene.primitives.Sphere
import io.github.erkko68.filament.compose.scene.rememberCameraState
import io.github.erkko68.filament.compose.scene.rememberEmissiveMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberGltfAsset
import io.github.erkko68.filament.compose.scene.rememberIndirectLightState
import io.github.erkko68.filament.compose.scene.rememberUnlitColorMaterialInstance
import io.github.erkko68.filament.compose.scene.toLinearColor
import seforimapp.earthwidget.generated.resources.Res
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import io.github.erkko68.filament.Camera as FilamentCamera

/** A 5772 K blackbody against a D65 white (Celestia's starcolors.cpp): a warm white, not yellow. */
private const val SUN_COLOR_RGB = 0xFFF2DE

private const val GLARE_COLOR_RGB = 0xFFC870

private val GlareStops: Array<Pair<Float, Color>> =
    Array(12) { i ->
        val u = i / 11f
        // Warmer than the disc: a near-white haze at low alpha over black reads as grey, not as light
        u to Color(0xFF000000.toInt() or GLARE_COLOR_RGB).copy(alpha = (0.65f * exp(-5.2f * u)).coerceIn(0f, 1f))
    }

private const val MOON_RING_STEPS = 120
private const val MOON_RING_RADIUS = 0.6f

/** Moonlight silver-blue: the holidays already use gold. */
internal const val KIDDUSH_LEVANA_SOLAR_RGB = 0xBFD4FF

/** Holiday stroke: a little bolder than the orbit line (0.75 px), not a tube. */
private const val ARC_STROKE_RADIUS = 2.4f

/**
 * Layout of the heliocentric scene in pixels of a wide [widthPx] × [heightPx] card: the orbit spans the width and,
 * seen low over the ecliptic, flattens into an ellipse that fits the height. Not to scale: the bodies are blown up
 * so the Earth's tilt and phase read at widget size.
 */
internal class SolarGeometry(
    widthPx: Int,
    heightPx: Int,
) {
    val halfWidth = widthPx / 2f
    val halfHeight = heightPx / 2f

    // Further back than the Earth widget: less perspective, so the near side of the orbit stays in the card
    val cameraZ = widthPx * 2.4f
    val orbitRadius = widthPx * 0.36f

    // Not to scale, just to read: the Sun stays clearly the biggest body
    val sunRadius = widthPx * 0.085f

    // Small enough to leave most of a holiday's arc showing on either side of it
    val earthRadius = widthPx * 0.034f
    val moonOrbitRadius = widthPx * 0.06f
    val moonRadius = widthPx * 0.009f

    /**
     * How far the scene is lifted: seen from above, the far side of the orbit shrinks and the near side (where the
     * Earth usually is) grows, so the centred scene sat low. The camera looks this far below the Sun instead.
     */
    val liftY = widthPx * 0.024f
}

/**
 * A dated stretch of the Earth's orbit, from [startDegrees] to [endDegrees] (heliocentric longitudes): an [isArc]
 * holiday drawn as that whole piece of orbit, or a Rosh Chodesh dot at its start.
 */
@Immutable
internal data class SolarOrbitMarker(
    val startDegrees: Float,
    val endDegrees: Float,
    val colorRgb: Int,
    val isArc: Boolean,
)

/** Shortest arcs still read as a stretch, not a dot (a one-day holiday is ~1°). */
private const val MIN_ARC_DEGREES = 2.4f
private const val ARC_STEP_DEGREES = 0.25f

/** Round enough not to read as a prism once the widget is shown large. */
private const val ARC_TUBE_SIDES = 16

/** Unwrapped [start, end] of a marker's arc, widened around its middle to [MIN_ARC_DEGREES]. */
internal fun SolarOrbitMarker.arcRange(): ClosedFloatingPointRange<Float> {
    val span = (endDegrees - startDegrees).mod(360f)
    val pad = ((MIN_ARC_DEGREES - span) / 2f).coerceAtLeast(0f)
    return (startDegrees - pad)..(startDegrees + span + pad)
}

/** Middle of a marker's stretch, where its hit target and label go. */
internal fun SolarOrbitMarker.middleDegrees(): Float = startDegrees + (endDegrees - startDegrees).mod(360f) / 2f

/**
 * The Sun–Earth–Moon system in the same ecliptic frame as [SceneFrame] (+Y = north ecliptic pole), seen from a
 * camera at [viewAzimuthDegrees] around the pole and [viewElevationDegrees] over the ecliptic.
 */
@Immutable
internal data class SolarRenderState(
    val widthPx: Int,
    val heightPx: Int,
    /** Heliocentric ecliptic longitude of the Earth (the Sun's geocentric one + 180°). */
    val earthLongitudeDegrees: Float,
    val siderealDegrees: Float,
    val obliquityDegrees: Float,
    /** Geocentric ecliptic position of the Moon. */
    val moonLongitudeDegrees: Float,
    val moonLatitudeDegrees: Float,
    /** Carrington rotation of the Sun on the date (sidereal period 25.38 days). */
    val sunRotationDegrees: Float = 0f,
    val viewAzimuthDegrees: Float,
    val viewElevationDegrees: Float,
    val viewZoom: Float,
    /** Full-window rendering: holidays as a plain colouring of the orbit line, vignette. */
    val detailed: Boolean = false,
    val markers: List<SolarOrbitMarker>,
    /** Kiddush Levana window as the Moon's geocentric longitudes at its start and end, drawn on its orbit. */
    val kiddushLevanaStartDegrees: Float? = null,
    val kiddushLevanaEndDegrees: Float? = null,
)

internal fun SolarRenderState.view(): Rotation =
    Rotation.axisAngle(AxisX, viewElevationDegrees) * Rotation.axisAngle(Direction.Up, viewAzimuthDegrees)

/** Camera azimuth that brings ecliptic longitude [longitudeDegrees] in front of the viewer. */
internal fun azimuthFacingLongitude(longitudeDegrees: Float): Float =
    eclipticDirection(longitudeDegrees).let { atan2(-it.x, it.z) * RAD_TO_DEG_F }

@Composable
internal fun SolarSystemSceneView(
    state: SolarRenderState,
    engine: Engine,
    modifier: Modifier = Modifier,
    /**
     * Whether the Sun's glare breathes: only while the widget is in use, or its endless animation keeps the window
     * redrawing (and the GPU busy) on an idle page.
     */
    animated: Boolean = true,
) {
    val geometry = remember(state.widthPx, state.heightPx) { SolarGeometry(state.widthPx, state.heightPx) }
    val camera =
        rememberCameraState(
            initialEye = Position(0f, -geometry.liftY, geometry.cameraZ),
            initialExposure = UnitExposure,
        )
    SideEffect {
        camera.eye = Position(0f, -geometry.liftY, geometry.cameraZ)
        camera.target = Position(0f, -geometry.liftY, 0f)
        camera.projection =
            Projection.Perspective(
                // Horizontal: the card's width spans halfWidth each side at z = 0, like perspectiveScale()
                fovDegrees = 2.0 * atan(geometry.halfWidth / (geometry.cameraZ * state.viewZoom).toDouble()) * 180.0 / PI,
                fovDirection = FilamentCamera.Fov.HORIZONTAL,
                near = geometry.cameraZ * 0.25,
                far = geometry.cameraZ * 3.0,
            )
    }
    val ambient =
        rememberIndirectLightState(
            initialIrradianceSh = SphericalHarmonics(1, floatArrayOf(1f, 1f, 1f)),
            // Brighter than the Earth widget's: at this size the night side must still read as a globe
            initialIntensity = EARTH_NIGHT_AMBIENT * 3.5f * IBL_PER_AMBIENT_UNIT,
        )
    val view = state.view()
    val earthDir = eclipticDirection(state.earthLongitudeDegrees)
    val earthPos = view * earthDir * geometry.orbitRadius
    val moonDir = eclipticDirection(state.moonLongitudeDegrees, state.moonLatitudeDegrees)
    val moonPos = earthPos + view * moonDir * geometry.moonOrbitRadius
    // ponytail: rebuilt when the camera moves; ~2k vertices for the orbit plus the arcs, cheap so far.
    val orbitMeshes =
        remember(view, geometry) {
            OrbitMeshes.build(geometry.orbitRadius, null, null) { deg -> view * eclipticDirection(deg) * geometry.orbitRadius }
        }
    // Rosh Chodesh: a short graduation across the orbit, like the month ticks of a dial
    // The Moon's orbit round the Earth (a thin ring) and the stretch of it the Moon crosses during Kiddush Levana
    // Built round the origin and moved onto the Earth by its node, their cross-sections turned from the Sun as when
    // they were built in place: the Earth's place to a quarter degree (4 times a day) rebuilds them, not each frame of
    // a play; a camera move or another month does too
    val ringEarthStep = (state.earthLongitudeDegrees * 4f).roundToInt()
    val moonRing =
        remember(view, geometry, ringEarthStep, state.kiddushLevanaStartDegrees, state.kiddushLevanaEndDegrees) {
            val sunToEarth = view * eclipticDirection(ringEarthStep / 4f) * geometry.orbitRadius
            val fromSun = Vec3f(sunToEarth.x, sunToEarth.y, sunToEarth.z)

            fun ringPoint(longitude: Float): MoonOrbitPosition {
                val p = view * eclipticDirection(longitude) * geometry.moonOrbitRadius
                return MoonOrbitPosition(x = p.x, yCam = p.y, zCam = p.z)
            }
            val ring =
                tubeMesh((0..MOON_RING_STEPS).map { ringPoint(it * 360f / MOON_RING_STEPS) }, MOON_RING_RADIUS, normalOrigin = fromSun)
            val kl =
                state.kiddushLevanaStartDegrees?.let { start ->
                    val span = ((state.kiddushLevanaEndDegrees ?: start) - start).mod(360f)
                    val steps = (span / 2f).toInt().coerceAtLeast(2)
                    tubeMesh((0..steps).map { ringPoint(start + span * it / steps) }, ARC_STROKE_RADIUS, normalOrigin = fromSun)
                }
            ring to kl
        }
    val ticks =
        remember(view, geometry, state.markers) {
            val up = view * Direction.Up
            state.markers.filter { !it.isArc }.map { marker ->
                val d = eclipticDirection(marker.startDegrees)
                val inner = view * d * (geometry.orbitRadius * (1f - TICK_HALF_LENGTH))
                val outer = view * d * (geometry.orbitRadius * (1f + TICK_HALF_LENGTH))
                val depth = orbitDepth((view * d).z * geometry.orbitRadius, geometry.orbitRadius)
                tickMesh(inner, outer, up, ARC_STROKE_RADIUS * 0.7f) to dimmed(marker.colorRgb, depthAlpha(depth, 0x60, 0xE0))
            }
        }
    // Each holiday arc as a thicker tube over its stretch of orbit, faded by the depth of its middle
    val arcs =
        remember(view, geometry, state.markers) {
            state.markers.filter { it.isArc }.map { marker ->
                val range = marker.arcRange()
                val steps = ((range.endInclusive - range.start) / ARC_STEP_DEGREES).toInt().coerceAtLeast(1)
                val path =
                    (0..steps).map { i ->
                        val p =
                            view * eclipticDirection(range.start + (range.endInclusive - range.start) * i / steps) * geometry.orbitRadius
                        MoonOrbitPosition(x = p.x, yCam = p.y, zCam = p.z)
                    }
                val middle = view * eclipticDirection(marker.middleDegrees()) * geometry.orbitRadius
                // Quantised to the orbit's depth bands, so a drag rarely recreates materials
                val band = (orbitDepth(middle.z, geometry.orbitRadius) * ORBIT_DEPTH_BANDS).toInt().coerceIn(0, ORBIT_DEPTH_BANDS - 1)
                val alpha = depthAlpha((band + 0.5f) / ORBIT_DEPTH_BANDS, 0xB0, 0xFF)
                // The orbit line itself, just coloured over the holiday — a stroke, not a tube
                tubeMesh(path, ARC_STROKE_RADIUS, sides = ARC_TUBE_SIDES) to dimmed(marker.colorRgb, alpha)
            }
        }

    Box(modifier) {
        Starfield(Modifier.matchParentSize())
        // NASA's Sun model (science.nasa.gov, public domain): a spherified cube with an emissive photosphere texture
        val sunModel = rememberGltfAsset(engine = engine) { Res.readBytes("files/sun.glb") }
        val earthModel = rememberEarthModel(engine)
        val moonModel = rememberMoonModel(engine)
        // A model only shows once its textures are uploaded: until then the Sun's stand-in below does
        val sunReady = sunModel?.isReady == true
        val earthReady = earthModel?.isReady == true
        val moonReady = moonModel?.isReady == true
        val sunCenter = Offset(geometry.halfWidth, geometry.halfHeight - geometry.liftY * state.viewZoom)
        // Sun animation clock (0..1 over SUN_ANIMATION_MS), read only while drawing: no recomposition per frame.
        // Not composed at all while idle: an infinite transition ticks the frame clock as long as it exists.
        val sunClock: androidx.compose.runtime.State<Float> =
            if (animated) {
                rememberInfiniteTransition(label = "sun").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(SUN_ANIMATION_MS, easing = LinearEasing)),
                    label = "sunClock",
                )
            } else {
                remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            }
        val sunScreenRadius = geometry.sunRadius * state.viewZoom
        // Behind the 3D view (Celestia's approach, render.cpp / pointstarrenderer): an additive glare sprite,
        // exponential 0.65·exp(−5.2·u) out to ~7.5 disc radii, tinted by the star colour
        Canvas(Modifier.matchParentSize()) {
            // The glare breathes a few percent, like the usual animated Sun
            val breath = 1f + 0.035f * sin(sunClock.value * 2f * PI.toFloat() * CORONA_PULSES)
            val glareRadius = sunScreenRadius * breath * if (state.detailed) 7.5f else 4.5f
            drawCircle(
                brush = Brush.radialGradient(colorStops = GlareStops, center = sunCenter, radius = glareRadius),
                radius = glareRadius,
                center = sunCenter,
                blendMode = BlendMode.Plus,
            )
        }
        // The glare above animates on its own Canvas; the 3D image only changes with the scene
        val rendering = rememberRenderOnChange(listOf(state, sunReady, earthReady, moonReady))
        FilamentSceneView(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            cameraState = camera,
            indirectLightState = ambient,
            postProcessing =
                widgetPostProcessing.copy(
                    vignette = if (state.detailed) Vignette(midPoint = 0.55f, roundness = 0.6f, feather = 0.7f) else null,
                ),
            shadows = null,
            transparent = true,
            renderingEnabled = rendering,
        ) {
            // Sunlight travels from the Sun (origin) out to the Earth; the Moon is close enough to share it.
            DirectionalLight(
                direction = view * earthDir,
                // A touch stronger than the Earth widget's: the globe is small here
                intensity = LightIntensity.LuminousPower(DEFAULT_DIFFUSE_STRENGTH * 1.2f * LUX_PER_DIFFUSE_UNIT),
            )
            if (sunModel != null && sunReady) {
                GltfInstance(
                    asset = sunModel,
                    rotation = view * Rotation.axisAngle(Direction.Up, state.sunRotationDegrees),
                    scale = Scale(geometry.sunRadius / SUN_MODEL_RADIUS),
                    castShadows = false,
                    receiveShadows = false,
                )
            } else {
                // While the model loads, and until its textures are up
                Sphere(
                    // Emissive at 1.2 undoes the exposure's 1/1.2 (see UnitExposure): full brightness
                    material =
                        rememberEmissiveMaterialInstance(
                            Color(0xFF000000.toInt() or SUN_COLOR_RGB).toLinearColor(),
                            intensity = 1.2f,
                        ),
                    radius = geometry.sunRadius,
                    castShadows = false,
                    receiveShadows = false,
                )
            }
            if (earthModel != null && earthReady) {
                Earth(
                    earthModel,
                    geometry.earthRadius,
                    rotation = view * earthToWorld(state.siderealDegrees, state.obliquityDegrees),
                    position = Position(earthPos.x, earthPos.y, earthPos.z),
                )
            }
            if (moonModel != null && moonReady) {
                Moon(
                    moonModel,
                    geometry.moonRadius,
                    position = Position(moonPos.x, moonPos.y, moonPos.z),
                    // Tidally locked: the near side (body +Z) faces the Earth.
                    rotation = view * Rotation.axisAngle(Direction.Up, atan2(-moonDir.x, -moonDir.z) * RAD_TO_DEG_F),
                )
            }
            Orbit(orbitMeshes, 0xFFFFFF)
            // The full window only: on the Home card the ring and arc would crowd the small Earth
            val earthAt = Position(earthPos.x, earthPos.y, earthPos.z)
            if (state.detailed) {
                moonRing.first?.let { MeshNode(rememberUnlitColorMaterialInstance(dimmed(0xFFFFFF, 0x55)), it, 1f, position = earthAt) }
            }
            if (state.detailed) {
                moonRing.second?.let {
                    MeshNode(
                        rememberUnlitColorMaterialInstance(dimmed(KIDDUSH_LEVANA_SOLAR_RGB, 0xFF)),
                        it,
                        1f,
                        position = earthAt,
                    )
                }
            }
            arcs.forEachIndexed { i, (mesh, color) ->
                key(i) { mesh?.let { MeshNode(rememberUnlitColorMaterialInstance(color), it, 1f) } }
            }
            ticks.forEachIndexed { i, (mesh, color) ->
                key("tick", i) { MeshNode(rememberUnlitColorMaterialInstance(color), mesh, 1f) }
            }
        }
    }
}

/** Screen position (render px) of a point of the Earth's orbit, for label overlays. */
internal fun solarOrbitScreenPosition(
    state: SolarRenderState,
    longitudeDegrees: Float,
): OrbitScreenPosition {
    val geometry = SolarGeometry(state.widthPx, state.heightPx)
    val p = state.view() * eclipticDirection(longitudeDegrees) * geometry.orbitRadius
    val scale = perspectiveScale(geometry.cameraZ, p.z) * state.viewZoom
    val x = geometry.halfWidth + p.x * scale
    val y = geometry.halfHeight - (p.y + geometry.liftY) * scale
    return OrbitScreenPosition(
        x = x,
        y = y,
        zCam = p.z,
        depth = orbitDepth(p.z, geometry.orbitRadius),
        hiddenByEarth =
            p.z < 0f &&
                hypot(x - geometry.halfWidth, y - (geometry.halfHeight - geometry.liftY * state.viewZoom)) <
                geometry.sunRadius * state.viewZoom,
    )
}

/** Ecliptic (longitude, latitude) → world direction. */
internal fun eclipticDirection(
    longitudeDegrees: Float,
    latitudeDegrees: Float,
): Direction {
    val l = longitudeDegrees * DEG_TO_RAD_F
    val b = latitudeDegrees * DEG_TO_RAD_F
    return Direction(cos(b) * cos(l), sin(b), -cos(b) * sin(l))
}

private const val SUN_ANIMATION_MS = 90_000

/** Glare breaths per animation turn (~6 s each). */
private const val CORONA_PULSES = 15

/** Radius of NASA's sun.glb: its ±0.5 cube, spherified, under the node's ×1000 scale. */
private const val SUN_MODEL_RADIUS = 500f

/** Half the length of a Rosh Chodesh tick, as a fraction of the orbit radius. */
private const val TICK_HALF_LENGTH = 0.035f
private const val TICK_SIDES = 8

/** A thin cylinder from [a] to [b]; [up] (not along the segment) orients its cross-section. */
private fun tickMesh(
    a: Direction,
    b: Direction,
    up: Direction,
    radius: Float,
): MeshArrays {
    val t = (b - a).normalized()
    val w = Direction(t.y * up.z - t.z * up.y, t.z * up.x - t.x * up.z, t.x * up.y - t.y * up.x).normalized()
    val u = Direction(w.y * t.z - w.z * t.y, w.z * t.x - w.x * t.z, w.x * t.y - w.y * t.x)
    val positions = FloatArray(2 * TICK_SIDES * 3)
    val normals = FloatArray(positions.size)
    for (end in 0..1) {
        val c = if (end == 0) a else b
        for (k in 0 until TICK_SIDES) {
            val angle = k * 2f * PI.toFloat() / TICK_SIDES
            val n = u * cos(angle) + w * sin(angle)
            val i = (end * TICK_SIDES + k) * 3
            positions[i] = c.x + n.x * radius
            positions[i + 1] = c.y + n.y * radius
            positions[i + 2] = c.z + n.z * radius
            normals[i] = n.x
            normals[i + 1] = n.y
            normals[i + 2] = n.z
        }
    }
    val indices =
        IntArray(TICK_SIDES * 6).also { idx ->
            for (k in 0 until TICK_SIDES) {
                val k1 = (k + 1) % TICK_SIDES
                val o = k * 6
                idx[o] = k
                idx[o + 1] = k1
                idx[o + 2] = TICK_SIDES + k
                idx[o + 3] = k1
                idx[o + 4] = TICK_SIDES + k1
                idx[o + 5] = TICK_SIDES + k
            }
        }
    return MeshArrays(positions, normals, FloatArray(2 * TICK_SIDES * 2), indices)
}
