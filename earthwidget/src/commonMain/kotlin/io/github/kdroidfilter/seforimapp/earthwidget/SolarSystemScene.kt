package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.erkko68.filament.Engine
import io.github.erkko68.filament.compose.FilamentSceneView
import io.github.erkko68.filament.compose.scene.Direction
import io.github.erkko68.filament.compose.scene.DirectionalLight
import io.github.erkko68.filament.compose.scene.LightIntensity
import io.github.erkko68.filament.compose.scene.Position
import io.github.erkko68.filament.compose.scene.Projection
import io.github.erkko68.filament.compose.scene.Rotation
import io.github.erkko68.filament.compose.scene.SphericalHarmonics
import io.github.erkko68.filament.compose.scene.primitives.Sphere
import io.github.erkko68.filament.compose.scene.rememberCameraState
import io.github.erkko68.filament.compose.scene.rememberIndirectLightState
import io.github.erkko68.filament.compose.scene.rememberTexturedMaterialInstance
import io.github.erkko68.filament.compose.scene.rememberUnlitColorMaterialInstance
import io.github.erkko68.filament.compose.scene.toLinearColor
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import io.github.erkko68.filament.Camera as FilamentCamera

private const val SUN_COLOR_RGB = 0xFFD27A

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
    val markerRadius = widthPx * 0.0045f

    /** Thickness of a holiday's stretch of orbit. */
    val arcRadius = widthPx * 0.0065f
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
    val viewAzimuthDegrees: Float,
    val viewElevationDegrees: Float,
    val viewZoom: Float,
    val markers: List<SolarOrbitMarker>,
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
    textures: WidgetTextures,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(state.widthPx, state.heightPx) { SolarGeometry(state.widthPx, state.heightPx) }
    val camera =
        rememberCameraState(
            initialEye = Position(0f, 0f, geometry.cameraZ),
            initialExposure = UnitExposure,
        )
    SideEffect {
        camera.eye = Position(0f, 0f, geometry.cameraZ)
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
                tubeMesh(path, geometry.arcRadius) to dimmed(marker.colorRgb, alpha)
            }
        }

    Box(modifier) {
        Starfield(Modifier.matchParentSize())
        // Soft corona behind the Sun (always at the centre of the view)
        Canvas(Modifier.matchParentSize()) {
            val radius = geometry.sunRadius * state.viewZoom * 1.8f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to Color(0xFF000000.toInt() or SUN_COLOR_RGB).copy(alpha = 0.45f),
                        1f to Color.Transparent,
                        center = Offset(geometry.halfWidth, geometry.halfHeight),
                        radius = radius,
                    ),
                radius = radius,
                center = Offset(geometry.halfWidth, geometry.halfHeight),
            )
        }
        FilamentSceneView(
            modifier = Modifier.matchParentSize(),
            engine = engine,
            cameraState = camera,
            indirectLightState = ambient,
            postProcessing = WidgetPostProcessing,
            shadows = null,
            transparent = true,
        ) {
            // Sunlight travels from the Sun (origin) out to the Earth; the Moon is close enough to share it.
            DirectionalLight(
                direction = view * earthDir,
                // A touch stronger than the Earth widget's: the globe is small here
                intensity = LightIntensity.LuminousPower(DEFAULT_DIFFUSE_STRENGTH * 1.2f * LUX_PER_DIFFUSE_UNIT),
            )
            Sphere(
                material = rememberUnlitColorMaterialInstance(Color(0xFF000000.toInt() or SUN_COLOR_RGB).toLinearColor()),
                radius = geometry.sunRadius,
                castShadows = false,
                receiveShadows = false,
            )
            textures.earth?.let { texture ->
                MeshNode(
                    material = rememberTexturedMaterialInstance(texture, roughness = 0.7f, sampler = BilinearRepeat),
                    mesh = UnitSphereMesh,
                    scale = geometry.earthRadius,
                    position = Position(earthPos.x, earthPos.y, earthPos.z),
                    rotation = view * earthToWorld(state.siderealDegrees, state.obliquityDegrees),
                )
            }
            textures.moon?.let { texture ->
                MeshNode(
                    material = rememberTexturedMaterialInstance(texture, roughness = 1f, sampler = BilinearRepeat),
                    mesh = UnitSphereMesh,
                    scale = geometry.moonRadius,
                    position = Position(moonPos.x, moonPos.y, moonPos.z),
                    // Tidally locked: the near side (body +Z) faces the Earth.
                    rotation = view * Rotation.axisAngle(Direction.Up, atan2(-moonDir.x, -moonDir.z) * RAD_TO_DEG_F),
                )
            }
            Orbit(orbitMeshes, 0xFFFFFF)
            arcs.forEachIndexed { i, (mesh, color) ->
                key(i) { mesh?.let { MeshNode(rememberUnlitColorMaterialInstance(color), it, 1f) } }
            }
            for (marker in state.markers.filter { !it.isArc }) {
                val p = view * eclipticDirection(marker.startDegrees) * geometry.orbitRadius
                Sphere(
                    material = rememberUnlitColorMaterialInstance(Color(0xFF000000.toInt() or marker.colorRgb).toLinearColor()),
                    radius = geometry.markerRadius,
                    position = Position(p.x, p.y, p.z),
                    castShadows = false,
                    receiveShadows = false,
                )
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
    val y = geometry.halfHeight - p.y * scale
    return OrbitScreenPosition(
        x = x,
        y = y,
        zCam = p.z,
        depth = orbitDepth(p.z, geometry.orbitRadius),
        hiddenByEarth = p.z < 0f && hypot(x - geometry.halfWidth, y - geometry.halfHeight) < geometry.sunRadius * state.viewZoom,
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
