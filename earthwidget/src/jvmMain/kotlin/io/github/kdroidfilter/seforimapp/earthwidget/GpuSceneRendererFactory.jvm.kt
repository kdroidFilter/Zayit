package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap

internal actual fun createGpuRenderer(): GpuSceneRenderer? {
    val shaderRenderer = EarthShaderRenderer.create() ?: return null
    return object : GpuSceneRenderer {
        override fun renderScene(
            state: EarthRenderState,
            textures: EarthWidgetTextures,
        ): ImageBitmap = shaderRenderer.renderScene(state, textures)

        override fun renderMoonFromMarker(
            state: MoonFromMarkerRenderState,
            moonTexture: EarthTexture?,
        ): ImageBitmap = shaderRenderer.renderMoonFromMarker(state, moonTexture)
    }
}
