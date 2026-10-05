package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.compose.FilamentSceneScope
import io.github.erkko68.filament.compose.FilamentSceneView
import io.github.erkko68.filament.compose.scene.AntiAliasing
import io.github.erkko68.filament.compose.scene.ColorGrade
import io.github.erkko68.filament.compose.scene.Direction
import io.github.erkko68.filament.compose.scene.DirectionalLight
import io.github.erkko68.filament.compose.scene.Exposure
import io.github.erkko68.filament.compose.scene.GltfAsset
import io.github.erkko68.filament.compose.scene.GltfInstance
import io.github.erkko68.filament.compose.scene.LightIntensity
import io.github.erkko68.filament.compose.scene.LinearColor
import io.github.erkko68.filament.compose.scene.Position
import io.github.erkko68.filament.compose.scene.PostProcessing
import io.github.erkko68.filament.compose.scene.Projection
import io.github.erkko68.filament.compose.scene.Rotation
import io.github.erkko68.filament.compose.scene.Scale
import io.github.erkko68.filament.compose.scene.SphericalHarmonics
import io.github.erkko68.filament.compose.scene.ToneMapping
import io.github.erkko68.filament.compose.scene.primitives.Mesh
import io.github.erkko68.filament.compose.scene.primitives.Sphere
import io.github.erkko68.filament.compose.scene.rememberCameraState
import io.github.erkko68.filament.compose.scene.rememberGltfAsset
import io.github.erkko68.filament.compose.scene.rememberIndirectLightState
import io.github.erkko68.filament.compose.scene.rememberUnlitColorMaterialInstance
import io.github.erkko68.filament.compose.scene.toLinearColor
import seforimapp.earthwidget.generated.resources.Res
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Whether the 3D widget can run here: filament-kmp ships natives for these OS/arch pairs only
 * (no macOS Intel, no Windows ARM64). Callers hide the widget when false.
 */
val isEarthWidgetSupported: Boolean by lazy {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val arm =
        System
            .getProperty("os.arch")
            .orEmpty()
            .lowercase()
            .let { it == "aarch64" || it == "arm64" }
    when {
        os.contains("mac") -> arm
        os.contains("win") -> !arm
        os.contains("linux") -> true
        else -> false
    }
}

/** Rendering parameters for the Earth + Moon composite scene. [renderSizePx] sets the scene's geometry units. */
@Immutable
internal data class EarthRenderState(
    val renderSizePx: Int,
    val earthRotationDegrees: Float,
    val lightDegrees: Float,
    val sunElevationDegrees: Float,
    val earthTiltDegrees: Float,
    val moonOrbitDegrees: Float,
    val markerLatitudeDegrees: Float,
    val markerLongitudeDegrees: Float,
    val showBackgroundStars: Boolean,
    val showOrbitPath: Boolean,
    val moonLightDegrees: Float,
    val moonSunElevationDegrees: Float,
    val moonPhaseAngleDegrees: Float?,
    val julianDay: Double?,
    val earthSizeFraction: Float,
    val kiddushLevanaStartDegrees: Float? = null,
    val kiddushLevanaEndDegrees: Float? = null,
    val kiddushLevanaColorRgb: Int = KIDDUSH_LEVANA_COLOR_RGB,
    /** Greenwich sidereal angle: spins the Earth in [SceneFrame]. */
    val siderealDegrees: Float = 0f,
    /**
     * The sidereal angle the camera aims at the marker for; null follows [siderealDegrees] (the marker kept centred).
     * Held still, the camera stays put in space: the Earth turns under it, the Moon goes round once a month.
     */
    val viewSiderealDegrees: Float? = null,
    val sunLongitudeDegrees: Float = 0f,
    /** Ecliptic longitude of the Moon's ascending node. */
    val moonNodeDegrees: Float = 0f,
    /** User turn of the globe about the view's vertical, from the marker-centred default. */
    val viewYawDegrees: Float = 0f,
    /** User tilt of the globe, from the marker-centred default. */
    val viewPitchDegrees: Float = 0f,
    /** Camera zoom (field of view narrowed by this factor); 1 = the whole orbit fits. */
    val viewZoom: Float = 1f,
)

/** Rendering parameters for the Moon-from-marker inset view. */
@Immutable
internal data class MoonFromMarkerRenderState(
    val renderSizePx: Int,
    val earthRotationDegrees: Float,
    val lightDegrees: Float,
    val sunElevationDegrees: Float,
    val earthTiltDegrees: Float,
    val moonOrbitDegrees: Float,
    val markerLatitudeDegrees: Float,
    val markerLongitudeDegrees: Float,
    val showBackgroundStars: Boolean,
    val moonLightDegrees: Float,
    val moonSunElevationDegrees: Float,
    val moonPhaseAngleDegrees: Float?,
    val julianDay: Double?,
    val earthSizeFraction: Float,
)

// Exposure(1, 1, 100) → EV100 = 0 → exposure factor 1/1.2. With linear tone mapping, a lit
// Lambertian surface then outputs albedo · lux · cosθ / (1.2π): lux = strength · 1.2π maps the
// old shader's diffuse strength one-to-one.
internal val UnitExposure = Exposure(aperture = 1f, shutterSpeed = 1f, sensitivity = 100f)
internal const val LUX_PER_DIFFUSE_UNIT = 1.2f * PI.toFloat()
internal const val IBL_PER_AMBIENT_UNIT = 1.2f

// Old shaders darkened ambient to 25% on the night side; Filament's IBL is uniform, so match the night side.
internal const val EARTH_NIGHT_AMBIENT = DEFAULT_AMBIENT * 0.25f
private const val MOON_NIGHT_AMBIENT = MOON_AMBIENT * 0.25f

private const val ORBIT_STEPS = 360
private const val ORBIT_STEP_DEGREES = 360f / ORBIT_STEPS
private const val ORBIT_TUBE_RADIUS = 0.75f
private const val KIDDUSH_LEVANA_TUBE_RADIUS = 1.25f
private const val TUBE_SIDES = 6

internal val WidgetPostProcessing =
    PostProcessing(
        antiAliasing = WidgetAntiAliasing.OFF.config,
        colorGrade = ColorGrade(toneMapping = ToneMapping.Linear),
    )

/** How the widgets' 3D views smooth their edges: sharper and dearer down the list (TAA trails a moving scene). */
enum class WidgetAntiAliasing(
    internal val config: AntiAliasing,
) {
    OFF(AntiAliasing(fxaaEnabled = false)),
    FXAA(AntiAliasing(fxaaEnabled = true)),
    MSAA_2(AntiAliasing(msaaEnabled = true, msaaSampleCount = 2, fxaaEnabled = true)),
    MSAA_4(AntiAliasing(msaaEnabled = true, msaaSampleCount = 4, fxaaEnabled = true)),
    MSAA_8(AntiAliasing(msaaEnabled = true, msaaSampleCount = 8, fxaaEnabled = true)),
    TAA(AntiAliasing(fxaaEnabled = false, taaEnabled = true)),
}

/** The anti-aliasing of the 3D views below; set by the host (an advanced setting), off by default. */
val LocalWidgetAntiAliasing = staticCompositionLocalOf { WidgetAntiAliasing.OFF }

/** [WidgetPostProcessing] with the anti-aliasing picked by the host. */
internal val widgetPostProcessing: PostProcessing
    @Composable get() = WidgetPostProcessing.copy(antiAliasing = LocalWidgetAntiAliasing.current.config)

/** Earth + orbiting Moon, laid out exactly like [computeSceneGeometry] so the orbit labels overlay still lines up. */
@Composable
internal fun EarthMoonSceneView(
    state: EarthRenderState,
    engine: Engine,
    showMoon: Boolean,
    modifier: Modifier = Modifier,
) {
    val geometry =
        remember(state.renderSizePx, state.earthSizeFraction) {
            computeSceneGeometry(state.renderSizePx, state.earthSizeFraction)
        }
    // A camera at cameraZ whose frustum spans sceneHalf at z = 0 reproduces perspectiveScale().
    val camera =
        rememberCameraState(
            initialEye = Position(0f, 0f, geometry.cameraZ),
            initialExposure = UnitExposure,
        )
    SideEffect {
        camera.eye = Position(0f, 0f, geometry.cameraZ)
        camera.projection =
            Projection.Perspective(
                fovDegrees = 2.0 * atan(geometry.sceneHalf / (geometry.cameraZ * state.viewZoom).toDouble()) * 180.0 / PI,
                near = geometry.cameraZ * 0.25,
                far = geometry.cameraZ * 3.0,
            )
    }
    val ambient =
        rememberIndirectLightState(
            initialIrradianceSh = SphericalHarmonics(1, floatArrayOf(1f, 1f, 1f)),
            initialIntensity = EARTH_NIGHT_AMBIENT * IBL_PER_AMBIENT_UNIT,
        )
    val frame = SceneFrame(state, geometry)
    val sunDir = frame.view * frame.sun
    val moonWorld = frame.orbitPoint(state.moonOrbitDegrees)
    val moonCam = frame.view * moonWorld * geometry.orbitRadius
    // Rebuilt when the camera moves, not as the sky turns with a running clock: sampled at fixed points of the orbit
    // (offset by the Sun), the ring only follows the node to 0.1° (it drifts 0.05° a day; the plane's tilt is the same
    // to the eye); the Kiddush Levana stretch, which goes round with the Sun, is picked to the sample (1°). ~2k vertices.
    val sunStep = floor(state.sunLongitudeDegrees / ORBIT_STEP_DEGREES)
    val nodeStep = (state.moonNodeDegrees * 10f).roundToInt()

    fun orbitMeshes(
        ring: Boolean,
        kiddushLevana: Boolean,
    ): OrbitMeshes {
        val sun = state.sunLongitudeDegrees
        return OrbitMeshes.build(
            geometry.orbitRadius,
            state.kiddushLevanaStartDegrees.takeIf { kiddushLevana },
            state.kiddushLevanaEndDegrees.takeIf { kiddushLevana },
            sampleOffset = sun,
            membershipOffset = sun - sunStep * ORBIT_STEP_DEGREES,
            withOrbit = ring,
        ) { deg -> frame.view * frame.orbitPoint(deg) * geometry.orbitRadius }
    }
    val orbitRing = remember(frame.view, nodeStep, geometry) { orbitMeshes(ring = true, kiddushLevana = false).orbit }
    val kiddushLevanaArc =
        remember(frame.view, sunStep, nodeStep, geometry, state.kiddushLevanaStartDegrees, state.kiddushLevanaEndDegrees) {
            orbitMeshes(ring = false, kiddushLevana = true).kiddushLevana
        }
    val orbitMeshes = OrbitMeshes(orbitRing, kiddushLevanaArc)

    val earthModel = rememberEarthModel(engine)
    val earthReady = earthModel?.isReady == true
    val moonModel = rememberMoonModel(engine)
    val moonReady = moonModel?.isReady == true
    val rendering = rememberRenderOnChange(listOf(state, showMoon, earthReady, moonReady))

    Box(modifier) {
        if (state.showBackgroundStars) Starfield(Modifier.matchParentSize())
        FilamentSceneView(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            cameraState = camera,
            indirectLightState = ambient,
            postProcessing = widgetPostProcessing,
            shadows = null,
            transparent = true,
            renderingEnabled = rendering,
        ) {
            DirectionalLight(
                direction = Direction(-sunDir.x, -sunDir.y, -sunDir.z),
                intensity = LightIntensity.LuminousPower(DEFAULT_DIFFUSE_STRENGTH * LUX_PER_DIFFUSE_UNIT),
            )
            if (earthModel != null && earthReady) {
                Earth(earthModel, geometry.earthRadiusPx, rotation = frame.view * frame.earth)
                Marker(state, geometry, frame)
            }
            if (showMoon && moonModel != null && moonReady) {
                Moon(
                    moonModel,
                    geometry.moonRadiusWorldPx,
                    position = Position(moonCam.x, moonCam.y, moonCam.z),
                    // Tidally locked: the near side (longitude 0, body +Z) faces the Earth.
                    rotation = frame.view * Rotation.axisAngle(Direction.Up, atan2(-moonWorld.x, -moonWorld.z) * RAD_TO_DEG_F),
                )
            }
            if (state.showOrbitPath) Orbit(orbitMeshes, state.kiddushLevanaColorRgb)
        }
    }
}

/** The Moon as seen from the marker: phase, orientation and eclipse dimming from [moonFromMarkerView]. */
@Composable
internal fun MoonFromMarkerSceneView(
    state: MoonFromMarkerRenderState,
    engine: Engine,
    modifier: Modifier = Modifier,
) {
    val view = moonFromMarkerView(state)
    val camera =
        rememberCameraState(
            initialProjection = Projection.Orthographic(-1.0, 1.0, -1.0, 1.0, 0.1, 10.0),
            initialExposure = UnitExposure,
        )
    SideEffect {
        camera.eye = Position(view.forward.x * 3f, view.forward.y * 3f, view.forward.z * 3f)
        camera.target = Position(0f)
        camera.up = Direction(view.up.x, view.up.y, view.up.z)
    }
    val ambient =
        rememberIndirectLightState(
            initialIrradianceSh = SphericalHarmonics(1, floatArrayOf(1f, 1f, 1f)),
            initialIntensity = MOON_NIGHT_AMBIENT * IBL_PER_AMBIENT_UNIT,
        )

    val moonModel = rememberMoonModel(engine)
    val moonReady = moonModel?.isReady == true
    val rendering = rememberRenderOnChange(listOf(state, moonReady))

    Box(modifier) {
        if (state.showBackgroundStars) Starfield(Modifier.matchParentSize())
        FilamentSceneView(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            cameraState = camera,
            indirectLightState = ambient,
            postProcessing = widgetPostProcessing,
            shadows = null,
            transparent = true,
            renderingEnabled = rendering,
        ) {
            DirectionalLight(
                direction = Direction(-view.sunDir.x, -view.sunDir.y, -view.sunDir.z),
                intensity = LightIntensity.LuminousPower(MOON_DIFFUSE_STRENGTH * LUX_PER_DIFFUSE_UNIT * view.sunVisibility),
            )
            if (moonModel != null && moonReady) Moon(moonModel, radius = 0.985f)
        }
        GhostOutline(Modifier.matchParentSize())
    }
}

// ============================================================================
// SCENE PIECES
// ============================================================================

@Composable
internal fun FilamentSceneScope.MeshNode(
    material: io.github.erkko68.filament.MaterialInstance,
    mesh: MeshArrays,
    scale: Float,
    position: Position = Position(0f),
    rotation: Rotation = Rotation.Identity,
) = Mesh(
    material = material,
    positions = mesh.positions,
    normals = mesh.normals,
    uvs = mesh.uvs,
    indices = mesh.indices,
    position = position,
    rotation = rotation,
    scale = Scale(scale),
    castShadows = false,
    receiveShadows = false,
)

/**
 * NASA's Earth (Blue Marble cube map + normal map), textures re-encoded as KTX2 (Basis Universal, transcoded to a
 * GPU-compressed format on load). [detailed] picks the 2048 px textures for a full window; the widgets get the 1024 px
 * ones, a quarter of the memory.
 */
@Composable
internal fun rememberEarthModel(
    engine: Engine,
    detailed: Boolean = false,
): GltfAsset? = rememberBodyModel(engine, "earth", detailed)

/** NASA's Earth model at [radius]; show it only once its textures are uploaded ([GltfAsset.isReady]). */
@Composable
internal fun FilamentSceneScope.Earth(
    model: GltfAsset,
    radius: Float,
    rotation: Rotation,
    position: Position = Position(0f),
) = GltfInstance(
    asset = model,
    position = position,
    rotation = rotation * EarthModelToBody,
    scale = Scale(radius / BODY_MODEL_RADIUS),
    castShadows = false,
    receiveShadows = false,
)

/** NASA's Moon (LRO cube map), texture re-encoded as KTX2. [detailed] as for [rememberEarthModel]. */
@Composable
internal fun rememberMoonModel(
    engine: Engine,
    detailed: Boolean = false,
): GltfAsset? = rememberBodyModel(engine, "moon", detailed)

/** files/[name].glb when [detailed], else its downscaled-texture twin files/[name]_lite.glb (same mesh and frame). */
@Composable
internal fun rememberBodyModel(
    engine: Engine,
    name: String,
    detailed: Boolean,
): GltfAsset? {
    val path = if (detailed) "files/$name.glb" else "files/${name}_lite.glb"
    return rememberGltfAsset(key = path, engine = engine) { Res.readBytes(path) }
}

/**
 * NASA's Moon model at [radius]; show it only once its textures are uploaded ([GltfAsset.isReady]). moon.glb already
 * has the body frame of [latLonToUnitVector]: north at +Y, longitude 0 (the near side) at +Z, 90°E at +X.
 */
@Composable
internal fun FilamentSceneScope.Moon(
    model: GltfAsset,
    radius: Float,
    position: Position = Position(0f),
    rotation: Rotation = Rotation.Identity,
) = GltfInstance(
    asset = model,
    position = position,
    rotation = rotation,
    scale = Scale(radius / BODY_MODEL_RADIUS),
    castShadows = false,
    receiveShadows = false,
)

/** Radius of NASA's earth.glb and moon.glb (spherified ±500 cubes). */
private const val BODY_MODEL_RADIUS = 500f

/**
 * earth.glb has north at +Y and Greenwich at −Z (90°E at −X); the body frame of [latLonToUnitVector] puts
 * Greenwich at +Z and 90°E at +X: a half turn about the pole.
 */
private val EarthModelToBody = Rotation.axisAngle(Direction.Up, 180f)

@Composable
private fun FilamentSceneScope.Marker(
    state: EarthRenderState,
    geometry: SceneGeometry,
    frame: SceneFrame,
) {
    val p = frame.view * frame.earth * latLonToUnitVector(state.markerLatitudeDegrees, state.markerLongitudeDegrees).toDirection()
    val radius = max(MIN_MARKER_RADIUS_PX, geometry.earthSizePx * MARKER_RADIUS_FRACTION)
    // ponytail: the old white outline ring is gone; a red dot on the surface reads fine at widget size.
    Sphere(
        material = rememberUnlitColorMaterialInstance(Color(MARKER_FILL_COLOR).toLinearColor()),
        radius = radius,
        position = Position(p.x * geometry.earthRadiusPx, p.y * geometry.earthRadiusPx, p.z * geometry.earthRadiusPx),
        castShadows = false,
        receiveShadows = false,
    )
}

@Composable
internal fun FilamentSceneScope.Orbit(
    meshes: OrbitMeshes,
    kiddushLevanaColorRgb: Int,
) {
    // Unlit colour pre-multiplied over the black sky stands in for the old alpha blending.
    for (band in 0 until ORBIT_DEPTH_BANDS) {
        val depth = (band + 0.5f) / ORBIT_DEPTH_BANDS
        val orbit = rememberUnlitColorMaterialInstance(dimmed(ORBIT_COLOR_RGB, depthAlpha(depth, ORBIT_ALPHA_BACK, ORBIT_ALPHA_FRONT)))
        meshes.orbit[band]?.let { MeshNode(orbit, it, 1f) }
        val kl =
            rememberUnlitColorMaterialInstance(
                dimmed(kiddushLevanaColorRgb, depthAlpha(depth, KIDDUSH_LEVANA_ALPHA_BACK, KIDDUSH_LEVANA_ALPHA_FRONT)),
            )
        meshes.kiddushLevana[band]?.let { MeshNode(kl, it, 1f) }
    }
}

internal fun dimmed(
    rgb: Int,
    alpha: Int,
): LinearColor {
    val a = alpha / 255f
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f * a,
        green = ((rgb shr 8) and 0xFF) / 255f * a,
        blue = (rgb and 0xFF) / 255f * a,
    ).toLinearColor()
}

@Composable
internal fun Starfield(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color.Black)
        val count = (size.width * size.height / PIXELS_PER_STAR).toInt().coerceIn(MIN_STAR_COUNT, MAX_STAR_COUNT)
        val random = Random(STARFIELD_SEED)
        repeat(count) {
            val t = random.nextFloat()
            val intensity = (32f + 223f * t * t * t) / 255f
            drawRect(
                color = Color(intensity, intensity, intensity),
                topLeft = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                size = Size(1f, 1f),
            )
        }
    }
}

@Composable
private fun GhostOutline(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val thickness = max(1.1f, size.minDimension * 0.0045f)
        drawCircle(
            color = Color(GHOST_MOON_OUTLINE_RGB or (GHOST_MOON_OUTLINE_ALPHA shl 24)),
            radius = size.minDimension / 2f - thickness - 0.5f,
            style = Stroke(width = thickness * 2f),
        )
    }
}

// ============================================================================
// GEOMETRY
// ============================================================================

internal class MeshArrays(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
)

/** Orbit and Kiddush Levana arc as thin tubes, cut into [ORBIT_DEPTH_BANDS] depth bands (far → near) to fade the far side. */
internal class OrbitMeshes(
    val orbit: List<MeshArrays?>,
    val kiddushLevana: List<MeshArrays?>,
) {
    companion object {
        /**
         * [cameraPoint]: camera-space position of the orbit at an orbit angle (degrees); [klStart]..[klEnd] is
         * highlighted. The samples sit [sampleOffset] back from whole degrees, and are tested for the highlight
         * [membershipOffset] forward.
         */
        fun build(
            orbitRadius: Float,
            klStart: Float?,
            klEnd: Float?,
            sampleOffset: Float = 0f,
            membershipOffset: Float = 0f,
            withOrbit: Boolean = true,
            cameraPoint: (Float) -> Direction,
        ): OrbitMeshes {
            val points =
                (0..ORBIT_STEPS).map { i ->
                    val deg = i * ORBIT_STEP_DEGREES - sampleOffset
                    val p = cameraPoint(deg)
                    deg to MoonOrbitPosition(x = p.x, yCam = p.y, zCam = p.z)
                }

            val bands =
                points.map { (_, p) ->
                    val depth = orbitDepth(p.zCam, orbitRadius)
                    (depth * ORBIT_DEPTH_BANDS).toInt().coerceIn(0, ORBIT_DEPTH_BANDS - 1)
                }

            // A point also closes the previous point's band, so neighbouring bands join without a gap.
            fun tube(
                radius: Float,
                band: Int,
                keep: (Float) -> Boolean,
            ) = tubeMesh(
                points.mapIndexed { i, (deg, pos) ->
                    pos.takeIf { keep(deg) && (bands[i] == band || bands.getOrNull(i - 1) == band) }
                },
                radius,
            )
            val inKl = { deg: Float -> klStart != null && klEnd != null && isAngleInRange(deg + membershipOffset, klStart, klEnd) }
            return OrbitMeshes(
                orbit = List(ORBIT_DEPTH_BANDS) { band -> if (withOrbit) tube(ORBIT_TUBE_RADIUS, band) { true } else null },
                kiddushLevana = List(ORBIT_DEPTH_BANDS) { tube(KIDDUSH_LEVANA_TUBE_RADIUS, it, inKl) },
            )
        }
    }
}

/** Tubes along each run of non-null points; the orbit is centred on the origin, so the radial vector is a normal. */
internal fun tubeMesh(
    path: List<MoonOrbitPosition?>,
    radius: Float,
    sides: Int = TUBE_SIDES,
    /** Where the cross-sections are turned from, relative to the points: the origin by default. */
    normalOrigin: Vec3f = Vec3f(0f, 0f, 0f),
): MeshArrays? {
    val positions = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    val indices = ArrayList<Int>()
    var runStart = -1
    for (i in path.indices) {
        val p = path[i]
        if (p == null) {
            runStart = -1
            continue
        }
        val prev = path.getOrNull(i - 1) ?: p
        val next = path.getOrNull(i + 1) ?: p
        val t = Vec3f(next.x - prev.x, next.yCam - prev.yCam, next.zCam - prev.zCam).normalized()
        val n1 = Vec3f(p.x + normalOrigin.x, p.yCam + normalOrigin.y, p.zCam + normalOrigin.z).normalized()
        val n2 = cross(t, n1)
        val base = positions.size / 3
        for (k in 0 until sides) {
            val a = k * 2f * PI.toFloat() / sides
            positions += p.x + radius * (cos(a) * n1.x + sin(a) * n2.x)
            positions += p.yCam + radius * (cos(a) * n1.y + sin(a) * n2.y)
            positions += p.zCam + radius * (cos(a) * n1.z + sin(a) * n2.z)
            uvs += i.toFloat() / path.size
            uvs += k.toFloat() / sides
        }
        if (runStart >= 0) {
            val prevBase = base - sides
            for (k in 0 until sides) {
                val k1 = (k + 1) % sides
                indices += listOf(prevBase + k, prevBase + k1, base + k, prevBase + k1, base + k1, base + k)
            }
        }
        runStart = i
    }
    if (indices.isEmpty()) return null
    val pos = positions.toFloatArray()
    return MeshArrays(pos, normalsFromCenter(pos), uvs.toFloatArray(), indices.toIntArray())
}

// Unlit, so normals only need to be non-degenerate for the tangent-frame builder.
private fun normalsFromCenter(positions: FloatArray): FloatArray {
    val out = FloatArray(positions.size)
    for (i in positions.indices step 3) {
        val v = Vec3f(positions[i], positions[i + 1], positions[i + 2]).normalized()
        out[i] = v.x
        out[i + 1] = v.y
        out[i + 2] = v.z
    }
    return out
}

// ============================================================================
// MOON FROM MARKER
// ============================================================================

internal class MoonFromMarkerView(
    /** Unit vector from the Moon towards the observer (the camera sits along it). */
    val forward: Vec3f,
    val up: Vec3f,
    /** Unit vector from the Moon towards the Sun. */
    val sunDir: Vec3f,
    val sunVisibility: Float,
)

/** Camera frame and lighting for the Moon seen from the marker — the math of the former SkSL renderer, unchanged. */
internal fun moonFromMarkerView(state: MoonFromMarkerRenderState): MoonFromMarkerView {
    val geometry = computeSceneGeometry(state.renderSizePx, state.earthSizeFraction)
    val moonOrbit = transformMoonOrbitPosition(state.moonOrbitDegrees, geometry.orbitRadius, geometry.viewPitchRad)

    val unit = latLonToUnitVector(state.markerLatitudeDegrees, state.markerLongitudeDegrees)
    val yaw = state.earthRotationDegrees * DEG_TO_RAD_F
    val xRot = unit.x * cos(yaw) - unit.z * sin(yaw)
    val zRot = unit.x * sin(yaw) + unit.z * cos(yaw)
    val tilt = state.earthTiltDegrees * DEG_TO_RAD_F
    val obs =
        Vec3f(
            (xRot * cos(tilt) + unit.y * sin(tilt)) * geometry.earthRadiusPx,
            (-xRot * sin(tilt) + unit.y * cos(tilt)) * geometry.earthRadiusPx,
            zRot * geometry.earthRadiusPx,
        )
    val upHint = if (obs.length() > EPSILON) obs.normalized() else Vec3f(0f, 1f, 0f)

    var viewDir = Vec3f(obs.x - moonOrbit.x, obs.y - moonOrbit.yCam, obs.z - moonOrbit.zCam)
    if (state.julianDay != null) {
        val horizontal =
            computeMoonHorizontalPosition(
                julianDay = state.julianDay,
                latitudeDeg = state.markerLatitudeDegrees.toDouble(),
                longitudeDeg = state.markerLongitudeDegrees.toDouble(),
            )
        val moonDirWorld =
            horizontalToWorld(
                latitudeDeg = state.markerLatitudeDegrees.toDouble(),
                longitudeDeg = state.markerLongitudeDegrees.toDouble(),
                azimuthFromNorthDeg = horizontal.azimuthFromNorthDeg,
                elevationDeg = horizontal.elevationDeg,
                earthRotationDegrees = state.earthRotationDegrees,
                earthTiltDegrees = state.earthTiltDegrees,
            )
        viewDir = Vec3f(-moonDirWorld.x, -moonDirWorld.y, -moonDirWorld.z)
    }

    val sunHint = sunVectorFromAngles(state.moonLightDegrees, state.moonSunElevationDegrees)
    val lighting =
        state.moonPhaseAngleDegrees?.let {
            computeMoonLightFromPhaseWithObserverUp(
                phaseAngleDegrees = it,
                viewDirX = viewDir.x,
                viewDirY = viewDir.y,
                viewDirZ = viewDir.z,
                observerUpX = upHint.x,
                observerUpY = upHint.y,
                observerUpZ = upHint.z,
                sunDirectionHint = sunHint,
            )
        }
    val lightDeg = lighting?.lightDegrees ?: state.moonLightDegrees
    val sunElev = lighting?.sunElevationDegrees ?: state.moonSunElevationDegrees

    val forward = if (viewDir.length() > EPSILON) viewDir.normalized() else Vec3f(0f, 0f, 1f)
    return MoonFromMarkerView(
        forward = forward,
        up = orthonormalUp(forward, upHint),
        sunDir = sunVectorFromAngles(lightDeg, sunElev),
        sunVisibility =
            moonSunVisibility(
                moonCenterX = moonOrbit.x,
                moonCenterY = moonOrbit.yCam,
                moonCenterZ = moonOrbit.zCam,
                moonRadius = geometry.moonRadiusWorldPx,
                sunAzimuthDegrees = lightDeg,
                sunElevationDegrees = sunElev,
            ),
    )
}

/** Up vector orthogonal to [forward], with the old renderer's fallback when [upHint] is parallel to it. */
private fun orthonormalUp(
    forward: Vec3f,
    upHint: Vec3f,
): Vec3f {
    var right = cross(upHint, forward)
    if (right.length() < EPSILON) right = Vec3f(0f, forward.z, -forward.y)
    if (right.length() < EPSILON) right = Vec3f(forward.z, 0f, -forward.x)
    return cross(forward, right.normalized()).normalized()
}

/**
 * Earth's shadow at the Moon's distance: 1 = fully lit, 0 = umbra.
 */
internal fun moonSunVisibility(
    moonCenterX: Float,
    moonCenterY: Float,
    moonCenterZ: Float,
    moonRadius: Float,
    sunAzimuthDegrees: Float,
    sunElevationDegrees: Float,
): Float {
    val sunDir = sunVectorFromAngles(sunAzimuthDegrees, sunElevationDegrees)
    val proj = moonCenterX * sunDir.x + moonCenterY * sunDir.y + moonCenterZ * sunDir.z
    if (proj > 0f) return 1f

    val r2 = moonCenterX * moonCenterX + moonCenterY * moonCenterY + moonCenterZ * moonCenterZ
    val moonDistance = sqrt(r2)
    val d = sqrt((r2 - proj * proj).coerceAtLeast(0f))
    val umbraRadius = moonDistance * EARTH_UMBRA_DISTANCE_RATIO
    val penumbraRadius = moonDistance * EARTH_PENUMBRA_DISTANCE_RATIO
    val softPenumbra = (penumbraRadius + moonRadius * 0.12f).coerceAtLeast(umbraRadius)
    return smoothStep(umbraRadius, softPenumbra, d)
}

/** Checks if an angle is within a range, handling wrap-around at 360 degrees. */
internal fun isAngleInRange(
    angle: Float,
    start: Float,
    end: Float,
): Boolean {
    val a = ((angle % 360f) + 360f) % 360f
    val s = ((start % 360f) + 360f) % 360f
    val e = ((end % 360f) + 360f) % 360f
    return if (s <= e) a in s..e else a >= s || a <= e
}

/** 0 = the orbit's farthest point from the viewer, 1 = its nearest. */
internal fun orbitDepth(
    zCam: Float,
    orbitRadius: Float,
): Float = if (orbitRadius > 0f) ((zCam / orbitRadius + 1f) / 2f).coerceIn(0f, 1f) else 1f

/** Alpha fading from [back] at the far side to [front] at the near side. */
internal fun depthAlpha(
    depth: Float,
    back: Int,
    front: Int,
): Int = (back + (front - back) * depth).toInt()

internal data class OrbitScreenPosition(
    val x: Float,
    val y: Float,
    val zCam: Float,
    val depth: Float,
    val hiddenByEarth: Boolean,
)

/** Moon orbit position projected into screen space, for UI overlays (labels) aligned with the rendered orbit. */
internal fun computeOrbitScreenPosition(
    state: EarthRenderState,
    orbitDegrees: Float,
): OrbitScreenPosition {
    val geometry = computeSceneGeometry(state.renderSizePx, state.earthSizeFraction)
    val orbit = SceneFrame(state, geometry).let { it.view * it.orbitPoint(orbitDegrees) * geometry.orbitRadius }
    // Zooming narrows the field of view, which scales the image about its centre
    val orbitScale = perspectiveScale(geometry.cameraZ, orbit.z) * state.viewZoom
    val x = geometry.sceneHalf + orbit.x * orbitScale
    val y = geometry.sceneHalf - orbit.y * orbitScale
    return OrbitScreenPosition(
        x = x,
        y = y,
        zCam = orbit.z,
        depth = orbitDepth(orbit.z, geometry.orbitRadius),
        // Behind the Earth's disc (its silhouette is ~the z = 0 radius; the camera sits well back)
        hiddenByEarth = orbit.z < 0f && hypot(x - geometry.sceneHalf, y - geometry.sceneHalf) < geometry.earthRadiusPx * state.viewZoom,
    )
}

// ============================================================================
// PHYSICAL FRAME
// ============================================================================

internal const val RAD_TO_DEG_F = (180.0 / PI).toFloat()
internal val AxisX = Direction(1f, 0f, 0f)
private val AxisZ = Direction(0f, 0f, 1f)

/** Earth body axes (see [latLonToUnitVector]) onto equatorial ones: x→y→z→x, so Greenwich goes to +X, north to +Z. */
private val BodyToEquatorial = Rotation.axisAngle(Direction(1f, 1f, 1f), 120f)

internal fun Vec3f.toDirection() = Direction(x, y, z)

internal fun earthToWorld(
    siderealDegrees: Float,
    obliquityDegrees: Float,
): Rotation =
    Rotation.axisAngle(AxisX, -(90f + obliquityDegrees)) *
        Rotation.axisAngle(AxisZ, siderealDegrees) *
        BodyToEquatorial

/** Ecliptic longitude → world direction (on the ecliptic). */
internal fun eclipticDirection(longitudeDegrees: Float): Direction {
    val l = longitudeDegrees * DEG_TO_RAD_F
    return Direction(cos(l), 0f, -sin(l))
}

/** Camera azimuth (about the ecliptic pole) that brings [d] in front of the viewer. */
private fun azimuthOf(d: Direction): Float = atan2(-d.x, d.z) * RAD_TO_DEG_F

/**
 * The Earth–Moon system as a real sky, plus the camera looking at it.
 *
 * World: +Y = north ecliptic pole, +X = vernal equinox, ecliptic longitude λ at (cos λ, 0, −sin λ), so the Sun
 * and Moon move counter-clockwise seen from the north, as they do. The Earth turns with Greenwich sidereal time
 * about an axis tilted by the obliquity, the Sun sits at its ecliptic longitude, and the Moon's orbit is inclined
 * on its real node line, the Moon at its Hebrew-day elongation from the Sun (day 1 = conjunction).
 *
 * Nothing in the world depends on the user: dragging only moves the camera ([EarthRenderState.viewYawDegrees],
 * [EarthRenderState.viewPitchDegrees]).
 * The camera follows the marker (see [view]), so changing the time keeps it centred and moves the Sun, hence the
 * day/night line, and the Moon along its orbit.
 */
internal class SceneFrame(
    state: EarthRenderState,
    geometry: SceneGeometry,
) {
    private val sunLongitude = state.sunLongitudeDegrees
    private val node = state.moonNodeDegrees * DEG_TO_RAD_F

    /** Earth body → world. */
    val earth: Rotation = earthToWorld(state.siderealDegrees, state.earthTiltDegrees)

    /** Unit vector from the Earth to the Sun. */
    val sun: Direction = eclipticDirection(sunLongitude)

    /**
     * World → camera, aimed at the marker from around the ecliptic pole (kept up): whatever the time the marker is
     * dead centre and the Moon's orbit keeps its orientation, the Moon sliding along it. The price: the Earth's axis
     * sways by up to the obliquity over a day, and the orbit flattens as the marker nears the ecliptic. The user's
     * drag adds [EarthRenderState.viewYawDegrees] about the pole and [EarthRenderState.viewPitchDegrees] in elevation.
     */
    val view: Rotation =
        run {
            val aimedEarth = state.viewSiderealDegrees?.let { earthToWorld(it, state.earthTiltDegrees) } ?: earth
            val marker = aimedEarth * latLonToUnitVector(state.markerLatitudeDegrees, state.markerLongitudeDegrees).toDirection()
            val elevation = asin(marker.y.coerceIn(-1f, 1f)) * RAD_TO_DEG_F + state.viewPitchDegrees
            Rotation.axisAngle(AxisX, elevation) * Rotation.axisAngle(Direction.Up, azimuthOf(marker) + state.viewYawDegrees)
        }

    /** World unit vector of the Moon's orbit at [orbitDegrees], the Hebrew-day angle of the orbit labels. */
    fun orbitPoint(orbitDegrees: Float): Direction =
        orbitalPoint(
            argumentOfLatitude = (sunLongitude + orbitDegrees - ORBIT_DAY_LABEL_START_DEGREES) * DEG_TO_RAD_F - node,
            inclination = MOON_ORBIT_INCLINATION_DEG * DEG_TO_RAD_F,
        )

    /** Point of an orbit inclined by [inclination] on the ascending node [node] (0 = the ecliptic, from the equinox). */
    private fun orbitalPoint(
        argumentOfLatitude: Float,
        inclination: Float,
        nodeRad: Float = if (inclination == 0f) 0f else node,
    ): Direction {
        val cu = cos(argumentOfLatitude)
        val su = sin(argumentOfLatitude)
        val x = cos(nodeRad) * cu - sin(nodeRad) * su * cos(inclination)
        val y = sin(nodeRad) * cu + cos(nodeRad) * su * cos(inclination)
        val z = su * sin(inclination)
        // Ecliptic (x, y, z) → world (x, z, −y)
        return Direction(x, z, -y)
    }
}
