package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.erkko68.filament.compose.rememberFilamentEngine
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ============================================================================
// SHARED ANIMATION SPECS
// ============================================================================

/** Shared spring spec for smooth angle animations. */
private val SmoothAngleSpringSpec =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

// ============================================================================
// DEFAULT VALUES
// ============================================================================

/** Default marker latitude (Jerusalem). */
private const val DEFAULT_MARKER_LATITUDE = 31.7683f

/** Default marker longitude (Jerusalem). */
private const val DEFAULT_MARKER_LONGITUDE = 35.2137f

/** Default Earth axial tilt in degrees. */
private const val DEFAULT_EARTH_TILT = 23.44f

/** Default sun light direction in degrees. */
private const val DEFAULT_LIGHT_DEGREES = 30f

/** Default sun elevation in degrees. */
private const val DEFAULT_SUN_ELEVATION = 12f

/** Moon view size ratio relative to main sphere. */
private const val MOON_VIEW_SIZE_RATIO = 0.45f

/** Moon render size ratio relative to main render size. */
private const val MOON_RENDER_SIZE_RATIO = 0.5f

/** Minimum moon render size in pixels. */
private const val MIN_MOON_RENDER_SIZE_PX = 120

/** Minimum scene size (geometry units). */
private const val MIN_RENDER_SIZE_PX = 160

// ============================================================================
// SCENE COMPOSABLE
// ============================================================================

/**
 * Renders the Earth-Moon scene with optional moon-from-marker view.
 *
 * @param modifier Modifier for the scene container.
 * @param sphereSize Display size of the main sphere.
 * @param renderSizePx Base internal render resolution.
 * @param earthRotationDegrees Earth rotation angle.
 * @param lightDegrees Sun azimuth direction.
 * @param sunElevationDegrees Sun elevation angle.
 * @param earthTiltDegrees Earth axial tilt.
 * @param moonOrbitDegrees Moon position on orbit.
 * @param markerLatitudeDegrees Marker latitude.
 * @param markerLongitudeDegrees Marker longitude.
 * @param showBackgroundStars Whether to show starfield.
 * @param showOrbitPath Whether to show orbit line.
 * @param orbitLabels Labels to draw along the orbit path.
 * @param showMoonFromMarker Whether to show moon-from-marker view.
 * @param moonLightDegrees Override for moon light direction.
 * @param moonSunElevationDegrees Override for moon sun elevation.
 * @param moonPhaseAngleDegrees Moon phase angle for lighting.
 * @param julianDay Julian day for ephemeris calculations.
 */
@Composable
fun EarthWidgetScene(
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    showBackgroundStars: Boolean,
    showOrbitPath: Boolean,
    modifier: Modifier = Modifier,
    sphereSize: Dp = 500.dp,
    renderSizePx: Int = 600,
    orbitLabels: List<OrbitLabelData> = emptyList(),
    onOrbitLabelClick: ((OrbitLabelData) -> Unit)? = null,
    showMoonFromMarker: Boolean = true,
    showMoonInOrbit: Boolean = true,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
    animateEarthRotation: Boolean = true,
    moonFromMarkerLightDegrees: Float? = null,
    moonFromMarkerSunElevationDegrees: Float? = null,
    kiddushLevanaStartDegrees: Float? = null,
    kiddushLevanaEndDegrees: Float? = null,
    kiddushLevanaColorRgb: Int = KIDDUSH_LEVANA_COLOR_RGB,
) {
    // Earth rotation and light can be instant (during drag) or animated (location change)
    val animatedEarthRotation =
        if (animateEarthRotation) {
            rememberSmoothAnimatedAngle(
                targetValue = earthRotationDegrees,
                normalize = ::normalizeAngle360,
            )
        } else {
            normalizeAngle360(earthRotationDegrees)
        }
    val animatedLightDegrees =
        if (animateEarthRotation) {
            rememberSmoothAnimatedAngle(
                targetValue = lightDegrees,
                normalize = ::normalizeAngle180,
            )
        } else {
            normalizeAngle180(lightDegrees)
        }
    val animatedSunElevation by animateFloatAsState(
        targetValue = sunElevationDegrees,
        animationSpec = SmoothAngleSpringSpec,
        label = "sunElevation",
    )
    val animatedTiltDegrees by animateFloatAsState(
        targetValue = earthTiltDegrees,
        animationSpec = SmoothAngleSpringSpec,
        label = "tiltDegrees",
    )
    val animatedMoonOrbit =
        rememberSmoothAnimatedAngle(
            targetValue = moonOrbitDegrees,
            normalize = ::normalizeAngle360,
        )
    val animatedMarkerLat by animateFloatAsState(
        targetValue = markerLatitudeDegrees,
        animationSpec = SmoothAngleSpringSpec,
        label = "markerLat",
    )
    val animatedMarkerLon =
        rememberSmoothAnimatedAngle(
            targetValue = markerLongitudeDegrees,
            normalize = ::normalizeAngle180,
        )
    val animatedMoonLightDegrees =
        rememberSmoothAnimatedAngle(
            targetValue = moonLightDegrees,
            normalize = ::normalizeAngle180,
        )
    val animatedMoonSunElevation by animateFloatAsState(
        targetValue = moonSunElevationDegrees,
        animationSpec = SmoothAngleSpringSpec,
        label = "moonSunElevation",
    )
    val animatedMoonPhaseAngle =
        moonPhaseAngleDegrees?.let {
            rememberSmoothAnimatedAngle(targetValue = it, normalize = ::normalizeAngle360)
        }

    val engine = rememberFilamentEngine()
    val textures = rememberWidgetTextures(engine)

    val moonViewSize = sphereSize * MOON_VIEW_SIZE_RATIO
    val resolvedEarthRenderSize = renderSizePx.coerceAtLeast(MIN_RENDER_SIZE_PX)
    val resolvedMoonRenderSize =
        (resolvedEarthRenderSize * MOON_RENDER_SIZE_RATIO)
            .roundToInt()
            .coerceAtLeast(MIN_MOON_RENDER_SIZE_PX)

    val sceneState =
        EarthRenderState(
            renderSizePx = resolvedEarthRenderSize,
            earthRotationDegrees = animatedEarthRotation,
            lightDegrees = animatedLightDegrees,
            sunElevationDegrees = animatedSunElevation,
            earthTiltDegrees = animatedTiltDegrees,
            moonOrbitDegrees = animatedMoonOrbit,
            markerLatitudeDegrees = animatedMarkerLat,
            markerLongitudeDegrees = animatedMarkerLon,
            showBackgroundStars = showBackgroundStars,
            showOrbitPath = showOrbitPath,
            moonLightDegrees = animatedMoonLightDegrees,
            moonSunElevationDegrees = animatedMoonSunElevation,
            moonPhaseAngleDegrees = animatedMoonPhaseAngle,
            julianDay = julianDay,
            earthSizeFraction = earthSizeFraction,
            kiddushLevanaStartDegrees = kiddushLevanaStartDegrees,
            kiddushLevanaEndDegrees = kiddushLevanaEndDegrees,
            kiddushLevanaColorRgb = kiddushLevanaColorRgb,
        )

    val earthContent: @Composable () -> Unit = {
        Box(modifier = Modifier.size(sphereSize)) {
            EarthMoonSceneView(
                state = sceneState,
                engine = engine,
                textures = textures,
                showMoon = showMoonInOrbit,
                modifier = Modifier.size(sphereSize),
            )
            if (showOrbitPath && orbitLabels.isNotEmpty()) {
                OrbitDayLabelsOverlay(
                    renderSizePx = sceneState.renderSizePx,
                    sphereSize = sphereSize,
                    labels = orbitLabels,
                    onLabelClick = onOrbitLabelClick,
                    earthSizeFraction = earthSizeFraction,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }

    val moonContent: @Composable () -> Unit = {
        val moonLightForMarker = moonFromMarkerLightDegrees ?: animatedMoonLightDegrees
        val moonSunElevationForMarker = moonFromMarkerSunElevationDegrees ?: animatedMoonSunElevation
        val moonState =
            MoonFromMarkerRenderState(
                renderSizePx = resolvedMoonRenderSize,
                earthRotationDegrees = animatedMarkerLon, // Use marker position, not visual rotation
                lightDegrees = moonLightForMarker,
                sunElevationDegrees = moonSunElevationForMarker,
                earthTiltDegrees = animatedTiltDegrees,
                moonOrbitDegrees = animatedMoonOrbit,
                markerLatitudeDegrees = animatedMarkerLat,
                markerLongitudeDegrees = animatedMarkerLon,
                showBackgroundStars = showBackgroundStars,
                moonLightDegrees = moonLightForMarker,
                moonSunElevationDegrees = moonSunElevationForMarker,
                moonPhaseAngleDegrees = animatedMoonPhaseAngle,
                julianDay = julianDay,
                earthSizeFraction = earthSizeFraction,
            )
        // Moon-from-marker view uses the actual marker longitude (not the visual Earth rotation)
        // This ensures the moon phase is always calculated from the marker's real position
        MoonFromMarkerSceneView(
            state = moonState,
            engine = engine,
            moonTexture = textures.moon,
            modifier = Modifier.size(moonViewSize),
        )
    }

    val spacing = 16.dp
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val showSideBySide = showMoonFromMarker && maxWidth >= sphereSize + moonViewSize + spacing

        if (showSideBySide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                earthContent()
                moonContent()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                earthContent()
                if (showMoonFromMarker) {
                    moonContent()
                }
            }
        }
    }
}

// ============================================================================
// ANIMATION HELPERS
// ============================================================================

private fun normalizeAngle360(value: Float): Float {
    val mod = value % 360f
    return if (mod < 0f) mod + 360f else mod
}

private fun normalizeAngle180(value: Float): Float {
    var wrapped = normalizeAngle360(value)
    if (wrapped > 180f) wrapped -= 360f
    return wrapped
}

@Composable
private fun rememberSmoothAnimatedAngle(
    targetValue: Float,
    normalize: (Float) -> Float,
): Float {
    val currentNormalize by rememberUpdatedState(normalize)
    val animatable = remember { Animatable(currentNormalize(targetValue)) }

    LaunchedEffect(targetValue) {
        val current = animatable.value
        val currentWrapped = currentNormalize(current)
        val targetWrapped = currentNormalize(targetValue)

        var delta = targetWrapped - currentWrapped
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        val newTarget = current + delta
        animatable.animateTo(
            targetValue = newTarget,
            animationSpec = SmoothAngleSpringSpec,
            initialVelocity = animatable.velocity,
        )
    }

    return normalize(animatable.value)
}

@Immutable
data class OrbitLabelData(
    val orbitDegrees: Float,
    val text: String,
    val dayOfMonth: Int,
)

@Composable
private fun OrbitDayLabelsOverlay(
    renderSizePx: Int,
    sphereSize: Dp,
    labels: List<OrbitLabelData>,
    onLabelClick: ((OrbitLabelData) -> Unit)?,
    modifier: Modifier = Modifier,
    earthSizeFraction: Float = EARTH_SIZE_FRACTION,
) {
    if (labels.isEmpty() || renderSizePx <= 0) return
    val fontSize = (sphereSize.value * 0.032f).coerceIn(11f, 20f).sp
    val textStyle =
        remember(fontSize) {
            TextStyle(
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 3f),
            )
        }

    val labelPositions =
        remember(labels, renderSizePx) {
            val center = renderSizePx / 2f
            val outwardPx = 12f

            labels.map { label ->
                val p =
                    computeOrbitScreenPosition(
                        outputSizePx = renderSizePx,
                        orbitDegrees = label.orbitDegrees,
                        earthSizeFraction = earthSizeFraction,
                    )
                val dx = p.x - center
                val dy = p.y - center
                val len = sqrt(dx * dx + dy * dy)

                val ox = if (len > 1e-3f) dx / len * outwardPx else 0f
                val oy = if (len > 1e-3f) dy / len * outwardPx else 0f

                Offset(x = p.x + ox, y = p.y + oy)
            }
        }

    val hoveredTextStyle =
        remember(fontSize) {
            TextStyle(
                color = Color(0xFFFFD700), // Gold color on hover
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 4f),
            )
        }

    Layout(
        modifier = modifier,
        content = {
            for (label in labels) {
                key(label.dayOfMonth) {
                    OrbitDayLabel(
                        label = label,
                        textStyle = textStyle,
                        hoveredTextStyle = hoveredTextStyle,
                        onClick = onLabelClick,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val scaleX = width / renderSizePx.toFloat()
        val scaleY = height / renderSizePx.toFloat()
        val placeables =
            measurables.map { measurable ->
                measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            }

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val p = labelPositions.getOrNull(index) ?: return@forEachIndexed
                val x = (p.x * scaleX - placeable.width / 2f).roundToInt()
                val y = (p.y * scaleY - placeable.height / 2f).roundToInt()
                // Absolute pixel placement; do not mirror in RTL.
                placeable.place(x, y)
            }
        }
    }
}

/**
 * Individual orbit day label with hover effect and expanded click area.
 */
@Composable
private fun OrbitDayLabel(
    label: OrbitLabelData,
    textStyle: TextStyle,
    hoveredTextStyle: TextStyle,
    onClick: ((OrbitLabelData) -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val currentStyle = if (isHovered && onClick != null) hoveredTextStyle else textStyle

    Box(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .then(
                    if (onClick != null) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .hoverable(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onClick(label) }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label.text,
            style = currentStyle,
        )
    }
}
