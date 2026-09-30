package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import dev.nucleusframework.window.tao.TaoGpuRenderContext
import dev.nucleusframework.window.tao.rememberTaoGpuRenderContext
import io.github.kdroidfilter.seforimapp.core.presentation.window.toImageBitmapOn
import io.github.kdroidfilter.seforimapp.features.bookcontent.BookContentViewModel
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * End-to-end harness, off unless `ZAYIT_E2E_OUT` names an output directory: a scripted scenario
 * ([E2eScenario]) drives the app through its own APIs and every step exports each window's
 * composition as a PNG — rendered from the composables themselves, not grabbed from the screen.
 */
object E2e {
    val outDir: File? = System.getenv("ZAYIT_E2E_OUT")?.let(::File)
    val enabled: Boolean = outDir != null
    val scenario: String = System.getenv("ZAYIT_E2E_SCENARIO") ?: "common"

    /** Suffix of a window's title-bar layer, exported as `<name>-title`. */
    const val TITLE_BAR = "#title"

    /** Each window's recorded layer, with the GPU context it is rasterized under ([toImageBitmapOn]). */
    private val layers = ConcurrentHashMap<String, Pair<GraphicsLayer, TaoGpuRenderContext?>>()
    private val bookViewModels = ConcurrentHashMap<String, BookContentViewModel>()

    fun registerBookViewModel(
        tabId: String,
        viewModel: BookContentViewModel,
    ) {
        if (enabled) bookViewModels[tabId] = viewModel
    }

    private var started = false

    // The run's own scope, for the life of the process (the harness quits the app when it ends).
    private val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Runs [block] once per process, outside any composition. */
    fun start(block: suspend () -> Unit) {
        if (started) return
        started = true
        launchRun(runScope, block)
    }

    private fun launchRun(
        @StructuredScope scope: CoroutineScope,
        block: suspend () -> Unit,
    ) {
        scope.launch { block() }
    }

    fun bookViewModel(tabId: String): BookContentViewModel? = bookViewModels[tabId]

    internal fun layer(windowId: String): GraphicsLayer? = layers[windowId]?.first

    internal fun registerLayer(
        windowId: String,
        layer: GraphicsLayer?,
        gpu: TaoGpuRenderContext? = null,
    ) {
        if (layer == null) layers.remove(windowId) else layers[windowId] = layer to gpu
    }

    /** Writes [bitmap] to `<out>/<name>.png`. */
    fun save(
        name: String,
        bitmap: androidx.compose.ui.graphics.ImageBitmap,
    ) {
        val dir = outDir ?: return
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return
        dir.mkdirs()
        File(dir, "$name.png").writeBytes(data.bytes)
    }

    /** Writes [windowId]'s last recorded frame to `<out>/<name>.png`. */
    suspend fun capture(
        windowId: String,
        name: String,
    ): Boolean {
        val dir = outDir ?: return false
        val (layer, gpu) = layers[windowId] ?: return false
        val bitmap = layer.toImageBitmapOn(gpu)
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return false
        dir.mkdirs()
        File(dir, "$name.png").writeBytes(data.bytes)
        return true
    }
}

/** Records this window's content for [E2e.capture]; nothing unless the harness is on. */
@Composable
fun Modifier.e2eCapture(windowId: String): Modifier {
    if (!E2e.enabled) return this
    val layer = rememberGraphicsLayer()
    val gpu = rememberTaoGpuRenderContext()
    DisposableEffect(windowId, layer, gpu) {
        E2e.registerLayer(windowId, layer, gpu)
        onDispose { if (E2e.layer(windowId) === layer) E2e.registerLayer(windowId, null) }
    }
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}
