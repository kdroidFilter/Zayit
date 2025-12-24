package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundles textures required for rendering Earth and Moon.
 */
internal data class EarthWidgetTextures(
    val earth: EarthTexture?,
    val moon: EarthTexture?,
)

/**
 * Rendering parameters for the Earth + Moon composite scene.
 */
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
)

/**
 * Rendering parameters for the Moon-from-marker inset view.
 */
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
)

/**
 * CPU renderer that produces ImageBitmaps from rendering state on a background dispatcher.
 */
internal class EarthWidgetRenderer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun renderScene(
        state: EarthRenderState,
        textures: EarthWidgetTextures,
    ): ImageBitmap = withContext(dispatcher) {
        val size = state.renderSizePx
        val argb = renderEarthWithMoonArgb(
            earthTexture = textures.earth,
            moonTexture = textures.moon,
            outputSizePx = size,
            earthRotationDegrees = state.earthRotationDegrees,
            lightDegrees = state.lightDegrees,
            sunElevationDegrees = state.sunElevationDegrees,
            earthTiltDegrees = state.earthTiltDegrees,
            moonOrbitDegrees = state.moonOrbitDegrees,
            markerLatitudeDegrees = state.markerLatitudeDegrees,
            markerLongitudeDegrees = state.markerLongitudeDegrees,
            showBackgroundStars = state.showBackgroundStars,
            showOrbitPath = state.showOrbitPath,
            moonLightDegrees = state.moonLightDegrees,
            moonSunElevationDegrees = state.moonSunElevationDegrees,
            moonPhaseAngleDegrees = state.moonPhaseAngleDegrees,
            julianDay = state.julianDay,
        )
        imageBitmapFromArgb(argb, size, size)
    }

    suspend fun renderMoonFromMarker(
        state: MoonFromMarkerRenderState,
        moonTexture: EarthTexture?,
    ): ImageBitmap = withContext(dispatcher) {
        val size = state.renderSizePx
        val argb = renderMoonFromMarkerArgb(
            moonTexture = moonTexture,
            outputSizePx = size,
            earthRotationDegrees = state.earthRotationDegrees,
            lightDegrees = state.lightDegrees,
            sunElevationDegrees = state.sunElevationDegrees,
            earthTiltDegrees = state.earthTiltDegrees,
            moonOrbitDegrees = state.moonOrbitDegrees,
            markerLatitudeDegrees = state.markerLatitudeDegrees,
            markerLongitudeDegrees = state.markerLongitudeDegrees,
            showBackgroundStars = state.showBackgroundStars,
            moonLightDegrees = state.moonLightDegrees,
            moonSunElevationDegrees = state.moonSunElevationDegrees,
            moonPhaseAngleDegrees = state.moonPhaseAngleDegrees,
            julianDay = state.julianDay,
        )
        imageBitmapFromArgb(argb, size, size)
    }
}
