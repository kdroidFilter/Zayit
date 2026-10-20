package io.github.kdroidfilter.seforimapp.core.e2e

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * End-to-end harness, off unless `ZAYIT_E2E_OUT` names an output directory: a scripted scenario
 * ([E2eScenario]) drives the app through its own APIs and every step exports each window's
 * composition as a PNG — rendered from the composables themselves, not grabbed from the screen.
 */
object E2e {
    val outDir: File? = System.getenv("ZAYIT_E2E_OUT")?.let(::File)
    val enabled: Boolean = outDir != null
    val scenario: String = System.getenv("ZAYIT_E2E_SCENARIO") ?: "common"

    /**
     * Where the run keeps the user database (highlights, notes, history) instead of the user's: scenarios that write
     * to it, or any with `ZAYIT_E2E_USER_DB=isolated`; null keeps the user's (the screenshots' baselines need it).
     */
    val userDataDir: File? = outDir?.takeIf { scenario in setOf("shiftselect") || System.getenv("ZAYIT_E2E_USER_DB") == "isolated" }

    /**
     * The last text the app put on the clipboard. The system clipboard itself cannot be checked: on Wayland the
     * compositor only lets an app set it on a real input event, which the harness's events are not.
     */
    @Volatile
    var copiedText: String? = null

    /** Suffix of a window's title-bar layer, exported as `<name>-title`. */
    const val TITLE_BAR = "#title"

    /**
     * Each window's recorded layer, with the GPU context it is rasterized under ([toImageBitmapOn]) and the
     * window's composition context, whose frame clock that waits on.
     */
    private val layers = ConcurrentHashMap<String, CapturedLayer>()

    private class CapturedLayer(
        val layer: GraphicsLayer,
        val gpu: TaoGpuRenderContext?,
        val composition: CoroutineContext,
    )

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

    /** The book scrollbar on screen: its list and the thumb position it last drew, for [E2eScrollbarScenario]. */
    @Volatile
    var bookScrollbar: Pair<androidx.compose.foundation.lazy.LazyListState, Float>? = null
        set(value) {
            field = value
            value?.let { bookScrollbarTrace.add(it.second) }
        }

    /** Every thumb position the book scrollbar drew. */
    val bookScrollbarTrace: MutableList<Float> = java.util.Collections.synchronizedList(ArrayList())

    /** The widgets of the Home on screen, for [E2eWidgetsScenario] to open and close their gallery. */
    @Volatile
    var homeWidgets: HomeWidgetsState? = null

    internal fun layer(windowId: String): GraphicsLayer? = layers[windowId]?.layer

    internal fun registerLayer(
        windowId: String,
        layer: GraphicsLayer,
        gpu: TaoGpuRenderContext?,
        composition: CoroutineContext,
    ) {
        layers[windowId] = CapturedLayer(layer, gpu, composition)
    }

    internal fun unregisterLayer(windowId: String) {
        layers.remove(windowId)
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
        val captured = layers[windowId] ?: return false
        val bitmap =
            withContext(captured.composition.minusKey(Job)) { captured.layer.toImageBitmapOn(captured.gpu) }
                ?: return false
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return false
        dir.mkdirs()
        File(dir, "$name.png").writeBytes(data.bytes)
        return true
    }

    /** Writes [windowId]'s title bar over its content, as one image, to `<out>/<name>.png`. */
    suspend fun captureWithTitleBar(
        windowId: String,
        name: String,
    ): Boolean {
        val dir = outDir ?: return false
        val images =
            listOf(windowId + TITLE_BAR, windowId).mapNotNull { id ->
                val captured = layers[id] ?: return@mapNotNull null
                withContext(captured.composition.minusKey(Job)) { captured.layer.toImageBitmapOn(captured.gpu) }
                    ?.let { Image.makeFromBitmap(it.asSkiaBitmap()) }
            }
        if (images.isEmpty()) return false
        val surface = Surface.makeRasterN32Premul(images.maxOf { it.width }, images.sumOf { it.height })
        var top = 0f
        images.forEach { image ->
            surface.canvas.drawImage(image, 0f, top)
            top += image.height
        }
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG) ?: return false
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
    val composition = rememberCoroutineScope().coroutineContext
    DisposableEffect(windowId, layer, gpu, composition) {
        E2e.registerLayer(windowId, layer, gpu, composition)
        onDispose { if (E2e.layer(windowId) === layer) E2e.unregisterLayer(windowId) }
    }
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}
