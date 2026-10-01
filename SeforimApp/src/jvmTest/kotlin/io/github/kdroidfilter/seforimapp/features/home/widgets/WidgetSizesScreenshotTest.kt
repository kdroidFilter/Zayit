package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.createGraph
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.LimudPanel
import io.github.kdroidfilter.seforimapp.features.home.widgets.luach.LimudWidget
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import io.github.vinceglb.filekit.FileKit
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every widget at every size the grid lets it take, and measures the widest empty band across each card: a
 * widget must never be resizable to a size it can't fill. Every size and the report go to build/screenshots/sizes. The Filament widgets (the Earth, the sky, the solar system) need a real window and fill
 * their card with their view; they're left out.
 */
@OptIn(ExperimentalTestApi::class)
class WidgetSizesScreenshotTest {
    private val graph by lazy {
        FileKit.init("seforimapp-tests")
        createGraph<AppGraph>()
    }

    private val pitch = CellPitch(MAX_GRID_WIDTH)
    private val out =
        File("build/screenshots/sizes").apply {
            deleteRecursively()
            mkdirs()
        }

    @Test
    fun `no widget size leaves its card half empty`() {
        val faults = mutableListOf<String>()
        val report = StringBuilder()
        for (widget in availableHomeWidgets.filterNot { it.id in FILAMENT_WIDGETS }) {
            for (w in widget.minSpan.w..minOf(widget.maxSpan.w, HOME_GRID_COLUMNS)) {
                val minRows = widget.minRows(w, pitch)
                for (h in minRows..maxOf(widget.maxSpan.h, minRows)) {
                    val image = shot(widget, w, h)
                    val (rows, columns) = emptyBands(image)
                    val line = "${widget.id} ${w}x$h: empty band ${rows}dp high, ${columns}dp wide"
                    report.appendLine(line)
                    ImageIO.write(image, "png", File(out, "${widget.id}-${w}x$h.png"))
                    val maxEmptyHeight = if (widget.id == "zmanim") ZMANIM_CARD_ROWS_GAP else MAX_EMPTY_HEIGHT
                    if (rows > maxEmptyHeight || columns > MAX_EMPTY_WIDTH) {
                        faults += line
                    }
                }
            }
        }
        File(out, "report.txt").writeText(report.toString())
        assertTrue(faults.isEmpty(), faults.joinToString("\n"))
    }

    private fun shot(
        widget: HomeWidget,
        w: Int,
        h: Int,
    ): BufferedImage {
        var image: BufferedImage? = null
        val width = pitch.width(w)
        val height = maxOf(pitch.height(h), widget.heightAt(width) ?: 0.dp)
        runDesktopComposeUiTest(width = width.value.toInt(), height = height.value.toInt()) {
            setContent {
                IntUiTheme(isDark = true) {
                    CompositionLocalProvider(
                        LocalAppGraph provides graph,
                        LocalTabSelected provides false,
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        val state = HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
                        Box(Modifier.background(JewelTheme.globalColors.panelBackground)) {
                            val modifier = Modifier.size(width, height)
                            if (widget == LimudWidget) LimudPanel(state, {}, modifier) else widget.Content(state, modifier)
                        }
                    }
                }
            }
            waitForIdle()
            image = onRoot().captureToImage().toAwtImage()
        }
        return image!!
    }

    /** The highest run of rows, and the widest run of columns, with nothing drawn on them, in dp, inside the frame. */
    private fun emptyBands(image: BufferedImage): Pair<Int, Int> {
        val inset = FRAME_INSET
        val xs = inset until image.width - inset
        val ys = inset until image.height - inset

        fun luminance(
            x: Int,
            y: Int,
        ): Int {
            val rgb = image.getRGB(x, y)
            return ((rgb shr 16 and 0xFF) * 3 + (rgb shr 8 and 0xFF) * 6 + (rgb and 0xFF)) / 10
        }

        fun flat(values: Sequence<Int>): Boolean {
            var min = 255
            var max = 0
            for (v in values) {
                min = minOf(min, v)
                max = maxOf(max, v)
            }
            return max - min < FLAT_RANGE
        }

        fun longestRun(flags: List<Boolean>): Int =
            flags
                .fold(0 to 0) { (best, run), empty ->
                    if (empty) {
                        maxOf(best, run + 1) to run + 1
                    } else {
                        best to
                            0
                    }
                }.first
        val rows = longestRun(ys.map { y -> flat(xs.asSequence().map { x -> luminance(x, y) }) })
        val columns = longestRun(xs.map { x -> flat(ys.asSequence().map { y -> luminance(x, y) }) })
        return rows to columns
    }

    private companion object {
        val FILAMENT_WIDGETS = setOf("earth", "sky", "solar_system")

        /** The frame, its rounded corners and the cards' own padding: not content, never counted. */
        const val FRAME_INSET = 16

        /** Below this luminance spread a line holds no text, only backgrounds. */
        const val FLAT_RANGE = 28

        // Wider than a gap between two lines of text or two columns of it
        const val MAX_EMPTY_HEIGHT = 40

        // The zmanim are rows of cards: the gap between two rows and the cards' padding read as one empty band
        const val ZMANIM_CARD_ROWS_GAP = 56
        const val MAX_EMPTY_WIDTH = 120
    }
}
