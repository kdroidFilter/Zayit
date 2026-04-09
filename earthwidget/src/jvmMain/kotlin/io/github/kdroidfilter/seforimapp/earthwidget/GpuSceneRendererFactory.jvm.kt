package io.github.kdroidfilter.seforimapp.earthwidget

import androidx.compose.ui.graphics.ImageBitmap

/** Singleton GPU renderer — shader is compiled once and shared across all widget instances. */
private val sharedGpuRenderer: GpuSceneRenderer? by lazy {
    val shaderRenderer = EarthShaderRenderer.create() ?: return@lazy null
    object : GpuSceneRenderer {
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

internal actual fun createGpuRenderer(): GpuSceneRenderer? = sharedGpuRenderer
