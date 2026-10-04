package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.nucleusframework.window.tao.TaoGpuRenderContext
import dev.nucleusframework.window.tao.rememberTaoGpuRenderContext
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Records the window body and keeps a reduced picture of it on the selected tab
 * (`TabEntry.thumbnail`), which the strip's hover card shows once the tab is no longer the selected
 * one. Taken once a tab has settled, and again as the pointer reaches the strip — the moment before
 * a click leaves the tab — so the card shows the page as it was left.
 */
@Composable
fun rememberTabThumbnails(openWindow: OpenWindow): Modifier {
    val layer = rememberGraphicsLayer()
    val reduced = rememberGraphicsLayer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val gpu = rememberTaoGpuRenderContext()
    val group = openWindow.group()
    val workspace = openWindow.session.workspace
    val thumbnails = LocalAppGraph.current.tabThumbnailStore
    LaunchedEffect(group, density, direction, gpu) {
        if (group == null) return@LaunchedEffect
        var last: String? = null
        snapshotFlow { group.selectedId to openWindow.pointerOnStrip }.collectLatest { (id, onStrip) ->
            val arrived = id != last
            last = id
            if (id == null) return@collectLatest
            // A tab that just arrived has not drawn yet; the pointer reaching the strip is the last
            // look at a tab that has.
            if (arrived || !onStrip) delay(SETTLE_MS)
            val picture = reducedPicture(layer, reduced, density, direction, gpu) ?: return@collectLatest
            workspace.tab(id)?.thumbnail = picture
            // Kept on disk, so a cold start shows the cards of the tabs it restores.
            withContext(Dispatchers.IO) { thumbnails.save(id, picture) }
        }
    }
    return Modifier.drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun reducedPicture(
    source: GraphicsLayer,
    into: GraphicsLayer,
    density: Density,
    direction: LayoutDirection,
    gpu: TaoGpuRenderContext?,
): ImageBitmap? {
    val size = source.size
    if (size.width <= 0 || size.height <= 0) return null
    val factor = (MAX_SIDE_PX.toFloat() / max(size.width, size.height)).coerceAtMost(1f)
    val target = IntSize((size.width * factor).roundToInt().coerceAtLeast(1), (size.height * factor).roundToInt().coerceAtLeast(1))
    // A picture is cosmetic: a failed readback keeps the last one.
    return try {
        into.record(density, direction, target) { scale(factor, factor, Offset.Zero) { drawLayer(source) } }
        into.toImageBitmapOn(gpu)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private const val SETTLE_MS = 600L
private const val MAX_SIDE_PX = 640
