package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.imageResource
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthmap
import seforimapp.earthwidget.generated.resources.moonmap
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

// ============================================================================
// SCENE COMPOSABLE
// ============================================================================

/**
 * Renders the Earth-Moon scene with optional moon-from-marker view.
 *
 * @param modifier Modifier for the scene container.
 * @param sphereSize Display size of the main sphere.
 * @param renderSizePx Internal render resolution.
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
    modifier: Modifier = Modifier,
    sphereSize: Dp = 500.dp,
    renderSizePx: Int = 600,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    showBackgroundStars: Boolean,
    showOrbitPath: Boolean,
    orbitLabels: List<OrbitLabelData> = emptyList(),
    onOrbitLabelClick: ((OrbitLabelData) -> Unit)? = null,
    showMoonFromMarker: Boolean = true,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
    animateEarthRotation: Boolean = true,
    moonFromMarkerLightDegrees: Float? = null,
    moonFromMarkerSunElevationDegrees: Float? = null,
) {
    val smoothSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // Earth rotation and light can be instant (during drag) or animated (location change)
    val animatedEarthRotation = if (animateEarthRotation) {
        rememberSmoothAnimatedAngle(
            targetValue = earthRotationDegrees,
            normalize = ::normalizeAngle360,
        )
    } else {
        normalizeAngle360(earthRotationDegrees)
    }
    val animatedLightDegrees = if (animateEarthRotation) {
        rememberSmoothAnimatedAngle(
            targetValue = lightDegrees,
            normalize = ::normalizeAngle180,
        )
    } else {
        normalizeAngle180(lightDegrees)
    }
    val animatedSunElevation by animateFloatAsState(
        targetValue = sunElevationDegrees,
        animationSpec = smoothSpec,
        label = "sunElevation",
    )
    val animatedTiltDegrees by animateFloatAsState(
        targetValue = earthTiltDegrees,
        animationSpec = smoothSpec,
        label = "tiltDegrees",
    )
    val animatedMoonOrbit = rememberSmoothAnimatedAngle(
        targetValue = moonOrbitDegrees,
        normalize = ::normalizeAngle360,
    )
    val animatedMarkerLat by animateFloatAsState(
        targetValue = markerLatitudeDegrees,
        animationSpec = smoothSpec,
        label = "markerLat",
    )
    val animatedMarkerLon = rememberSmoothAnimatedAngle(
        targetValue = markerLongitudeDegrees,
        normalize = ::normalizeAngle180,
    )
    val animatedMoonLightDegrees = rememberSmoothAnimatedAngle(
        targetValue = moonLightDegrees,
        normalize = ::normalizeAngle180,
    )
    val animatedMoonSunElevation by animateFloatAsState(
        targetValue = moonSunElevationDegrees,
        animationSpec = smoothSpec,
        label = "moonSunElevation",
    )
    val animatedMoonPhaseAngle = moonPhaseAngleDegrees?.let {
        rememberSmoothAnimatedAngle(targetValue = it, normalize = ::normalizeAngle360)
    }

    val earthTexture = rememberEarthTexture()
    val moonTexture = rememberMoonTexture()

    val moonViewSize = sphereSize * MOON_VIEW_SIZE_RATIO
    val moonRenderSizePx = (renderSizePx * MOON_RENDER_SIZE_RATIO)
        .roundToInt()
        .coerceAtLeast(MIN_MOON_RENDER_SIZE_PX)

    val sphereImage = rememberEarthMoonImage(
        earthTexture = earthTexture,
        moonTexture = moonTexture,
        renderSizePx = renderSizePx,
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
    )

    val earthContent: @Composable () -> Unit = {
        Box(modifier = Modifier.size(sphereSize)) {
            Image(
                bitmap = sphereImage,
                contentDescription = null,
                modifier = Modifier.size(sphereSize),
            )
            if (showOrbitPath && orbitLabels.isNotEmpty()) {
                OrbitDayLabelsOverlay(
                    renderSizePx = renderSizePx,
                    sphereSize = sphereSize,
                    labels = orbitLabels,
                    onLabelClick = onOrbitLabelClick,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }

    val moonContent: @Composable () -> Unit = {
        val moonLightForMarker = moonFromMarkerLightDegrees ?: animatedMoonLightDegrees
        val moonSunElevationForMarker = moonFromMarkerSunElevationDegrees ?: animatedMoonSunElevation
        // Moon-from-marker view uses the actual marker longitude (not the visual Earth rotation)
        // This ensures the moon phase is always calculated from the marker's real position
        MoonFromMarkerWidgetView(
            sphereSize = moonViewSize,
            renderSizePx = moonRenderSizePx,
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
// MOON FROM MARKER VIEW
// ============================================================================

/**
 * Displays the Moon as seen from the marker's position on Earth.
 *
 * @param modifier Modifier for the view.
 * @param sphereSize Display size.
 * @param renderSizePx Internal render resolution.
 * @param earthRotationDegrees Earth rotation.
 * @param lightDegrees Sun azimuth.
 * @param sunElevationDegrees Sun elevation.
 * @param earthTiltDegrees Earth tilt.
 * @param moonOrbitDegrees Moon orbit position.
 * @param markerLatitudeDegrees Observer latitude.
 * @param markerLongitudeDegrees Observer longitude.
 * @param showBackgroundStars Whether to show starfield.
 * @param moonLightDegrees Override for moon light.
 * @param moonSunElevationDegrees Override for moon sun elevation.
 * @param moonPhaseAngleDegrees Moon phase angle.
 * @param julianDay Julian day for ephemeris.
 */
@Composable
fun MoonFromMarkerWidgetView(
    modifier: Modifier = Modifier,
    sphereSize: Dp = 220.dp,
    renderSizePx: Int = 320,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    showBackgroundStars: Boolean = true,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
) {
    val moonTexture = rememberMoonTexture()

    val moonImage = rememberMoonFromMarkerImage(
        moonTexture = moonTexture,
        renderSizePx = renderSizePx,
        earthRotationDegrees = earthRotationDegrees,
        lightDegrees = lightDegrees,
        sunElevationDegrees = sunElevationDegrees,
        earthTiltDegrees = earthTiltDegrees,
        moonOrbitDegrees = moonOrbitDegrees,
        markerLatitudeDegrees = markerLatitudeDegrees,
        markerLongitudeDegrees = markerLongitudeDegrees,
        showBackgroundStars = showBackgroundStars,
        moonLightDegrees = moonLightDegrees,
        moonSunElevationDegrees = moonSunElevationDegrees,
        moonPhaseAngleDegrees = moonPhaseAngleDegrees,
        julianDay = julianDay,
    )

    Image(
        bitmap = moonImage,
        contentDescription = null,
        modifier = modifier.size(sphereSize),
    )
}

// ============================================================================
// TEXTURE LOADING
// ============================================================================

/**
 * Loads and caches the Earth texture.
 */
@Composable
private fun rememberEarthTexture(): EarthTexture? {
    val image = imageResource(Res.drawable.earthmap)
    return remember(image) { earthTextureFromImageBitmap(image) }
}

/**
 * Loads and caches the Moon texture.
 */
@Composable
private fun rememberMoonTexture(): EarthTexture? {
    val image = imageResource(Res.drawable.moonmap)
    return remember(image) { earthTextureFromImageBitmap(image) }
}

// ============================================================================
// IMAGE RENDERING CACHE
// ============================================================================

/**
 * Renders and caches the Moon-from-marker view image.
 */
@Composable
private fun rememberMoonFromMarkerImage(
    moonTexture: EarthTexture?,
    renderSizePx: Int,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    showBackgroundStars: Boolean,
    moonLightDegrees: Float,
    moonSunElevationDegrees: Float,
    moonPhaseAngleDegrees: Float?,
    julianDay: Double?,
): ImageBitmap {
    return remember(
        moonTexture,
        renderSizePx,
        earthRotationDegrees,
        lightDegrees,
        sunElevationDegrees,
        earthTiltDegrees,
        moonOrbitDegrees,
        markerLatitudeDegrees,
        markerLongitudeDegrees,
        showBackgroundStars,
        moonLightDegrees,
        moonSunElevationDegrees,
        moonPhaseAngleDegrees,
        julianDay,
    ) {
        val argb = renderMoonFromMarkerArgb(
            moonTexture = moonTexture,
            outputSizePx = renderSizePx,
            lightDegrees = lightDegrees,
            sunElevationDegrees = sunElevationDegrees,
            earthRotationDegrees = earthRotationDegrees,
            earthTiltDegrees = earthTiltDegrees,
            moonOrbitDegrees = moonOrbitDegrees,
            markerLatitudeDegrees = markerLatitudeDegrees,
            markerLongitudeDegrees = markerLongitudeDegrees,
            showBackgroundStars = showBackgroundStars,
            moonLightDegrees = moonLightDegrees,
            moonSunElevationDegrees = moonSunElevationDegrees,
            moonPhaseAngleDegrees = moonPhaseAngleDegrees,
            julianDay = julianDay,
        )
        imageBitmapFromArgb(argb, renderSizePx, renderSizePx)
    }
}

/**
 * Renders and caches the Earth-Moon composite image.
 */
@Composable
private fun rememberEarthMoonImage(
    earthTexture: EarthTexture?,
    moonTexture: EarthTexture?,
    renderSizePx: Int,
    earthRotationDegrees: Float,
    lightDegrees: Float,
    sunElevationDegrees: Float,
    earthTiltDegrees: Float,
    moonOrbitDegrees: Float,
    markerLatitudeDegrees: Float,
    markerLongitudeDegrees: Float,
    showBackgroundStars: Boolean,
    showOrbitPath: Boolean,
    moonLightDegrees: Float,
    moonSunElevationDegrees: Float,
    moonPhaseAngleDegrees: Float?,
    julianDay: Double?,
): ImageBitmap {
    return remember(
        earthTexture,
        moonTexture,
        renderSizePx,
        earthRotationDegrees,
        lightDegrees,
        sunElevationDegrees,
        earthTiltDegrees,
        moonOrbitDegrees,
        markerLatitudeDegrees,
        markerLongitudeDegrees,
        showBackgroundStars,
        showOrbitPath,
        moonLightDegrees,
        moonSunElevationDegrees,
        moonPhaseAngleDegrees,
        julianDay,
    ) {
        val argb = renderEarthWithMoonArgb(
            earthTexture = earthTexture,
            moonTexture = moonTexture,
            outputSizePx = renderSizePx,
            earthRotationDegrees = earthRotationDegrees,
            lightDegrees = lightDegrees,
            sunElevationDegrees = sunElevationDegrees,
            earthTiltDegrees = earthTiltDegrees,
            moonOrbitDegrees = moonOrbitDegrees,
            markerLatitudeDegrees = markerLatitudeDegrees,
            markerLongitudeDegrees = markerLongitudeDegrees,
            showBackgroundStars = showBackgroundStars,
            showOrbitPath = showOrbitPath,
            moonLightDegrees = moonLightDegrees,
            moonSunElevationDegrees = moonSunElevationDegrees,
            moonPhaseAngleDegrees = moonPhaseAngleDegrees,
            julianDay = julianDay,
        )
        imageBitmapFromArgb(argb, renderSizePx, renderSizePx)
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
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val animatable = remember { Animatable(normalize(targetValue)) }

    LaunchedEffect(targetValue) {
        val current = animatable.value
        val currentWrapped = normalize(current)
        val targetWrapped = normalize(targetValue)

        var delta = targetWrapped - currentWrapped
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        val newTarget = current + delta
        animatable.animateTo(
            targetValue = newTarget,
            animationSpec = springSpec,
            initialVelocity = animatable.velocity,
        )
    }

    return normalize(animatable.value)
}

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
) {
    if (labels.isEmpty() || renderSizePx <= 0) return
    val fontSize = (sphereSize.value * 0.025f).coerceIn(9f, 16f).sp
    val textStyle = remember(fontSize) {
        TextStyle(
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 3f),
        )
    }

    val labelPositions = remember(labels, renderSizePx) {
        val center = renderSizePx / 2f
        val outwardPx = 12f

        labels.map { label ->
            val p = computeOrbitScreenPosition(
                outputSizePx = renderSizePx,
                orbitDegrees = label.orbitDegrees,
            )
            val dx = p.x - center
            val dy = p.y - center
            val len = sqrt(dx * dx + dy * dy)

            val ox = if (len > 1e-3f) dx / len * outwardPx else 0f
            val oy = if (len > 1e-3f) dy / len * outwardPx else 0f

            Offset(x = p.x + ox, y = p.y + oy)
        }
    }

    Layout(
        modifier = modifier,
        content = {
            for (label in labels) {
                key(label.dayOfMonth) {
                    BasicText(
                        text = label.text,
                        style = textStyle,
                        modifier = if (onLabelClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable { onLabelClick(label) }
                        },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val scaleX = width / renderSizePx.toFloat()
        val scaleY = height / renderSizePx.toFloat()
        val placeables = measurables.map { measurable ->
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
