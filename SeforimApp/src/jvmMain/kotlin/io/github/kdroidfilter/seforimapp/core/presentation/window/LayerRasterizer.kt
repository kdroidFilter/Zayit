package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import dev.nucleusframework.window.tao.TaoGpuRenderContext
import dev.nucleusframework.window.tao.TaoOpenGlRenderContext

/**
 * Rasterizes this layer on the calling (composition) thread without racing the window's render thread; null when
 * the window's GPU context can't be reached (the window is going away). Needs a frame clock in the caller's context.
 *
 * A layer showing a GPU texture (the 3D widgets draw through a Nucleus TextureView) reads it back through the
 * window's Skia GPU context, which the render thread replays frames with at the same time; Skia's context is not
 * thread-safe, so an unguarded readback crashes at random. [gpu] (the window's [TaoGpuRenderContext], null without
 * one) runs it with exclusive access to that context. On OpenGL the readback also needs that context current, or
 * Skia reads the texture's name in whichever context is (another window's): it can only be bound during a frame.
 */
suspend fun GraphicsLayer.toImageBitmapOn(gpu: TaoGpuRenderContext?): ImageBitmap? {
    val rasterize = {
        ImageBitmap(size.width, size.height).also { bitmap ->
            // The recorded layer carries its own density and direction; these only set up the scope.
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size.toSize()) {
                drawLayer(this@toImageBitmapOn)
            }
        }
    }
    return when (gpu) {
        null -> rasterize()
        is TaoOpenGlRenderContext -> withFrameNanos { gpu.withContextCurrent(rasterize) }
        else -> gpu.runOnGpuThread(rasterize)
    }
}
