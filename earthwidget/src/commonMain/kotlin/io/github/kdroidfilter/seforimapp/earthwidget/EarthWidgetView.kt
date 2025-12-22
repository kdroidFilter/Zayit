package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import seforimapp.earthwidget.generated.resources.Res
import seforimapp.earthwidget.generated.resources.earthmap1k
import seforimapp.earthwidget.generated.resources.moonmap2k
import seforimapp.earthwidget.generated.resources.earthwidget_light_label
import seforimapp.earthwidget.generated.resources.earthwidget_marker_latitude_label
import seforimapp.earthwidget.generated.resources.earthwidget_marker_longitude_label
import seforimapp.earthwidget.generated.resources.earthwidget_moon_from_marker_label
import seforimapp.earthwidget.generated.resources.earthwidget_moon_orbit_label
import seforimapp.earthwidget.generated.resources.earthwidget_rotation_label
import seforimapp.earthwidget.generated.resources.earthwidget_show_background_label
import seforimapp.earthwidget.generated.resources.earthwidget_show_orbit_label
import seforimapp.earthwidget.generated.resources.earthwidget_sun_elevation_label
import seforimapp.earthwidget.generated.resources.earthwidget_tilt_label
import kotlin.math.roundToInt

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
// MAIN WIDGET VIEW
// ============================================================================

/**
 * Interactive Earth widget with manual controls.
 *
 * Displays Earth and Moon with sliders to control rotation, lighting,
 * tilt, moon orbit position, and marker location.
 *
 * @param modifier Modifier for the widget container.
 * @param sphereSize Display size of the main sphere.
 * @param renderSizePx Internal render resolution.
 * @param initialMarkerLatitudeDegrees Starting marker latitude.
 * @param initialMarkerLongitudeDegrees Starting marker longitude.
 * @param initialRotationDegrees Starting Earth rotation.
 * @param initialLightDegrees Starting light direction.
 * @param initialSunElevationDegrees Starting sun elevation.
 * @param initialTiltDegrees Starting Earth tilt.
 * @param initialMoonOrbitDegrees Starting moon orbit position.
 */
@Composable
fun EarthWidgetView(
    modifier: Modifier = Modifier,
    sphereSize: Dp = 500.dp,
    renderSizePx: Int = 600,
    initialMarkerLatitudeDegrees: Float = DEFAULT_MARKER_LATITUDE,
    initialMarkerLongitudeDegrees: Float = DEFAULT_MARKER_LONGITUDE,
    initialRotationDegrees: Float = 0f,
    initialLightDegrees: Float = DEFAULT_LIGHT_DEGREES,
    initialSunElevationDegrees: Float = DEFAULT_SUN_ELEVATION,
    initialTiltDegrees: Float = DEFAULT_EARTH_TILT,
    initialMoonOrbitDegrees: Float = 0f,
) {
    // State for all controllable parameters
    var rotationDegrees by remember { mutableFloatStateOf(initialRotationDegrees) }
    var lightDegrees by remember { mutableFloatStateOf(initialLightDegrees) }
    var sunElevationDegrees by remember { mutableFloatStateOf(initialSunElevationDegrees) }
    var tiltDegrees by remember { mutableFloatStateOf(initialTiltDegrees) }
    var moonOrbitDegrees by remember { mutableFloatStateOf(initialMoonOrbitDegrees) }
    var markerLatitudeDegrees by remember {
        mutableFloatStateOf(initialMarkerLatitudeDegrees.coerceIn(-90f, 90f))
    }
    var markerLongitudeDegrees by remember {
        mutableFloatStateOf(initialMarkerLongitudeDegrees.coerceIn(-180f, 180f))
    }
    var showBackground by remember { mutableStateOf(true) }
    var showOrbitPath by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Main scene
        item {
            EarthWidgetScene(
                sphereSize = sphereSize,
                renderSizePx = renderSizePx,
                earthRotationDegrees = rotationDegrees,
                lightDegrees = lightDegrees,
                sunElevationDegrees = sunElevationDegrees,
                earthTiltDegrees = tiltDegrees,
                moonOrbitDegrees = moonOrbitDegrees,
                markerLatitudeDegrees = markerLatitudeDegrees,
                markerLongitudeDegrees = markerLongitudeDegrees,
                showBackgroundStars = showBackground,
                showOrbitPath = showOrbitPath,
            )
        }

        // Toggle controls
        item {
            LabeledCheckbox(
                checked = showBackground,
                onCheckedChange = { showBackground = it },
                label = stringResource(Res.string.earthwidget_show_background_label),
            )
        }

        item {
            LabeledCheckbox(
                checked = showOrbitPath,
                onCheckedChange = { showOrbitPath = it },
                label = stringResource(Res.string.earthwidget_show_orbit_label),
            )
        }

        // Slider controls
        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_rotation_label),
                value = rotationDegrees,
                onValueChange = { rotationDegrees = it },
                valueRange = 0f..360f,
            )
        }

        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_light_label),
                value = lightDegrees,
                onValueChange = { lightDegrees = it },
                valueRange = -180f..180f,
            )
        }

        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_sun_elevation_label),
                value = sunElevationDegrees,
                onValueChange = { sunElevationDegrees = it },
                valueRange = -90f..90f,
            )
        }

        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_tilt_label),
                value = tiltDegrees,
                onValueChange = { tiltDegrees = it },
                valueRange = -60f..60f,
            )
        }

        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_moon_orbit_label),
                value = moonOrbitDegrees,
                onValueChange = { moonOrbitDegrees = it },
                valueRange = 0f..360f,
            )
        }

        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_marker_latitude_label),
                value = markerLatitudeDegrees,
                onValueChange = { markerLatitudeDegrees = it },
                valueRange = -90f..90f,
            )
        }

        item {
            LabeledSlider(
                label = stringResource(Res.string.earthwidget_marker_longitude_label),
                value = markerLongitudeDegrees,
                onValueChange = { markerLongitudeDegrees = it },
                valueRange = -180f..180f,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

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
    showMoonFromMarker: Boolean = true,
    moonLightDegrees: Float = lightDegrees,
    moonSunElevationDegrees: Float = sunElevationDegrees,
    moonPhaseAngleDegrees: Float? = null,
    julianDay: Double? = null,
) {
    val animationSpec = tween<Float>(durationMillis = 260)
    val animatedEarthRotation = rememberAnimatedAngle(
        targetValue = earthRotationDegrees,
        durationMillis = 260,
        normalize = ::normalizeAngle360,
    )
    val animatedLightDegrees = rememberAnimatedAngle(
        targetValue = lightDegrees,
        durationMillis = 260,
        normalize = ::normalizeAngle180,
    )
    val animatedSunElevation by animateFloatAsState(
        targetValue = sunElevationDegrees,
        animationSpec = animationSpec,
        label = "sunElevation",
    )
    val animatedTiltDegrees by animateFloatAsState(
        targetValue = earthTiltDegrees,
        animationSpec = animationSpec,
        label = "tiltDegrees",
    )
    val animatedMoonOrbit = rememberAnimatedAngle(
        targetValue = moonOrbitDegrees,
        durationMillis = 260,
        normalize = ::normalizeAngle360,
    )
    val animatedMarkerLat by animateFloatAsState(
        targetValue = markerLatitudeDegrees,
        animationSpec = animationSpec,
        label = "markerLat",
    )
    val animatedMarkerLon = rememberAnimatedAngle(
        targetValue = markerLongitudeDegrees,
        durationMillis = 260,
        normalize = ::normalizeAngle180,
    )
    val animatedMoonLightDegrees = rememberAnimatedAngle(
        targetValue = moonLightDegrees,
        durationMillis = 260,
        normalize = ::normalizeAngle180,
    )
    val animatedMoonSunElevation by animateFloatAsState(
        targetValue = moonSunElevationDegrees,
        animationSpec = animationSpec,
        label = "moonSunElevation",
    )
    val animatedMoonPhaseAngle = moonPhaseAngleDegrees?.let {
        rememberAnimatedAngle(
            targetValue = it,
            durationMillis = 260,
            normalize = ::normalizeAngle360,
        )
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            bitmap = sphereImage,
            contentDescription = null,
            modifier = Modifier.size(sphereSize),
        )

        if (showMoonFromMarker) {
            Text(text = stringResource(Res.string.earthwidget_moon_from_marker_label))
            MoonFromMarkerWidgetView(
                sphereSize = moonViewSize,
                renderSizePx = moonRenderSizePx,
                earthRotationDegrees = animatedEarthRotation,
                lightDegrees = animatedLightDegrees,
                sunElevationDegrees = animatedSunElevation,
                earthTiltDegrees = animatedTiltDegrees,
                moonOrbitDegrees = animatedMoonOrbit,
                markerLatitudeDegrees = animatedMarkerLat,
                markerLongitudeDegrees = animatedMarkerLon,
                showBackgroundStars = showBackgroundStars,
                moonLightDegrees = animatedMoonLightDegrees,
                moonSunElevationDegrees = animatedMoonSunElevation,
                moonPhaseAngleDegrees = animatedMoonPhaseAngle,
                julianDay = julianDay,
            )
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
// REUSABLE UI COMPONENTS
// ============================================================================

/**
 * A slider with label and current value display.
 *
 * @param label Text label for the slider.
 * @param value Current slider value.
 * @param onValueChange Callback when value changes.
 * @param valueRange Range of valid values.
 * @param modifier Modifier for the container.
 */
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label)
            Text(text = value.roundToInt().toString())
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

/**
 * A checkbox with accompanying label.
 *
 * @param checked Whether the checkbox is checked.
 * @param onCheckedChange Callback when check state changes.
 * @param label Text label for the checkbox.
 * @param modifier Modifier for the container.
 */
@Composable
private fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label)
    }
}

// ============================================================================
// TEXTURE LOADING
// ============================================================================

/**
 * Loads and caches the Earth texture.
 */
@Composable
private fun rememberEarthTexture(): EarthTexture? {
    val image = imageResource(Res.drawable.earthmap1k)
    return remember(image) { earthTextureFromImageBitmap(image) }
}

/**
 * Loads and caches the Moon texture.
 */
@Composable
private fun rememberMoonTexture(): EarthTexture? {
    val image = imageResource(Res.drawable.moonmap2k)
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
private fun rememberAnimatedAngle(
    targetValue: Float,
    durationMillis: Int,
    normalize: (Float) -> Float,
): Float {
    var unwrappedTarget by remember { mutableFloatStateOf(targetValue) }

    val animated by animateFloatAsState(
        targetValue = unwrappedTarget,
        animationSpec = tween(durationMillis = durationMillis),
        label = "animatedAngle",
    )

    LaunchedEffect(targetValue) {
        val currentWrapped = normalize(animated)
        var delta = targetValue - currentWrapped
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        unwrappedTarget = animated + delta
    }

    return normalize(animated)
}
