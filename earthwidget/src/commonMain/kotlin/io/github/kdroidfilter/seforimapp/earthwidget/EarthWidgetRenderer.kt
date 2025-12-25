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
 *
 * Uses a buffer pool to reduce memory allocations during repeated renders.
 * Intermediate buffers (Earth, Moon spheres) and output buffers are pooled
 * and reused across frames, significantly reducing GC pressure.
 *
 * @param dispatcher Coroutine dispatcher for background rendering.
 * @param bufferPool Optional buffer pool; defaults to global shared pool.
 */
internal class EarthWidgetRenderer(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val bufferPool: PixelBufferPool = globalPixelBufferPool,
) {
    /**
     * Renders the Earth-Moon composite scene.
     *
     * @param state Rendering parameters.
     * @param textures Earth and Moon textures.
     * @return Rendered ImageBitmap.
     */
    suspend fun renderScene(
        state: EarthRenderState,
        textures: EarthWidgetTextures,
    ): ImageBitmap = withContext(dispatcher) {
        val size = state.renderSizePx
        val pixelCount = size * size

        // Acquire output buffer from pool
        val outputBuffer = bufferPool.acquire(pixelCount)

        try {
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
                bufferPool = bufferPool,
                outputBuffer = outputBuffer,
            )

            // Convert to ImageBitmap (copies the data)
            val bitmap = imageBitmapFromArgb(argb, size, size)
            bitmap
        } finally {
            // Release output buffer back to pool
            bufferPool.release(outputBuffer)
        }
    }

    /**
     * Renders the Moon as seen from the marker position on Earth.
     *
     * @param state Rendering parameters.
     * @param moonTexture Moon surface texture.
     * @return Rendered ImageBitmap.
     */
    suspend fun renderMoonFromMarker(
        state: MoonFromMarkerRenderState,
        moonTexture: EarthTexture?,
    ): ImageBitmap = withContext(dispatcher) {
        val size = state.renderSizePx
        val pixelCount = size * size

        // Acquire output buffer from pool
        val outputBuffer = bufferPool.acquire(pixelCount)

        try {
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
                bufferPool = bufferPool,
                outputBuffer = outputBuffer,
            )

            // Convert to ImageBitmap (copies the data)
            val bitmap = imageBitmapFromArgb(argb, size, size)
            bitmap
        } finally {
            // Release output buffer back to pool
            bufferPool.release(outputBuffer)
        }
    }
}
