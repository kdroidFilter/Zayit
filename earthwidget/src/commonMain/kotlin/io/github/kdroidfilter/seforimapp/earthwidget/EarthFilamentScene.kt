package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.Texture
import io.github.erkko68.filament.TextureSampler
import io.github.erkko68.filament.compose.FilamentSceneScope
import io.github.erkko68.filament.compose.FilamentSceneView
import io.github.erkko68.filament.compose.scene.AntiAliasing
import io.github.erkko68.filament.compose.scene.ColorGrade
import io.github.erkko68.filament.compose.scene.Direction
import io.github.erkko68.filament.compose.scene.DirectionalLight
import io.github.erkko68.filament.compose.scene.Exposure
import io.github.erkko68.filament.compose.scene.LightIntensity
import io.github.erkko68.filament.compose.scene.LinearColor
import io.github.erkko68.filament.compose.scene.Position
import io.github.erkko68.filament.compose.scene.PostProcessing
import io.github.erkko68.filament.compose.scene.Projection
import io.github.erkko68.filament.compose.scene.Rotation
import io.github.erkko68.filament.compose.scene.SphericalHarmonics
import io.github.erkko68.filament.compose.scene.ToneMapping
import io.github.erkko68.filament.compose.scene.primitives.Mesh
import io.github.erkko68.filament.compose.scene.primitives.Sphere
import io.github.erkko68.filament.compose.scene.rememberCameraState
import io.github.erkko68.filament.compose.scene.rememberIndirectLightState
import io.github.erkko68.filament.compose.scene.rememberTexture
import io.github.erkko68.filament.compose.scene.rememberTexturedMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberUnlitColorMaterialInstance
import io.github.erkko68.filament.compose.scene.toLinearColor
import seforimapp.earthwidget.generated.resources.Res
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
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
private val UnitExposure = Exposure(aperture = 1f, shutterSpeed = 1f, sensitivity = 100f)
private const val LUX_PER_DIFFUSE_UNIT = 1.2f * PI.toFloat()
private const val IBL_PER_AMBIENT_UNIT = 1.2f

// Old shaders darkened ambient to 25% on the night side; Filament's IBL is uniform, so match the night side.
private const val EARTH_NIGHT_AMBIENT = DEFAULT_AMBIENT * 0.25f
private const val MOON_NIGHT_AMBIENT = MOON_AMBIENT * 0.25f

private const val ORBIT_STEPS = 360
private const val ORBIT_TUBE_RADIUS = 0.75f
private const val KIDDUSH_LEVANA_TUBE_RADIUS = 1.25f
private const val TUBE_SIDES = 6

private val WidgetPostProcessing =
    PostProcessing(
        antiAliasing = AntiAliasing(msaaEnabled = true, fxaaEnabled = true),
        colorGrade = ColorGrade(toneMapping = ToneMapping.Linear),
    )

// Mipmaps aren't generated by the loader: plain bilinear.
private val BilinearRepeat by lazy {
    TextureSampler(TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.REPEAT)
}

internal class WidgetTextures(
    val earth: Texture?,
    val moon: Texture?,
)

@Composable
internal fun rememberWidgetTextures(engine: Engine): WidgetTextures =
    WidgetTextures(
        earth = rememberTexture(engine = engine) { Res.readBytes("drawable/earthmap.jpg") },
        moon = rememberTexture(engine = engine) { Res.readBytes("drawable/moonmap.jpg") },
    )

/** Earth + orbiting Moon, laid out exactly like [computeSceneGeometry] so the orbit labels overlay still lines up. */
@Composable
internal fun EarthMoonSceneView(
    state: EarthRenderState,
    engine: Engine,
    textures: WidgetTextures,
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
                fovDegrees = 2.0 * atan(geometry.sceneHalf / geometry.cameraZ.toDouble()) * 180.0 / PI,
                near = geometry.cameraZ * 0.25,
                far = geometry.cameraZ * 3.0,
            )
    }
    val ambient =
        rememberIndirectLightState(
            initialIrradianceSh = SphericalHarmonics(1, floatArrayOf(1f, 1f, 1f)),
            initialIntensity = EARTH_NIGHT_AMBIENT * IBL_PER_AMBIENT_UNIT,
        )
    val sunDir = sunVectorFromAngles(state.lightDegrees, state.sunElevationDegrees)
    val moonOrbit = transformMoonOrbitPosition(state.moonOrbitDegrees, geometry.orbitRadius, geometry.viewPitchRad)
    val orbitMeshes =
        remember(geometry, state.kiddushLevanaStartDegrees, state.kiddushLevanaEndDegrees) {
            OrbitMeshes.build(geometry, state.kiddushLevanaStartDegrees, state.kiddushLevanaEndDegrees)
        }

    Box(modifier) {
        if (state.showBackgroundStars) Starfield(Modifier.matchParentSize())
        FilamentSceneView(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            cameraState = camera,
            indirectLightState = ambient,
            postProcessing = WidgetPostProcessing,
            shadows = null,
            transparent = true,
        ) {
            DirectionalLight(
                direction = Direction(-sunDir.x, -sunDir.y, -sunDir.z),
                intensity = LightIntensity.LuminousPower(DEFAULT_DIFFUSE_STRENGTH * LUX_PER_DIFFUSE_UNIT),
            )
            textures.earth?.let { texture ->
                MeshNode(
                    material = rememberTexturedMaterialInstance(texture, roughness = 0.7f, sampler = BilinearRepeat),
                    mesh = UnitSphereMesh,
                    scale = geometry.earthRadiusPx,
                    rotation = bodyRotation(state.earthRotationDegrees, state.earthTiltDegrees),
                )
                Marker(state, geometry)
            }
            if (showMoon) {
                textures.moon?.let { texture ->
                    MeshNode(
                        material = rememberTexturedMaterialInstance(texture, roughness = 1f, sampler = BilinearRepeat),
                        mesh = UnitSphereMesh,
                        scale = geometry.moonRadiusWorldPx,
                        position = Position(moonOrbit.x, moonOrbit.yCam, moonOrbit.zCam),
                        rotation = bodyRotation(state.moonOrbitDegrees + state.earthRotationDegrees, 0f),
                    )
                }
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
    moonTexture: Texture?,
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

    Box(modifier) {
        if (state.showBackgroundStars) Starfield(Modifier.matchParentSize())
        FilamentSceneView(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            cameraState = camera,
            indirectLightState = ambient,
            postProcessing = WidgetPostProcessing,
            shadows = null,
            transparent = true,
        ) {
            DirectionalLight(
                direction = Direction(-view.sunDir.x, -view.sunDir.y, -view.sunDir.z),
                intensity = LightIntensity.LuminousPower(MOON_DIFFUSE_STRENGTH * LUX_PER_DIFFUSE_UNIT * view.sunVisibility),
            )
            moonTexture?.let { texture ->
                MeshNode(
                    material = rememberTexturedMaterialInstance(texture, roughness = 1f, sampler = BilinearRepeat),
                    mesh = UnitSphereMesh,
                    scale = 0.985f,
                )
            }
        }
        GhostOutline(Modifier.matchParentSize())
    }
}

// ============================================================================
// SCENE PIECES
// ============================================================================

@Composable
private fun FilamentSceneScope.MeshNode(
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
    scale =
        io.github.erkko68.filament.compose.scene
            .Scale(scale),
    castShadows = false,
    receiveShadows = false,
)

@Composable
private fun FilamentSceneScope.Marker(
    state: EarthRenderState,
    geometry: SceneGeometry,
) {
    val p =
        earthBodyToWorld(
            latLonToUnitVector(state.markerLatitudeDegrees, state.markerLongitudeDegrees),
            state.earthRotationDegrees,
            state.earthTiltDegrees,
        )
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
private fun FilamentSceneScope.Orbit(
    meshes: OrbitMeshes,
    kiddushLevanaColorRgb: Int,
) {
    // Unlit colour pre-multiplied over the black sky stands in for the old alpha blending.
    val front = rememberUnlitColorMaterialInstance(dimmed(ORBIT_COLOR_RGB, ORBIT_ALPHA_FRONT))
    val back = rememberUnlitColorMaterialInstance(dimmed(ORBIT_COLOR_RGB, ORBIT_ALPHA_BACK))
    meshes.orbitFront?.let { MeshNode(front, it, 1f) }
    meshes.orbitBack?.let { MeshNode(back, it, 1f) }
    val klFront = rememberUnlitColorMaterialInstance(dimmed(kiddushLevanaColorRgb, KIDDUSH_LEVANA_ALPHA_FRONT))
    val klBack = rememberUnlitColorMaterialInstance(dimmed(kiddushLevanaColorRgb, KIDDUSH_LEVANA_ALPHA_BACK))
    meshes.klFront?.let { MeshNode(klFront, it, 1f) }
    meshes.klBack?.let { MeshNode(klBack, it, 1f) }
}

private fun dimmed(
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
private fun Starfield(modifier: Modifier = Modifier) {
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

/**
 * Node rotation for a textured body. The old shader mapped a world normal to texture space with
 * `Ry(yaw) · Rz(tilt)`; the node rotation is its inverse, `Rz(-tilt) · Ry(-yaw)`.
 */
private fun bodyRotation(
    yawDegrees: Float,
    tiltDegrees: Float,
): Rotation =
    Rotation.axisAngle(Direction(0f, 0f, 1f), -tiltDegrees) *
        Rotation.axisAngle(Direction.Up, -yawDegrees)

/** Same transform as [bodyRotation], applied to a body-space vector (used for the marker). */
private fun earthBodyToWorld(
    v: Vec3f,
    yawDegrees: Float,
    tiltDegrees: Float,
): Vec3f {
    val yaw = yawDegrees * DEG_TO_RAD_F
    val x1 = v.x * cos(yaw) - v.z * sin(yaw)
    val z1 = v.x * sin(yaw) + v.z * cos(yaw)
    val tilt = tiltDegrees * DEG_TO_RAD_F
    return Vec3f(x1 * cos(tilt) + v.y * sin(tilt), -x1 * sin(tilt) + v.y * cos(tilt), z1)
}

internal class MeshArrays(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
)

/**
 * Unit sphere whose UVs follow the old shader's equirectangular mapping: body direction
 * (sin lon · cos lat, sin lat, cos lon · cos lat), u = lon / 360° + 0.5, north pole at v = 1
 * (filament-kmp's loader puts the image top at v = 1, as its own Sphere primitive assumes).
 */
internal val UnitSphereMesh: MeshArrays by lazy { latLonSphere(rings = 64, segments = 128) }

internal fun latLonSphere(
    rings: Int,
    segments: Int,
): MeshArrays {
    val vertexCount = (rings + 1) * (segments + 1)
    val positions = FloatArray(vertexCount * 3)
    val uvs = FloatArray(vertexCount * 2)
    var p = 0
    var u = 0
    for (r in 0..rings) {
        val v = r.toFloat() / rings
        val lat = (v - 0.5f) * PI.toFloat()
        for (s in 0..segments) {
            val uu = s.toFloat() / segments
            val lon = (uu - 0.5f) * 2f * PI.toFloat()
            positions[p++] = sin(lon) * cos(lat)
            positions[p++] = sin(lat)
            positions[p++] = cos(lon) * cos(lat)
            uvs[u++] = uu
            uvs[u++] = v
        }
    }
    // Rows go south → north and columns west → east, so (a, b, c) is CCW seen from outside.
    val indices = IntArray(rings * segments * 6)
    var i = 0
    val stride = segments + 1
    for (r in 0 until rings) {
        for (s in 0 until segments) {
            val a = r * stride + s
            val b = a + 1
            val c = a + stride
            val d = c + 1
            indices[i++] = a
            indices[i++] = b
            indices[i++] = c
            indices[i++] = b
            indices[i++] = d
            indices[i++] = c
        }
    }
    return MeshArrays(positions, positions.copyOf(), uvs, indices)
}

/** Orbit and Kiddush Levana arc as thin tubes, split at zCam = 0 so the far half can be dimmed. */
private class OrbitMeshes(
    val orbitFront: MeshArrays?,
    val orbitBack: MeshArrays?,
    val klFront: MeshArrays?,
    val klBack: MeshArrays?,
) {
    companion object {
        fun build(
            geometry: SceneGeometry,
            klStart: Float?,
            klEnd: Float?,
        ): OrbitMeshes {
            val points =
                (0..ORBIT_STEPS).map { i ->
                    val deg = i * 360f / ORBIT_STEPS
                    deg to transformMoonOrbitPosition(deg, geometry.orbitRadius, geometry.viewPitchRad)
                }

            fun tube(
                radius: Float,
                keep: (Float, MoonOrbitPosition) -> Boolean,
            ) = tubeMesh(points.map { (deg, pos) -> if (keep(deg, pos)) pos else null }, radius)
            val inKl = { deg: Float -> klStart != null && klEnd != null && isAngleInRange(deg, klStart, klEnd) }
            return OrbitMeshes(
                orbitFront = tube(ORBIT_TUBE_RADIUS) { _, p -> p.zCam >= 0f },
                orbitBack = tube(ORBIT_TUBE_RADIUS) { _, p -> p.zCam < 0f },
                klFront = tube(KIDDUSH_LEVANA_TUBE_RADIUS) { d, p -> inKl(d) && p.zCam >= 0f },
                klBack = tube(KIDDUSH_LEVANA_TUBE_RADIUS) { d, p -> inKl(d) && p.zCam < 0f },
            )
        }
    }
}

/** Tubes along each run of non-null points; the orbit is centred on the origin, so the radial vector is a normal. */
private fun tubeMesh(
    path: List<MoonOrbitPosition?>,
    radius: Float,
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
        val n1 = Vec3f(p.x, p.yCam, p.zCam).normalized()
        val n2 = cross(t, n1)
        val base = positions.size / 3
        for (k in 0 until TUBE_SIDES) {
            val a = k * 2f * PI.toFloat() / TUBE_SIDES
            positions += p.x + radius * (cos(a) * n1.x + sin(a) * n2.x)
            positions += p.yCam + radius * (cos(a) * n1.y + sin(a) * n2.y)
            positions += p.zCam + radius * (cos(a) * n1.z + sin(a) * n2.z)
            uvs += i.toFloat() / path.size
            uvs += k.toFloat() / TUBE_SIDES
        }
        if (runStart >= 0) {
            val prevBase = base - TUBE_SIDES
            for (k in 0 until TUBE_SIDES) {
                val k1 = (k + 1) % TUBE_SIDES
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

internal data class OrbitScreenPosition(
    val x: Float,
    val y: Float,
    val zCam: Float,
)

/** Moon orbit position projected into screen space, for UI overlays (labels) aligned with the rendered orbit. */
internal fun computeOrbitScreenPosition(
    outputSizePx: Int,
    orbitDegrees: Float,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
): OrbitScreenPosition {
    val geometry = computeSceneGeometry(outputSizePx, earthSizeFraction)
    val orbit = transformMoonOrbitPosition(orbitDegrees, geometry.orbitRadius, geometry.viewPitchRad)
    val orbitScale = perspectiveScale(geometry.cameraZ, orbit.zCam)
    return OrbitScreenPosition(
        x = geometry.sceneHalf + orbit.x * orbitScale,
        y = geometry.sceneHalf - orbit.yCam * orbitScale,
        zCam = orbit.zCam,
    )
}
